package studio.cosmosis.mask

import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Deterministic network-free saliency fallback. This is deliberately not
 * presented as ORML/U2Net. It estimates border background color and combines
 * color distance with a soft center prior to produce an editable mask seed.
 */
object SmartMask {
    fun saliency(path:Path,threshold:Double=0.28):MaskDocument = saliency(requireNotNull(ImageIO.read(path.toFile())){"Unsupported image $path"},threshold)
    fun saliency(image:BufferedImage,threshold:Double=0.28):MaskDocument {
        val w=image.width;val h=image.height
        val samples=mutableListOf<Int>()
        val step=maxOf(1,minOf(w,h)/96)
        for(x in 0 until w step step){samples+=image.getRGB(x,0);if(h>1)samples+=image.getRGB(x,h-1)}
        for(y in 0 until h step step){samples+=image.getRGB(0,y);if(w>1)samples+=image.getRGB(w-1,y)}
        fun avg(shift:Int)=samples.map{(it shr shift) and 255}.average()
        val br=avg(16);val bg=avg(8);val bb=avg(0)
        val maxDist=sqrt(3.0*255.0*255.0)
        val mask=BufferedImage(w,h,BufferedImage.TYPE_BYTE_GRAY)
        val raster=mask.raster
        val cx=(w-1)/2.0;val cy=(h-1)/2.0;val maxR=hypot(cx.coerceAtLeast(1.0),cy.coerceAtLeast(1.0))
        for(y in 0 until h) for(x in 0 until w){
            val rgb=image.getRGB(x,y);val r=(rgb shr 16)and 255;val g=(rgb shr 8)and 255;val b=rgb and 255
            val dist=sqrt((r-br)*(r-br)+(g-bg)*(g-bg)+(b-bb)*(b-bb))/maxDist
            val center=(1.0-hypot(x-cx,y-cy)/maxR).coerceIn(0.0,1.0)
            val localContrast=(abs(r-g)+abs(g-b)+abs(b-r))/(3.0*255.0)
            val score=(dist*.72 + center*.20 + localContrast*.08).coerceIn(0.0,1.0)
            val value=when { score>=threshold -> 255; score>=threshold*.70 -> ((score-threshold*.70)/(threshold*.30)*255).toInt().coerceIn(0,255); else -> 0 }
            raster.setSample(x,y,0,value)
        }
        return MaskDocument.fromBufferedImage(mask)
    }
}
