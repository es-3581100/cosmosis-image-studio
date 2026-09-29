package studio.cosmosis.orml.bodypix

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

data class BodyPixSegmentation(val scores:FloatArray,val width:Int,val height:Int)

class ReflectiveTensorFlowBodyPixRuntime private constructor(
    private val modelPath:Path,
    private val binding:BodyPixTensorFlowBinding
):BodyPixRuntime {
    override fun generateMask(input:Path,output:Path,options:BodyPixOptions) {
        require(Files.isRegularFile(input)){"input image does not exist: "+input}
        val source=requireNotNull(ImageIO.read(input.toFile())){"input is not a decodable image: "+input}
        val (targetWidth,targetHeight)=bodyPixInputSize(
            source.width,source.height,options.internalResolution,options.maxInputSide
        )
        val normalized=normalizeBodyPixMobileNetInput(source,targetWidth,targetHeight)
        val segmentation=binding.infer(Files.readAllBytes(modelPath),normalized,targetWidth,targetHeight)
        val mask=bodyPixMaskImage(
            segmentation.scores,segmentation.width,segmentation.height,
            source.width,source.height,options.threshold
        )
        output.parent?.let(Files::createDirectories)
        require(ImageIO.write(mask,"png",output.toFile())){"PNG writer unavailable"}
    }

    override fun close(){}

    companion object {
        fun verifyEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowBodyPixRuntime::class.java.classLoader
        ){
            BodyPixModelPin.requirePinnedModel(environment)
            BodyPixTensorFlowBinding(classLoader).verifyRuntime()
        }

        fun fromEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowBodyPixRuntime::class.java.classLoader
        ):ReflectiveTensorFlowBodyPixRuntime {
            val model=BodyPixModelPin.requirePinnedModel(environment)
            val binding=BodyPixTensorFlowBinding(classLoader)
            binding.verifyRuntime()
            return ReflectiveTensorFlowBodyPixRuntime(model,binding)
        }
    }
}

internal fun bodyPixInputSize(
    sourceWidth:Int,
    sourceHeight:Int,
    internalResolution:Double=BodyPixModelPin.defaultInternalResolution,
    maxInputSide:Int=BodyPixModelPin.defaultMaxInputSide
):Pair<Int,Int>{
    require(sourceWidth>0&&sourceHeight>0){"source dimensions must be positive"}
    require(internalResolution in 0.1..1.0){"internalResolution must be between 0.1 and 1.0"}
    val maxDimension=maxOf(sourceWidth,sourceHeight).toDouble()
    val factor=min(internalResolution,maxInputSide/maxDimension)
    val width=validBodyPixResolution(sourceWidth*factor,BodyPixModelPin.outputStride)
    val height=validBodyPixResolution(sourceHeight*factor,BodyPixModelPin.outputStride)
    return width to height
}

internal fun validBodyPixResolution(raw:Double,stride:Int):Int {
    require(stride>0)
    val bounded=raw.coerceAtLeast(33.0)
    val rounded=bounded.roundToInt()
    if((rounded-1)%stride==0)return rounded
    return (floor(bounded/stride)*stride+1).toInt().coerceAtLeast(33)
}

internal fun normalizeBodyPixMobileNetInput(
    source:BufferedImage,
    width:Int,
    height:Int
):FloatArray{
    val resized=resizeBodyPixImage(source,width,height,BufferedImage.TYPE_INT_RGB,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    val normalized=FloatArray(width*height*3)
    var p=0
    for(y in 0 until height)for(x in 0 until width){
        val rgb=resized.getRGB(x,y)
        normalized[p++]=(((rgb ushr 16) and 0xff)/255.0f)*2.0f-1.0f
        normalized[p++]=(((rgb ushr 8) and 0xff)/255.0f)*2.0f-1.0f
        normalized[p++]=((rgb and 0xff)/255.0f)*2.0f-1.0f
    }
    return normalized
}

internal fun bodyPixMaskImage(
    scores:FloatArray,
    scoreWidth:Int,
    scoreHeight:Int,
    outputWidth:Int,
    outputHeight:Int,
    threshold:Float=BodyPixModelPin.defaultThreshold
):BufferedImage{
    require(scoreWidth>0&&scoreHeight>0)
    require(scores.size==scoreWidth*scoreHeight){"unexpected BodyPix segmentation size"}
    require(threshold in 0.0f..1.0f){"threshold must be between 0 and 1"}

    val probability=BufferedImage(scoreWidth,scoreHeight,BufferedImage.TYPE_BYTE_GRAY)
    var i=0
    for(y in 0 until scoreHeight)for(x in 0 until scoreWidth){
        val value=(scores[i++].coerceIn(0.0f,1.0f)*255.0f).roundToInt()
        probability.raster.setSample(x,y,0,value)
    }
    val resized=resizeBodyPixImage(
        probability,outputWidth,outputHeight,BufferedImage.TYPE_BYTE_GRAY,RenderingHints.VALUE_INTERPOLATION_BILINEAR
    )
    val mask=BufferedImage(outputWidth,outputHeight,BufferedImage.TYPE_BYTE_GRAY)
    val cutoff=(threshold*255.0f).roundToInt()
    for(y in 0 until outputHeight)for(x in 0 until outputWidth){
        mask.raster.setSample(x,y,0,if(resized.raster.getSample(x,y,0)>=cutoff)255 else 0)
    }
    return mask
}

private fun resizeBodyPixImage(source:BufferedImage,width:Int,height:Int,type:Int,interpolation:Any):BufferedImage {
    require(width>0&&height>0){"image dimensions must be positive"}
    val out=BufferedImage(width,height,type)
    val g=out.createGraphics()
    try{
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,interpolation)
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(source,0,0,width,height,null)
    }finally{g.dispose()}
    return out
}

internal class BodyPixTensorFlowBinding(private val classLoader:ClassLoader) {
    private val graphClass by lazy{load("org.tensorflow.Graph")}
    private val sessionClass by lazy{load("org.tensorflow.Session")}
    private val graphDefClass by lazy{load("org.tensorflow.proto.framework.GraphDef")}
    private val shapeClass by lazy{load("org.tensorflow.ndarray.Shape")}
    private val tFloat32Class by lazy{load("org.tensorflow.types.TFloat32")}
    private val tensorFlowClass by lazy{load("org.tensorflow.TensorFlow")}

    fun verifyRuntime(){
        graphClass;sessionClass;graphDefClass;shapeClass;tFloat32Class
        val version=tensorFlowClass.methods.firstOrNull{
            it.name=="version"&&Modifier.isStatic(it.modifiers)&&it.parameterCount==0
        }?:error("TensorFlow.version() is unavailable")
        val reported=version.invoke(null)?.toString().orEmpty()
        require(reported.isNotBlank()){"TensorFlow native runtime did not report a version"}
    }

    fun infer(graphBytes:ByteArray,input:FloatArray,width:Int,height:Int):BodyPixSegmentation {
        require(input.size==width*height*3){"unexpected BodyPix input size"}
        val graph=graphClass.getDeclaredConstructor().newInstance()
        var session:Any?=null
        var inputTensor:Any?=null
        var outputTensor:Any?=null
        try{
            val graphDef=parseGraphDef(graphBytes)
            invokeCompatible(graph,"importGraphDef",graphDef)
            session=sessionClass.getConstructor(graphClass).newInstance(graph)

            val shape=shapeClass.getMethod("of",LongArray::class.java)
                .invoke(null,longArrayOf(1,height.toLong(),width.toLong(),3))
            inputTensor=tFloat32Class.getMethod("tensorOf",shapeClass).invoke(null,shape)
            val setFloat=findMethod(inputTensor.javaClass,"setFloat",2)

            var p=0
            for(y in 0 until height)for(x in 0 until width)for(c in 0..2){
                setFloat.invoke(inputTensor,input[p++],longArrayOf(0,y.toLong(),x.toLong(),c.toLong()))
            }

            var runner=invokeCompatible(session,"runner")?:error("TensorFlow Session.runner() returned null")
            runner=invokeCompatible(runner,"feed",BodyPixModelPin.inputTensor,inputTensor)?:runner
            runner=invokeCompatible(runner,"fetch",BodyPixModelPin.outputTensor)?:runner
            val outputs=invokeCompatible(runner,"run") as? List<*> ?:error("TensorFlow runner did not return a tensor list")
            outputTensor=outputs.firstOrNull()?:error("TensorFlow BodyPix returned no segmentation tensor")
            require(tFloat32Class.isInstance(outputTensor)){"BodyPix segmentation tensor is not TFloat32"}

            val outputShape=invokeCompatible(outputTensor,"shape")?:error("BodyPix output shape is unavailable")
            val dimensions=invokeCompatible(outputShape,"asArray") as? LongArray
                ?:error("BodyPix output dimensions are unavailable")
            require(dimensions.size==4&&dimensions[0]==1L&&dimensions[3]==1L){
                "unexpected BodyPix output shape: "+dimensions.joinToString("x")
            }
            val outHeight=dimensions[1].toInt()
            val outWidth=dimensions[2].toInt()
            require(outWidth>0&&outHeight>0){"BodyPix output dimensions are invalid"}
            val getFloat=findMethod(outputTensor.javaClass,"getFloat",1)
            val result=FloatArray(outWidth*outHeight)
            var o=0
            for(y in 0 until outHeight)for(x in 0 until outWidth){
                val v=getFloat.invoke(outputTensor,longArrayOf(0,y.toLong(),x.toLong(),0)) as Number
                result[o++]=v.toFloat()
            }
            return BodyPixSegmentation(result,outWidth,outHeight)
        }finally{
            closeQuietly(outputTensor)
            closeQuietly(inputTensor)
            closeQuietly(session)
            closeQuietly(graph)
        }
    }

    private fun parseGraphDef(bytes:ByteArray):Any {
        val parse=graphDefClass.methods.firstOrNull{
            it.name=="parseFrom"&&Modifier.isStatic(it.modifiers)&&
                it.parameterCount==1&&it.parameterTypes[0]==ByteArray::class.java
        }?:error("GraphDef.parseFrom(byte[]) is unavailable")
        return parse.invoke(null,bytes)
    }

    private fun load(name:String):Class<*> = try{
        Class.forName(name,false,classLoader)
    }catch(t:Throwable){
        throw IllegalStateException("required TensorFlow class is unavailable: "+name,t)
    }

    private fun invokeCompatible(target:Any,name:String,vararg args:Any?):Any? {
        val method=target.javaClass.methods.firstOrNull{
            it.name==name&&it.parameterCount==args.size&&parametersCompatible(it,args)
        }?:error(target.javaClass.name+"."+name+"("+args.size+") is unavailable")
        return method.invoke(target,*args)
    }

    private fun findMethod(type:Class<*>,name:String,count:Int):Method =
        type.methods.firstOrNull{it.name==name&&it.parameterCount==count}
            ?:error(type.name+"."+name+"("+count+") is unavailable")

    private fun parametersCompatible(method:Method,args:Array<out Any?>):Boolean =
        method.parameterTypes.zip(args).all{(parameter,arg)->
            if(arg==null)!parameter.isPrimitive else wrap(parameter).isAssignableFrom(arg.javaClass)
        }

    private fun wrap(type:Class<*>):Class<*> = when(type){
        java.lang.Boolean.TYPE->java.lang.Boolean::class.java
        java.lang.Byte.TYPE->java.lang.Byte::class.java
        java.lang.Short.TYPE->java.lang.Short::class.java
        java.lang.Integer.TYPE->java.lang.Integer::class.java
        java.lang.Long.TYPE->java.lang.Long::class.java
        java.lang.Float.TYPE->java.lang.Float::class.java
        java.lang.Double.TYPE->java.lang.Double::class.java
        java.lang.Character.TYPE->java.lang.Character::class.java
        else->type
    }

    private fun closeQuietly(value:Any?){
        if(value==null)return
        runCatching{
            value.javaClass.methods.firstOrNull{it.name=="close"&&it.parameterCount==0}?.invoke(value)
        }
    }
}
