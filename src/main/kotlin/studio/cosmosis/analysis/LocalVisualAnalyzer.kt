package studio.cosmosis.analysis

import studio.cosmosis.StructuredPrompt
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs

data class VisualAnalysis(val width:Int,val height:Int,val orientation:String,val meanLuma:Double,val edgeDensity:Double,val palette:List<String>,val saliencyBox:IntArray,val tags:Set<String>)

object LocalVisualAnalyzer {
    fun analyze(path: Path): VisualAnalysis = analyze(requireNotNull(ImageIO.read(path.toFile())) { "Unsupported image $path" })
    fun analyze(img: BufferedImage): VisualAnalysis {
        val w=img.width; val h=img.height; var lum=0.0; var samples=0; var edges=0; var comparisons=0
        val buckets=linkedMapOf<Int,Int>()
        val stride=maxOf(1, maxOf(w,h)/320)
        for(y in 0 until h step stride) for(x in 0 until w step stride){
            val rgb=img.getRGB(x,y); val r=(rgb shr 16) and 255;val g=(rgb shr 8) and 255;val b=rgb and 255
            val l=.2126*r+.7152*g+.0722*b;lum+=l;samples++
            val key=((r/51)*51 shl 16) or ((g/51)*51 shl 8) or ((b/51)*51);buckets[key]=(buckets[key]?:0)+1
            if(x+stride<w){ val rgb2=img.getRGB(x+stride,y); val r2=(rgb2 shr 16)and 255;val g2=(rgb2 shr 8)and 255;val b2=rgb2 and 255; if(abs(r-r2)+abs(g-g2)+abs(b-b2)>110) edges++;comparisons++ }
        }
        val palette=buckets.entries.sortedByDescending{it.value}.take(6).map { "#%06X".format(it.key and 0xFFFFFF) }
        val mean=lum/samples.coerceAtLeast(1); val ed=edges.toDouble()/comparisons.coerceAtLeast(1)
        val tags=linkedSetOf<String>().apply { add(if(mean<90)"dark" else if(mean>180)"bright" else "mid-key"); add(if(ed>.25)"high-detail" else "clean-shapes"); add(if(w>h)"landscape" else if(h>w)"portrait" else "square") }
        return VisualAnalysis(w,h,tags.last(),mean,ed,palette,intArrayOf(w/5,h/5,w*4/5,h*4/5),tags)
    }
    fun toStructuredPrompt(a: VisualAnalysis, subjectHint:String="replaceable subject"): StructuredPrompt = StructuredPrompt(
        subject=subjectHint,
        styleDna="${a.tags.joinToString(", ")}; palette ${a.palette.joinToString(" ")}",
        composition="${a.orientation} frame; preserve primary subject placement in central ${(a.saliencyBox[2]-a.saliencyBox[0])}×${(a.saliencyBox[3]-a.saliencyBox[1])} region",
        lighting=when { a.meanLuma<90 -> "low-key lighting"; a.meanLuma>180 -> "high-key lighting"; else -> "balanced mid-key lighting" },
        color="dominant palette ${a.palette.joinToString(", ")}",
        qualityConstraints="preserve geometry, readable edges, no unintended crop",
        avoid="unrequested text, warped anatomy, broken object boundaries"
    )
}
