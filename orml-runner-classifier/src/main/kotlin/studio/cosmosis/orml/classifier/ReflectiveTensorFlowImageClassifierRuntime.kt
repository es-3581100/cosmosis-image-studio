package studio.cosmosis.orml.classifier

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class ReflectiveTensorFlowImageClassifierRuntime private constructor(
    private val modelPath:Path,
    private val binding:ImageClassifierTensorFlowBinding
):ImageClassifierRuntime {
    override fun infer(input:Path):ImageClassifierInference {
        require(Files.isRegularFile(input)){"input image does not exist: $input"}
        val source=requireNotNull(ImageIO.read(input.toFile())){"input is not a decodable image: $input"}
        val normalized=normalizeClassifierInput(source)
        return binding.infer(Files.readAllBytes(modelPath),normalized)
    }

    override fun close(){}

    companion object {
        fun verifyEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowImageClassifierRuntime::class.java.classLoader
        ){
            ImageClassifierModelPin.requirePinnedModel(environment)
            ImageClassifierTensorFlowBinding(classLoader).verifyRuntime()
        }

        fun fromEnvironment(
            environment:Map<String,String> = System.getenv(),
            classLoader:ClassLoader = Thread.currentThread().contextClassLoader
                ?:ReflectiveTensorFlowImageClassifierRuntime::class.java.classLoader
        ):ReflectiveTensorFlowImageClassifierRuntime {
            val model=ImageClassifierModelPin.requirePinnedModel(environment)
            val binding=ImageClassifierTensorFlowBinding(classLoader)
            binding.verifyRuntime()
            return ReflectiveTensorFlowImageClassifierRuntime(model,binding)
        }
    }
}

internal fun normalizeClassifierInput(source:BufferedImage):FloatArray {
    val resized=BufferedImage(
        ImageClassifierModelPin.width,
        ImageClassifierModelPin.height,
        BufferedImage.TYPE_INT_RGB
    )
    val g=resized.createGraphics()
    try{
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(source,0,0,resized.width,resized.height,null)
    }finally{g.dispose()}

    // Upstream ORML copies into its 224x224 FLOAT32 input buffer with a
    // negative target height, which is an explicit vertical flip.
    val normalized=FloatArray(resized.width*resized.height*3)
    var p=0
    for(y in 0 until resized.height){
        val sourceY=resized.height-1-y
        for(x in 0 until resized.width){
            val rgb=resized.getRGB(x,sourceY)
            normalized[p++]=((rgb ushr 16) and 0xff)/255.0f
            normalized[p++]=((rgb ushr 8) and 0xff)/255.0f
            normalized[p++]=(rgb and 0xff)/255.0f
        }
    }
    return normalized
}

internal class ImageClassifierTensorFlowBinding(private val classLoader:ClassLoader) {
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

    fun infer(graphBytes:ByteArray,input:FloatArray):ImageClassifierInference {
        require(input.size==ImageClassifierModelPin.width*ImageClassifierModelPin.height*3){
            "unexpected classifier input size"
        }

        val graph=graphClass.getDeclaredConstructor().newInstance()
        var session:Any?=null
        var inputTensor:Any?=null
        var classTensor:Any?=null
        var embeddingTensor:Any?=null
        try{
            val graphDef=parseGraphDef(graphBytes)
            invokeCompatible(graph,"importGraphDef",graphDef)
            session=sessionClass.getConstructor(graphClass).newInstance(graph)

            val shape=shapeClass.getMethod("of",LongArray::class.java)
                .invoke(null,longArrayOf(1,ImageClassifierModelPin.height.toLong(),ImageClassifierModelPin.width.toLong(),3))
            inputTensor=tFloat32Class.getMethod("tensorOf",shapeClass).invoke(null,shape)
            val setFloat=findMethod(inputTensor.javaClass,"setFloat",2)

            var p=0
            for(y in 0 until ImageClassifierModelPin.height)for(x in 0 until ImageClassifierModelPin.width)for(c in 0..2){
                setFloat.invoke(inputTensor,input[p++],longArrayOf(0,y.toLong(),x.toLong(),c.toLong()))
            }

            var runner=invokeCompatible(session,"runner")?:error("TensorFlow Session.runner() returned null")
            runner=invokeCompatible(runner,"feed",ImageClassifierModelPin.inputTensor,inputTensor)?:runner
            runner=invokeCompatible(runner,"fetch",ImageClassifierModelPin.classificationTensor)?:runner
            runner=invokeCompatible(runner,"fetch",ImageClassifierModelPin.embeddingTensor)?:runner
            val outputs=invokeCompatible(runner,"run") as? List<*>
                ?:error("TensorFlow runner did not return a tensor list")
            require(outputs.size>=2){"classifier graph returned fewer than two output tensors"}
            classTensor=outputs[0]?:error("classification tensor is null")
            embeddingTensor=outputs[1]?:error("embedding tensor is null")
            require(tFloat32Class.isInstance(classTensor)){"classification tensor is not TFloat32"}
            require(tFloat32Class.isInstance(embeddingTensor)){"embedding tensor is not TFloat32"}

            return ImageClassifierInference(
                classScores=readFloatTensor(classTensor),
                embedding=readFloatTensor(embeddingTensor)
            )
        }finally{
            closeQuietly(embeddingTensor)
            closeQuietly(classTensor)
            closeQuietly(inputTensor)
            closeQuietly(session)
            closeQuietly(graph)
        }
    }

    private fun readFloatTensor(tensor:Any):FloatArray {
        val shape=invokeCompatible(tensor,"shape")?:error("TensorFlow tensor shape is unavailable")
        val dims=invokeCompatible(shape,"asArray") as? LongArray
            ?:error("TensorFlow tensor dimensions are unavailable")
        require(dims.isNotEmpty()&&dims.size<=4){"unsupported tensor dimensions: "+dims.size}
        require(dims.all{it>0}){"tensor contains non-positive dimensions"}
        val total=dims.fold(1L){acc,v->Math.multiplyExact(acc,v)}
        require(total<=1_000_000L){"tensor output is unexpectedly large: "+total}
        val getFloat=findMethod(tensor.javaClass,"getFloat",1)
        val result=FloatArray(total.toInt())
        for(linear in result.indices){
            var remainder=linear.toLong()
            val indices=LongArray(dims.size)
            for(i in dims.indices.reversed()){
                indices[i]=remainder%dims[i]
                remainder/=dims[i]
            }
            result[linear]=(getFloat.invoke(tensor,indices) as Number).toFloat()
        }
        return result
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
