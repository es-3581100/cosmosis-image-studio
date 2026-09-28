package studio.cosmosis.mask

import java.awt.Color
import java.awt.image.BufferedImage
import java.util.ArrayDeque
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class MaskStroke(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val radius: Int, val erase: Boolean)

class MaskDocument(val width: Int, val height: Int) {
    private var pixels = ByteArray(width*height)
    private val undo = ArrayDeque<ByteArray>()
    private val redo = ArrayDeque<ByteArray>()
    private fun snapshot() { undo.add(pixels.copyOf()); if (undo.size > 32) undo.removeFirst(); redo.clear() }
    fun apply(stroke: MaskStroke) { snapshot(); rasterLine(stroke) }
    fun clear() { snapshot(); pixels.fill(0) }
    fun invert() { snapshot(); for(i in pixels.indices) pixels[i]=(255-(pixels[i].toInt() and 255)).toByte() }
    fun undo(): Boolean { val p=if(undo.isEmpty()) return false else undo.removeLast(); redo.add(pixels); pixels=p; return true }
    fun redo(): Boolean { val p=if(redo.isEmpty()) return false else redo.removeLast(); undo.add(pixels); pixels=p; return true }
    fun feather(radius: Int) {
        if(radius<=0)return; snapshot(); val src=pixels.copyOf(); val r=min(radius,32)
        for(y in 0 until height) for(x in 0 until width){ var sum=0; var n=0
            for(yy in max(0,y-r)..min(height-1,y+r)) for(xx in max(0,x-r)..min(width-1,x+r)){sum += src[yy*width+xx].toInt() and 255;n++}
            pixels[y*width+x]=(sum/n).toByte()
        }
    }
    fun coverage(): Double = pixels.count { (it.toInt() and 255)>127 }.toDouble()/pixels.size
    fun toBufferedImage(): BufferedImage { val img=BufferedImage(width,height,BufferedImage.TYPE_BYTE_GRAY); val raster=img.raster; for(y in 0 until height) for(x in 0 until width) raster.setSample(x,y,0,pixels[y*width+x].toInt() and 255); return img }
    fun save(path: Path) { path.parent?.toFile()?.mkdirs(); ImageIO.write(toBufferedImage(),"png",path.toFile()) }

    companion object {
        fun fromBufferedImage(image:BufferedImage):MaskDocument {
            val doc=MaskDocument(image.width,image.height)
            val raster=image.raster
            for(y in 0 until image.height) for(x in 0 until image.width) {
                val v=if(raster.numBands==1) raster.getSample(x,y,0) else {
                    val rgb=image.getRGB(x,y);(((rgb shr 16)and 255)+((rgb shr 8)and 255)+(rgb and 255))/3
                }
                doc.pixels[y*image.width+x]=v.coerceIn(0,255).toByte()
            }
            return doc
        }
    }
    private fun rasterLine(s: MaskStroke) {
        val dx=s.x1-s.x0; val dy=s.y1-s.y0; val steps=max(1,max(kotlin.math.abs(dx),kotlin.math.abs(dy)))
        for(i in 0..steps){ val x=s.x0+dx*i/steps; val y=s.y0+dy*i/steps; disk(x,y,s.radius,if(s.erase)0 else 255) }
    }
    private fun disk(cx:Int,cy:Int,r:Int,v:Int){ val rr=r*r; for(y in max(0,cy-r)..min(height-1,cy+r)) for(x in max(0,cx-r)..min(width-1,cx+r)){val dx=x-cx;val dy=y-cy;if(dx*dx+dy*dy<=rr)pixels[y*width+x]=v.toByte()} }
}
