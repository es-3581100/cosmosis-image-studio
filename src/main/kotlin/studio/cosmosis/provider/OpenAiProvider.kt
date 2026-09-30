package studio.cosmosis.provider

import java.util.Base64

open class OpenAiProvider(
    private val apiKey:()->String?={System.getenv("OPENAI_API_KEY")},
    private val baseUrl:String="https://api.openai.com/v1",
    override val id:String="openai",
    private val http:HttpSupport=HttpSupport(),
    modelDefinitions:List<ModelDefinition>?=null
):ImageProvider {
    private val registry:List<ModelDefinition> = modelDefinitions ?: ModelRegistry.bundled().definitionsFor(id).ifEmpty { listOf(
        ModelDefinition(id,"route-configured","Configured OpenAI-compatible route",ProviderCapabilities(textToImage=true,imageToImage=true,maskEditing=true,multipleReferences=true,multiTurnEditing=false,transparentBackground=true,customDimensions=true,parallelVariants=true,maxReferenceImages=16,qualityLevels=setOf("auto","low","medium","high"),outputFormats=setOf("png","webp","jpeg"),promptRevision=true))
    ) }
    override fun models()=registry
    override fun capabilities(model:String)=modelDefinition(model).capabilities
    protected fun headers():Map<String,String> { val key=apiKey()?.takeIf{it.isNotBlank()} ?: error("OPENAI_API_KEY is not configured"); return mapOf("Authorization" to "Bearer $key") }

    override fun generate(request:GenerationRequest):GenerationResult {
        val definition=modelDefinition(request.model)
        CapabilityValidator.validate(request,definition,false)
        if(request.metadata["openAiWorkflow"]?.equals("responses",true)==true) return responsesGenerate(request,definition,false)
        validateOpenAiRequest(request)
        val start=System.nanoTime()
        val body=OpenAiWire.generationBody(request)
        val (code,json)=http.json("POST","$baseUrl/images/generations",headers(),body)
        if(code !in 200..299) error("OpenAI image generation HTTP $code: ${json.take(800)}")
        return parse(request,json,start)
    }

    override fun edit(request:GenerationRequest):GenerationResult {
        val definition=modelDefinition(request.model)
        CapabilityValidator.validate(request,definition,true)
        if(request.metadata["openAiWorkflow"]?.equals("responses",true)==true && request.mask==null) return responsesGenerate(request,definition,true)
        validateOpenAiRequest(request)
        require(request.references.isNotEmpty()){ "OpenAI edit requires at least one input image" }
        val start=System.nanoTime()
        val fields=linkedMapOf("model" to request.model,"prompt" to request.prompt,"n" to request.variants.toString(),"output_format" to request.outputFormat).apply {
            request.quality?.let{put("quality",it)}
            if(request.transparent)put("background","transparent")
            if(request.width!=null&&request.height!=null)put("size","${request.width}x${request.height}")
            request.metadata["compression"]?.let{put("output_compression",it)}
        }
        val files=request.references.mapIndexed { i,r -> Triple("image[]",r,"image-$i.${ext(r.mime)}") }.toMutableList()
        request.mask?.let{files += Triple("mask",it,"mask.png")}
        val(code,json)=http.multipart("$baseUrl/images/edits",headers(),fields,files)
        if(code !in 200..299) error("OpenAI image edit HTTP $code: ${json.take(800)}")
        return parse(request,json,start)
    }

    private fun responsesGenerate(request:GenerationRequest,definition:ModelDefinition,editing:Boolean):GenerationResult {
        CapabilityValidator.validate(request,definition,editing)
        validateOpenAiRequest(request)
        require(request.variants==1){"OpenAI Responses image workflow supports one admitted image per conversational turn in Cosmosis; use the direct Images API or Agent Build for multiple variants"}
        val start=System.nanoTime()
        val content=mutableListOf(JsonUtil.obj("type" to JsonUtil.quote("input_text"),"text" to JsonUtil.quote(request.prompt)))
        request.references.forEach { content += JsonUtil.obj("type" to JsonUtil.quote("input_image"),"image_url" to JsonUtil.quote(http.dataUri(it))) }
        val tool=OpenAiWire.responsesImageTool(request)
        val reasoningModel=request.metadata["reasoningModel"]?.takeIf{it.isNotBlank()} ?: System.getenv("OPENAI_RESPONSES_MODEL")?.takeIf{it.isNotBlank()} ?: error("OpenAI Responses workflow requires reasoningModel metadata or OPENAI_RESPONSES_MODEL")
        val body=JsonUtil.obj("model" to JsonUtil.quote(reasoningModel),"input" to JsonUtil.arr(listOf(JsonUtil.obj("role" to JsonUtil.quote("user"),"content" to JsonUtil.arr(content)))),"tools" to JsonUtil.arr(listOf(tool)),"previous_response_id" to request.previousResponseId?.let(JsonUtil::quote))
        val(code,json)=http.json("POST","$baseUrl/responses",headers(),body)
        if(code !in 200..299) error("OpenAI Responses image HTTP $code: ${json.take(800)}")
        val b64s=JsonUtil.allStringFields(json,"result") + JsonUtil.allStringFields(json,"b64_json")
        if(b64s.isEmpty()) error("OpenAI Responses result contained no image payload")
        val revised=JsonUtil.stringField(json,"revised_prompt")
        val responseId=JsonUtil.stringField(json,"id")
        return GenerationResult(request.id,id,request.model,b64s.map{GeneratedImage(Base64.getDecoder().decode(it),"image/${request.outputFormat}",revised,responseId)},(System.nanoTime()-start)/1_000_000,mapOf("responseId" to (responseId?:"")))
    }

    private fun validateOpenAiRequest(request:GenerationRequest){
        val w=request.width;val h=request.height
        if(w!=null&&h!=null){
            require(w%16==0&&h%16==0){"OpenAI image width and height must both be divisible by 16"}
            require(w<=3840&&h<=3840){"OpenAI image edges must not exceed 3840 pixels"}
            val pixels=w.toLong()*h.toLong();require(pixels in 655_360L..8_294_400L){"OpenAI image pixel count must be between 655,360 and 8,294,400"}
            val ratio=maxOf(w,h).toDouble()/minOf(w,h).toDouble();require(ratio<=3.0){"OpenAI image aspect ratio must be between 1:3 and 3:1"}
        }
        request.metadata["compression"]?.let{raw->
            val value=raw.toIntOrNull()?:throw IllegalArgumentException("OpenAI output compression must be an integer from 0 to 100")
            require(value in 0..100){"OpenAI output compression must be between 0 and 100"}
            require(request.outputFormat.lowercase() in setOf("jpeg","webp")){"OpenAI output compression is only valid for JPEG or WebP"}
        }
    }

    override fun testConnection():ConnectionStatus {
        val s=System.nanoTime()
        return try {
            val(c,b)=http.json("GET","$baseUrl/models",headers())
            ConnectionStatus(c in 200..299,"HTTP $c ${if(c in 200..299)"ready" else b.take(120)}",(System.nanoTime()-s)/1_000_000)
        } catch(e:Exception){ConnectionStatus(false,e.message?:e.javaClass.simpleName,(System.nanoTime()-s)/1_000_000)}
    }
    private fun parse(request:GenerationRequest,json:String,start:Long):GenerationResult {
        val b64s=JsonUtil.allStringFields(json,"b64_json")
        if(b64s.isEmpty()) error("Provider response contained no b64_json image")
        val revisions=JsonUtil.allStringFields(json,"revised_prompt")
        return GenerationResult(request.id,id,request.model,b64s.mapIndexed{i,b->GeneratedImage(Base64.getDecoder().decode(b),"image/${request.outputFormat}",revisions.getOrNull(i))},(System.nanoTime()-start)/1_000_000)
    }
    private fun ext(mime:String)=when(mime){"image/jpeg"->"jpg";"image/webp"->"webp";else->"png"}
}
