package studio.cosmosis.provider

import java.util.Base64

open class OpenAiProvider(
    private val apiKey:()->String?={System.getenv("OPENAI_API_KEY")},
    private val baseUrl:String="https://api.openai.com/v1",
    override val id:String="openai",
    private val http:HttpSupport=HttpSupport()
):ImageProvider {
    private val registry:List<ModelDefinition> = ModelRegistry.bundled().definitionsFor(id).ifEmpty { listOf(
        ModelDefinition(id,"route-configured","Configured OpenAI-compatible route",ProviderCapabilities(textToImage=true,imageToImage=true,maskEditing=true,multipleReferences=true,multiTurnEditing=false,transparentBackground=true,customDimensions=true,parallelVariants=true,maxReferenceImages=16,qualityLevels=setOf("auto","low","medium","high"),outputFormats=setOf("png","webp","jpeg"),promptRevision=true))
    ) }
    override fun models()=registry
    override fun capabilities(model:String)=registry.find{it.id==model}?.capabilities ?: ProviderCapabilities(textToImage=true,imageToImage=true,maskEditing=true,multipleReferences=true,maxReferenceImages=16,qualityLevels=setOf("auto"),outputFormats=setOf("png","webp","jpeg"))
    protected fun headers():Map<String,String> { val key=apiKey()?.takeIf{it.isNotBlank()} ?: error("OPENAI_API_KEY is not configured"); return mapOf("Authorization" to "Bearer $key") }
    override fun generate(request:GenerationRequest):GenerationResult {
        if(request.metadata["openAiWorkflow"]?.equals("responses",true)==true) return responsesGenerate(request)
        CapabilityValidator.validate(request,capabilities(request.model)); val start=System.nanoTime()
        val body=OpenAiWire.generationBody(request)
        val (code,json)=http.json("POST","$baseUrl/images/generations",headers(),body); if(code !in 200..299) error("OpenAI image generation HTTP $code: ${json.take(800)}")
        return parse(request,json,start)
    }
    override fun edit(request:GenerationRequest):GenerationResult {
        if(request.metadata["openAiWorkflow"]?.equals("responses",true)==true && request.mask==null) return responsesGenerate(request)
        val caps=capabilities(request.model); CapabilityValidator.validate(request,caps,true); require(request.references.isNotEmpty()){ "OpenAI edit requires at least one input image" }; val start=System.nanoTime()
        val fields=linkedMapOf("model" to request.model,"prompt" to request.prompt,"n" to request.variants.toString(),"output_format" to request.outputFormat).apply { request.quality?.let{put("quality",it)}; if(request.transparent)put("background","transparent"); if(request.width!=null&&request.height!=null)put("size","${request.width}x${request.height}"); request.metadata["compression"]?.let{put("output_compression",it)} }
        val files=request.references.mapIndexed { i,r -> Triple("image[]",r,"image-$i.${ext(r.mime)}") }.toMutableList(); request.mask?.let{files += Triple("mask",it,"mask.png")}
        val(code,json)=http.multipart("$baseUrl/images/edits",headers(),fields,files); if(code !in 200..299) error("OpenAI image edit HTTP $code: ${json.take(800)}")
        return parse(request,json,start)
    }
    fun responsesGenerate(request:GenerationRequest):GenerationResult {
        CapabilityValidator.validate(request,capabilities(request.model)); val start=System.nanoTime()
        val content=mutableListOf(JsonUtil.obj("type" to JsonUtil.quote("input_text"),"text" to JsonUtil.quote(request.prompt)))
        request.references.forEach { content += JsonUtil.obj("type" to JsonUtil.quote("input_image"),"image_url" to JsonUtil.quote(http.dataUri(it))) }
        val tool=JsonUtil.obj("type" to JsonUtil.quote("image_generation"),"model" to JsonUtil.quote(request.model),"action" to JsonUtil.quote(if(request.references.isEmpty())"generate" else "auto"),"quality" to request.quality?.let(JsonUtil::quote),"background" to JsonUtil.quote(if(request.transparent)"transparent" else "auto"),"output_format" to JsonUtil.quote(request.outputFormat))
        val reasoningModel=request.metadata["reasoningModel"]?.takeIf{it.isNotBlank()} ?: System.getenv("OPENAI_RESPONSES_MODEL")?.takeIf{it.isNotBlank()} ?: error("OpenAI Responses workflow requires reasoningModel metadata or OPENAI_RESPONSES_MODEL")
        val body=JsonUtil.obj("model" to JsonUtil.quote(reasoningModel),"input" to JsonUtil.arr(listOf(JsonUtil.obj("role" to JsonUtil.quote("user"),"content" to JsonUtil.arr(content)))),"tools" to JsonUtil.arr(listOf(tool)),"previous_response_id" to request.previousResponseId?.let(JsonUtil::quote))
        val(code,json)=http.json("POST","$baseUrl/responses",headers(),body); if(code !in 200..299) error("OpenAI Responses image HTTP $code: ${json.take(800)}")
        val b64s=JsonUtil.allStringFields(json,"result") + JsonUtil.allStringFields(json,"b64_json")
        if(b64s.isEmpty()) error("OpenAI Responses result contained no image payload")
        val revised=JsonUtil.stringField(json,"revised_prompt"); val responseId=JsonUtil.stringField(json,"id")
        return GenerationResult(request.id,id,request.model,b64s.map{GeneratedImage(Base64.getDecoder().decode(it),"image/${request.outputFormat}",revised,responseId)},(System.nanoTime()-start)/1_000_000,mapOf("responseId" to (responseId?:"")))
    }
    override fun testConnection():ConnectionStatus { val s=System.nanoTime(); return try { val(c,b)=http.json("GET","$baseUrl/models",headers()); ConnectionStatus(c in 200..299,"HTTP $c ${if(c in 200..299)"ready" else b.take(120)}",(System.nanoTime()-s)/1_000_000) } catch(e:Exception){ConnectionStatus(false,e.message?:e.javaClass.simpleName,(System.nanoTime()-s)/1_000_000)} }
    private fun parse(request:GenerationRequest,json:String,start:Long):GenerationResult { val b64s=JsonUtil.allStringFields(json,"b64_json"); if(b64s.isEmpty()) error("Provider response contained no b64_json image"); val revisions=JsonUtil.allStringFields(json,"revised_prompt"); return GenerationResult(request.id,id,request.model,b64s.mapIndexed{i,b->GeneratedImage(Base64.getDecoder().decode(b),"image/${request.outputFormat}",revisions.getOrNull(i))},(System.nanoTime()-start)/1_000_000) }
    private fun ext(mime:String)=when(mime){"image/jpeg"->"jpg";"image/webp"->"webp";else->"png"}
}
