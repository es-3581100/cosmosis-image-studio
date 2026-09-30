package studio.cosmosis.provider

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Deterministic offline preview provider used to exercise the full editor,
 * worker, mask, lineage and export pipeline without credentials or paid calls.
 * It is intentionally labelled PREVIEW and must never be presented as an AI
 * model. Real provider adapters remain OpenAI, Gemini and LiteLLM/custom.
 */
class LocalPreviewProvider:ImageProvider {
    override val id="local"
    private val caps=ProviderCapabilities(
        textToImage=true,imageToImage=true,maskEditing=true,multipleReferences=false,multiTurnEditing=false,
        transparentBackground=true,customDimensions=true,parallelVariants=true,maxReferenceImages=1,
        qualityLevels=setOf("preview","auto"),outputFormats=setOf("png")
    )
    override fun capabilities(model:String)=modelDefinition(model).capabilities
    override fun models()=listOf(ModelDefinition(id,"local-preview-v1","Local deterministic preview (not AI)",caps,notes="Offline pipeline verification renderer"))
    override fun generate(request:GenerationRequest):GenerationResult {
        CapabilityValidator.validate(request,modelDefinition(request.model),false);val start=System.nanoTime()
        val images=(0 until request.variants).map{i->GeneratedImage(renderNew(request,i),"image/png",providerAssetId="local-${request.id}-$i")}
        return GenerationResult(request.id,id,request.model,images,(System.nanoTime()-start)/1_000_000,mapOf("mode" to "deterministic-preview","ai" to "false"))
    }
    override fun edit(request:GenerationRequest):GenerationResult {
        CapabilityValidator.validate(request,modelDefinition(request.model),true);require(request.references.size==1){"Local preview edit accepts exactly one source image"};val start=System.nanoTime()
        val source=requireNotNull(ImageIO.read(request.references.single().path.toFile())){"Unsupported source image"}
        val mask=request.mask?.let{requireNotNull(ImageIO.read(it.path.toFile())){"Unsupported mask image"}}
        if(mask!=null)require(mask.width==source.width&&mask.height==source.height){"Mask must match source dimensions"}
        val images=(0 until request.variants).map{i->GeneratedImage(renderEdit(source,mask,request.prompt,i),"image/png",providerAssetId="local-${request.id}-$i")}
        return GenerationResult(request.id,id,request.model,images,(System.nanoTime()-start)/1_000_000,mapOf("mode" to "deterministic-preview","ai" to "false"))
    }
    override fun testConnection()=ConnectionStatus(true,"LOCAL / no network / preview renderer ready",0)

    private fun renderNew(request:GenerationRequest,variant:Int):ByteArray {
        val w=(request.width?:1024).coerceIn(64,4096);val h=(request.height?:1024).coerceIn(64,4096)
        val type=if(request.transparent)BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val out=BufferedImage(w,h,type);val g=out.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON)
        val seed=abs((request.prompt+variant).hashCode());val c1=Color(34+(seed%120),30+((seed/17)%100),26+((seed/31)%90));val c2=Color(190+seed%50,175+(seed/13)%55,130+(seed/29)%80)
        val block=maxOf(24,minOf(w,h)/12);for(y in 0 until h step block)for(x in 0 until w step block){val mix=((x/block+y/block+variant)%5)/4f;g.color=blend(c1,c2,mix);g.fillRect(x,y,block,block)}
        g.color=Color(16,16,14,220);g.fillRect(0,(h*.72).toInt(),w,(h*.28).toInt());g.color=Color(255,255,227);g.font=Font(Font.MONOSPACED,Font.BOLD,maxOf(14,w/42));g.drawString("COSMOSIS / LOCAL PREVIEW",maxOf(18,w/30),(h*.80).toInt());g.font=Font(Font.MONOSPACED,Font.PLAIN,maxOf(11,w/58));wrap(request.prompt,54).take(3).forEachIndexed{idx,line->g.drawString(line,maxOf(18,w/30),(h*.86).toInt()+idx*maxOf(14,h/40))};g.dispose();return png(out)
    }
    private fun renderEdit(source:BufferedImage,mask:BufferedImage?,prompt:String,variant:Int):ByteArray {
        val out=BufferedImage(source.width,source.height,BufferedImage.TYPE_INT_ARGB);val g=out.createGraphics();g.drawImage(source,0,0,null);g.dispose()
        val seed=abs((prompt+variant).hashCode());val tint=Color(120+seed%110,90+(seed/13)%130,45+(seed/31)%150)
        for(y in 0 until out.height)for(x in 0 until out.width){val apply=mask?.raster?.getSample(x,y,0)?.let{it>127}?:true;if(!apply)continue;val rgb=Color(out.getRGB(x,y),true);val m=.32;val r=(rgb.red*(1-m)+tint.red*m).toInt();val gg=(rgb.green*(1-m)+tint.green*m).toInt();val b=(rgb.blue*(1-m)+tint.blue*m).toInt();out.setRGB(x,y,Color(r,gg,b,rgb.alpha).rgb)}
        return png(out)
    }
    private fun png(img:BufferedImage):ByteArray=ByteArrayOutputStream().use{ImageIO.write(img,"png",it);it.toByteArray()}
    private fun blend(a:Color,b:Color,t:Float)=Color((a.red+(b.red-a.red)*t).toInt(),(a.green+(b.green-a.green)*t).toInt(),(a.blue+(b.blue-a.blue)*t).toInt())
    private fun wrap(text:String,width:Int):List<String>{val words=text.replace('\n',' ').split(Regex("\\s+")).filter{it.isNotBlank()};val lines=mutableListOf<String>();var cur="";for(w in words){if(cur.isEmpty()||cur.length+1+w.length<=width)cur=if(cur.isEmpty())w else "$cur $w" else{lines+=cur;cur=w}};if(cur.isNotBlank())lines+=cur;return lines}
}
