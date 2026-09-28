package studio.cosmosis.provider

import java.util.Base64

class GeminiProvider(
    private val apiKey:()->String?={System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY")},
    private val baseUrl:String="https://generativelanguage.googleapis.com/v1beta",
    private val http:HttpSupport=HttpSupport()
):ImageProvider {
    override val id="gemini"
    private val registry:List<ModelDefinition> = ModelRegistry.bundled().definitionsFor(id).ifEmpty { listOf(
        ModelDefinition(id,"gemini-3.1-flash-image","Gemini image route",ProviderCapabilities(textToImage=true,imageToImage=true,multipleReferences=true,multiTurnEditing=true,maxReferenceImages=14,outputFormats=setOf("png","jpeg")))
    ) }
    override fun models()=registry
    override fun capabilities(model:String)=registry.find{it.id==model}?.capabilities ?: registry.first().capabilities
    private fun headers():Map<String,String> { val key=apiKey()?.takeIf{it.isNotBlank()} ?: error("GEMINI_API_KEY / GOOGLE_API_KEY is not configured"); return mapOf("x-goog-api-key" to key) }
    override fun generate(request:GenerationRequest)=call(request,false)
    override fun edit(request:GenerationRequest)=call(request,true)
    private fun call(request:GenerationRequest,editing:Boolean):GenerationResult {
        CapabilityValidator.validate(request,capabilities(request.model),editing)
        if(editing) require(request.references.isNotEmpty()){ "Gemini edit requires an input image" }
        val start=System.nanoTime();val body=GeminiWire.interactionsBody(request)
        val(code,json)=http.json("POST","$baseUrl/interactions",headers(),body)
        if(code !in 200..299) error("Gemini image HTTP $code: ${json.take(800)}")
        val blocks=JsonUtil.imageBlocks(json)
        if(blocks.isEmpty()) error("Gemini interaction contained no image output blocks")
        val interactionId=JsonUtil.stringField(json,"id")
        return GenerationResult(
            request.id,id,request.model,
            blocks.map { block -> GeneratedImage(Base64.getDecoder().decode(block.data),block.mimeType,providerAssetId=interactionId) },
            (System.nanoTime()-start)/1_000_000,
            mapOf("interactionId" to (interactionId?:""))
        )
    }
    override fun testConnection():ConnectionStatus {
        val s=System.nanoTime()
        return try { val(c,b)=http.json("GET","$baseUrl/models/${registry.first().id}",headers());ConnectionStatus(c in 200..299,"HTTP $c ${if(c in 200..299)"ready" else b.take(120)}",(System.nanoTime()-s)/1_000_000) }
        catch(e:Exception){ConnectionStatus(false,e.message?:e.javaClass.simpleName,(System.nanoTime()-s)/1_000_000)}
    }
}
