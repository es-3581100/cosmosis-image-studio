package studio.cosmosis.orml.u2net

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.roundToInt

class ReflectiveTensorFlowU2NetRuntime private constructor(
    private val modelPath:Path,
    private val binding:TensorFlowBinding
):U2NetRuntime {
    override fun generateMask(input:Path,output:Path) {
        require(Files.isRegularFile(input)){"input image does not exist: $input"}
        val source=requireNotNull(ImageIO.read(input.toFile())){"input is not a decodable image: $input"}
        val resized=resizeRgb(source,U2NetModelPin.width,U2NetModelPin.height)
        val normalized=FloatArray(U2NetModelPin.width*U2NetModelPin.height*3)
        var p=0
        for(y in 0 until U2NetModelPin.height)for(x in 0 until U2NetModelPin.width){
            val rgb=resized.getRGB(x,y)
            normalized[p++]=(((rgb ushr 16) and 0xff)/255.0f)*2.0f-1.0f
            normalized[p++]=(((rgb ushr 8) and 0xff)/255.0f)*2.0f-1.0f
            normalized[p++]=((rgb and 0xff)/255.0f)*2.0f-1.0f
        }

        val matte320=binding.infer(Files.readAllBytes(modelPath),normalized)
        require(matte320.size==U2NetModelPin.width*U2NetModelPin.height){
            "U2Net output size ${matte320.size} does not match expected 320x320"
        }

        val small=BufferedImage(U2NetModelPin.width,U2NetModelPin.height,BufferedImage.TYPE_BYTE_GRAY)
        var i=0
        for(y in 0 until U2NetModelPin.height)for(x in 0 until U2NetModelPin.width){
            val value=(matte320[i++].coerceIn(0.0f,1.0f)*255.0f).roundToInt()
            small.raster.setSample(x,y,0,value)
        }

        val full=resizeGray(small,source.width,source.height)
        output.parent?.let(Files::createDirectories)
        require(ImageIO.write(full,"png",output.toFile())){"PNG writer unavailable"}
    }

    override fun close(){}

    companion object {
        fun verifyEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowU2NetRuntime::class.java.classLoader
        ){
            U2NetModelPin.requirePinnedModel(environment)
            TensorFlowBinding(classLoader).verifyRuntime()
        }

        fun fromEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowU2NetRuntime::class.java.classLoader
        ):ReflectiveTensorFlowU2NetRuntime {
            val model=U2NetModelPin.requirePinnedModel(environment)
            val binding=TensorFlowBinding(classLoader)
            binding.verifyRuntime()
            return ReflectiveTensorFlowU2NetRuntime(model,binding)
        }

        private fun resizeRgb(source:BufferedImage,width:Int,height:Int):BufferedImage {
            val out=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
            val g=out.createGraphics()
            try{
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(source,0,0,width,height,null)
            }finally{g.dispose()}
            return out
        }

        private fun resizeGray(source:BufferedImage,width:Int,height:Int):BufferedImage {
            val out=BufferedImage(width,height,BufferedImage.TYPE_BYTE_GRAY)
            val g=out.createGraphics()
            try{
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY)
                g.drawImage(source,0,0,width,height,null)
            }finally{g.dispose()}
            return out
        }
    }
}

internal class TensorFlowBinding(private val classLoader:ClassLoader) {
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

    fun infer(graphBytes:ByteArray,input:FloatArray):FloatArray {
        require(input.size==U2NetModelPin.width*U2NetModelPin.height*3){"unexpected U2Net input size"}
        val graph=graphClass.getDeclaredConstructor().newInstance()
        var session:Any?=null
        var inputTensor:Any?=null
        var outputTensor:Any?=null
        try{
            val graphDef=parseGraphDef(graphBytes)
            invokeCompatible(graph,"importGraphDef",graphDef)
            session=sessionClass.getConstructor(graphClass).newInstance(graph)

            val shape=shapeClass.getMethod("of",LongArray::class.java)
                .invoke(null,longArrayOf(1,U2NetModelPin.height.toLong(),U2NetModelPin.width.toLong(),3))
            inputTensor=tFloat32Class.getMethod("tensorOf",shapeClass).invoke(null,shape)
            val inputData=invokeCompatible(inputTensor,"data")?:inputTensor
            val setFloat=findMethod(inputData.javaClass,"setFloat",2)

            var p=0
            for(y in 0 until U2NetModelPin.height)for(x in 0 until U2NetModelPin.width)for(c in 0..2){
                setFloat.invoke(inputData,input[p++],longArrayOf(0,y.toLong(),x.toLong(),c.toLong()))
            }

            var runner=invokeCompatible(session,"runner")?:error("TensorFlow Session.runner() returned null")
            runner=invokeCompatible(runner,"feed",U2NetModelPin.inputTensor,inputTensor)?:runner
            runner=invokeCompatible(runner,"fetch",U2NetModelPin.outputTensor)?:runner
            val outputs=invokeCompatible(runner,"run") as? List<*> ?:error("TensorFlow runner did not return a tensor list")
            outputTensor=outputs.firstOrNull()?:error("TensorFlow U2Net returned no output tensor")
            val outputData=invokeCompatible(outputTensor,"data")?:outputTensor
            val getFloat=findMethod(outputData.javaClass,"getFloat",1)

            val result=FloatArray(U2NetModelPin.width*U2NetModelPin.height)
            var o=0
            for(y in 0 until U2NetModelPin.height)for(x in 0 until U2NetModelPin.width){
                val v=getFloat.invoke(outputData,longArrayOf(0,y.toLong(),x.toLong(),0)) as Number
                result[o++]=v.toFloat()
            }
            return result
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
        throw IllegalStateException("required TensorFlow class is unavailable: $name",t)
    }

    private fun invokeCompatible(target:Any,name:String,vararg args:Any?):Any? {
        val method=target.javaClass.methods.firstOrNull{
            it.name==name&&it.parameterCount==args.size&&parametersCompatible(it,args)
        }?:error("${target.javaClass.name}.$name(${args.size}) is unavailable")
        return method.invoke(target,*args)
    }

    private fun findMethod(type:Class<*>,name:String,count:Int):Method =
        type.methods.firstOrNull{it.name==name&&it.parameterCount==count}
            ?:error("${type.name}.$name($count) is unavailable")

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
