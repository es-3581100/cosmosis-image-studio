package studio.cosmosis.orml.superresolution

import java.awt.image.BufferedImage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

data class FalsrInputs(
    val y:FloatArray,
    val pbpr:FloatArray,
    val width:Int,
    val height:Int
)

class ReflectiveTensorFlowSuperResolutionRuntime private constructor(
    private val modelPath:Path,
    private val binding:SuperResolutionTensorFlowBinding
):SuperResolutionRuntime {
    override fun upscale2x(input:BufferedImage):BufferedImage {
        val prepared=falsrInputs(input)
        val rgb=binding.infer(
            Files.readAllBytes(modelPath),
            prepared.y,
            prepared.pbpr,
            prepared.width,
            prepared.height
        )
        return falsrRgbImage(rgb,prepared.width*2,prepared.height*2)
    }

    override fun close(){}

    companion object {
        fun verifyEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowSuperResolutionRuntime::class.java.classLoader
        ){
            SuperResolutionModelPin.requirePinnedModel(environment)
            SuperResolutionTensorFlowBinding(classLoader).verifyRuntime()
        }

        fun fromEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowSuperResolutionRuntime::class.java.classLoader
        ):ReflectiveTensorFlowSuperResolutionRuntime {
            val model=SuperResolutionModelPin.requirePinnedModel(environment)
            val binding=SuperResolutionTensorFlowBinding(classLoader)
            binding.verifyRuntime()
            return ReflectiveTensorFlowSuperResolutionRuntime(model,binding)
        }
    }
}

internal fun falsrInputs(source:BufferedImage):FalsrInputs {
    require(source.width>0&&source.height>0){"source dimensions must be positive"}
    val width=source.width
    val height=source.height
    val targetWidth=width*2
    val targetHeight=height*2
    val yValues=FloatArray(width*height)
    val pbpr=FloatArray(targetWidth*targetHeight*2)

    for(y in 0 until height)for(x in 0 until width){
        val rgb=source.getRGB(x,y)
        val r=((rgb ushr 16) and 0xff)/255.0f
        val g=((rgb ushr 8) and 0xff)/255.0f
        val b=(rgb and 0xff)/255.0f
        val luminance=0.2126f*r+0.7152f*g+0.0722f*b
        val pb=0.5f*(b-luminance)/(1.0f-0.0722f)
        val pr=0.5f*(r-luminance)/(1.0f-0.2126f)
        yValues[y*width+x]=luminance

        for(dy in 0..1)for(dx in 0..1){
            val tx=x*2+dx
            val ty=y*2+dy
            val offset=(ty*targetWidth+tx)*2
            pbpr[offset]=pb
            pbpr[offset+1]=pr
        }
    }
    return FalsrInputs(yValues,pbpr,width,height)
}

internal fun falsrRgbImage(rgb:FloatArray,width:Int,height:Int):BufferedImage {
    require(width>0&&height>0){"output dimensions must be positive"}
    require(rgb.size==width*height*3){"unexpected FALSR RGB output size"}
    val image=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
    var p=0
    for(y in 0 until height)for(x in 0 until width){
        val r=(rgb[p++].coerceIn(0.0f,1.0f)*255.0f).roundToInt()
        val g=(rgb[p++].coerceIn(0.0f,1.0f)*255.0f).roundToInt()
        val b=(rgb[p++].coerceIn(0.0f,1.0f)*255.0f).roundToInt()
        image.setRGB(x,y,(r shl 16) or (g shl 8) or b)
    }
    return image
}

internal class SuperResolutionTensorFlowBinding(private val classLoader:ClassLoader) {
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

    fun infer(
        graphBytes:ByteArray,
        yInput:FloatArray,
        pbprInput:FloatArray,
        width:Int,
        height:Int
    ):FloatArray {
        require(yInput.size==width*height){"unexpected FALSR Y input size"}
        require(pbprInput.size==width*2*height*2*2){"unexpected FALSR PbPr input size"}

        val graph=graphClass.getDeclaredConstructor().newInstance()
        var session:Any?=null
        var yTensor:Any?=null
        var pbprTensor:Any?=null
        var outputTensor:Any?=null
        try{
            val graphDef=parseGraphDef(graphBytes)
            invokeCompatible(graph,"importGraphDef",graphDef)
            session=sessionClass.getConstructor(graphClass).newInstance(graph)

            val yShape=shapeClass.getMethod("of",LongArray::class.java)
                .invoke(null,longArrayOf(1,height.toLong(),width.toLong(),1))
            yTensor=tFloat32Class.getMethod("tensorOf",shapeClass).invoke(null,yShape)
            val setY=findMethod(yTensor.javaClass,"setFloat",2)
            var yp=0
            for(y in 0 until height)for(x in 0 until width){
                setY.invoke(yTensor,yInput[yp++],longArrayOf(0,y.toLong(),x.toLong(),0))
            }

            val pbprShape=shapeClass.getMethod("of",LongArray::class.java)
                .invoke(null,longArrayOf(1,(height*2).toLong(),(width*2).toLong(),2))
            pbprTensor=tFloat32Class.getMethod("tensorOf",shapeClass).invoke(null,pbprShape)
            val setPbPr=findMethod(pbprTensor.javaClass,"setFloat",2)
            var cp=0
            for(y in 0 until height*2)for(x in 0 until width*2)for(c in 0..1){
                setPbPr.invoke(pbprTensor,pbprInput[cp++],longArrayOf(0,y.toLong(),x.toLong(),c.toLong()))
            }

            var runner=invokeCompatible(session,"runner")?:error("TensorFlow Session.runner() returned null")
            runner=invokeCompatible(runner,"feed",SuperResolutionModelPin.inputYTensor,yTensor)?:runner
            runner=invokeCompatible(runner,"feed",SuperResolutionModelPin.inputPbPrTensor,pbprTensor)?:runner
            runner=invokeCompatible(runner,"fetch",SuperResolutionModelPin.outputTensor)?:runner
            val outputs=invokeCompatible(runner,"run") as? List<*> ?:error("TensorFlow runner did not return a tensor list")
            outputTensor=outputs.firstOrNull()?:error("TensorFlow FALSR returned no output tensor")
            require(tFloat32Class.isInstance(outputTensor)){"FALSR output tensor is not TFloat32"}

            val outputShape=invokeCompatible(outputTensor,"shape")?:error("FALSR output shape is unavailable")
            val dims=invokeCompatible(outputShape,"asArray") as? LongArray
                ?:error("FALSR output dimensions are unavailable")
            require(dims.contentEquals(longArrayOf(1,(height*2).toLong(),(width*2).toLong(),3))){
                "unexpected FALSR output shape: "+dims.joinToString("x")
            }

            val getFloat=findMethod(outputTensor.javaClass,"getFloat",1)
            val result=FloatArray(width*2*height*2*3)
            var p=0
            for(y in 0 until height*2)for(x in 0 until width*2)for(c in 0..2){
                val value=getFloat.invoke(outputTensor,longArrayOf(0,y.toLong(),x.toLong(),c.toLong())) as Number
                result[p++]=value.toFloat()
            }
            return result
        }finally{
            closeQuietly(outputTensor)
            closeQuietly(pbprTensor)
            closeQuietly(yTensor)
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
