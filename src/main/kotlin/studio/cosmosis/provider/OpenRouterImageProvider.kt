package studio.cosmosis.provider

import java.nio.file.Files
import java.util.Base64

/**
 * OpenRouter's unified Image API is not the OpenAI Images wire contract.
 * It uses POST {baseUrl}/images for both generation and reference editing,
 * and GET {baseUrl}/images/models as the authoritative image-capable catalog.
 */
class OpenRouterImageProvider(
    private val apiKey:()->String?,
    private val baseUrl:String="https://openrouter.ai/api/v1",
    private val modelId:String,
    private val http:HttpSupport=HttpSupport()
):ImageProvider {
    override val id:String="custom"

    private val definition=ModelDefinition(
        provider=id,
        id=modelId,
        label=modelId,
        capabilities=declaredCapabilities(modelId),
        notes=declaredNotes(modelId)
    )

    override fun models()=listOf(definition)

    private fun headers():Map<String,String> {
        val key=apiKey()?.takeIf{it.isNotBlank()} ?: error("OpenRouter/custom API key is not configured")
        return mapOf("Authorization" to "Bearer $key")
    }

    override fun generate(request:GenerationRequest):GenerationResult {
        val declared=modelDefinition(request.model)
        CapabilityValidator.validate(request,declared,false)
        return execute(request,includeReferences=false)
    }

    override fun edit(request:GenerationRequest):GenerationResult {
        val declared=modelDefinition(request.model)
        CapabilityValidator.validate(request,declared,true)
        require(request.references.isNotEmpty()){"OpenRouter image editing requires at least one reference image"}
        return execute(request,includeReferences=true)
    }

    private fun execute(request:GenerationRequest,includeReferences:Boolean):GenerationResult {
        val start=System.nanoTime()
        val fields=mutableListOf<Pair<String,String?>>(
            "model" to JsonUtil.quote(request.model),
            "prompt" to JsonUtil.quote(request.prompt),
            "n" to request.variants.takeIf{it>1}?.toString(),
            "quality" to request.quality?.takeIf{it.isNotBlank()&&!it.equals("auto",true)}?.let(JsonUtil::quote),
            "output_format" to JsonUtil.quote(request.outputFormat),
            "aspect_ratio" to request.aspectRatio?.let(JsonUtil::quote),
            "resolution" to request.metadata["imageSize"]?.let(JsonUtil::quote),
            "input_references" to request.references.takeIf{includeReferences && it.isNotEmpty()}?.let(::referenceArray)
        )
        val body=fields.filter{it.second!=null}.joinToString(prefix="{",postfix="}") {
            JsonUtil.quote(it.first)+":"+it.second
        }
        val endpoint=baseUrl.trimEnd('/')+"/images"
        val(code,json)=http.json("POST",endpoint,headers(),body,timeoutSeconds=120)
        if(code !in 200..299) throw ProviderHttpException("OpenRouter image",code,endpoint,json)
        val payloads=JsonUtil.allStringFields(json,"b64_json")
        if(payloads.isEmpty()) error("OpenRouter image response contained no data[].b64_json payload")
        val mediaTypes=JsonUtil.allStringFields(json,"media_type")
        return GenerationResult(
            request.id,id,request.model,
            payloads.mapIndexed { index,b64 ->
                GeneratedImage(
                    Base64.getDecoder().decode(b64),
                    mediaTypes.getOrNull(index)?.takeIf{it.startsWith("image/")}
                        ?: "image/"+if(request.outputFormat=="jpg")"jpeg" else request.outputFormat
                )
            },
            (System.nanoTime()-start)/1_000_000
        )
    }

    private fun referenceArray(refs:List<ReferenceImage>):String =
        JsonUtil.arr(refs.map { ref ->
            val bytes=Files.readAllBytes(ref.path)
            val dataUrl="data:"+ref.mime+";base64,"+Base64.getEncoder().encodeToString(bytes)
            JsonUtil.obj(
                "type" to JsonUtil.quote("image_url"),
                "image_url" to JsonUtil.obj("url" to JsonUtil.quote(dataUrl))
            )
        })

    override fun testConnection():ConnectionStatus {
        val start=System.nanoTime()
        return try {
            val(code,json)=http.json("GET",baseUrl.trimEnd('/')+"/images/models",headers(),timeoutSeconds=20)
            if(code !in 200..299) {
                ConnectionStatus(false,"OpenRouter image catalog HTTP $code "+json.take(160),(System.nanoTime()-start)/1_000_000)
            } else {
                val imageModelIds=JsonUtil.allStringFields(json,"id").toSet()
                if(modelId in imageModelIds) {
                    val caps=definition.capabilities
                    val mode=when{
                        caps.imageToImage && caps.textToImage -> "generate + edit"
                        caps.imageToImage -> "edit"
                        else -> "generate"
                    }
                    ConnectionStatus(true,"OpenRouter Image API ready / $mode / model is image-capable",(System.nanoTime()-start)/1_000_000)
                } else {
                    ConnectionStatus(
                        false,
                        "Model '$modelId' is not in OpenRouter /images/models. It may be text/vision-only; choose an image-output model.",
                        (System.nanoTime()-start)/1_000_000
                    )
                }
            }
        } catch(e:Exception) {
            ConnectionStatus(false,e.message?:e.javaClass.simpleName,(System.nanoTime()-start)/1_000_000)
        }
    }

    companion object {
        fun declaredCapabilities(modelId:String):ProviderCapabilities = when(modelId) {
            "inclusionai/ming-image-0.1-design" -> ProviderCapabilities(
                textToImage=true,
                imageToImage=false,
                maskEditing=false,
                multipleReferences=false,
                maxReferenceImages=0,
                customDimensions=false,
                parallelVariants=false,
                qualityLevels=emptySet(),
                outputFormats=setOf("png","jpeg","webp")
            )
            "inclusionai/ming-image-0.1-design-layer" -> ProviderCapabilities(
                textToImage=false,
                imageToImage=true,
                maskEditing=false,
                multipleReferences=false,
                maxReferenceImages=1,
                customDimensions=false,
                parallelVariants=false,
                qualityLevels=emptySet(),
                outputFormats=setOf("png","webp")
            )
            else -> ProviderCapabilities(
                textToImage=true,
                imageToImage=false,
                maskEditing=false,
                multipleReferences=false,
                maxReferenceImages=0,
                transparentBackground=false,
                customDimensions=false,
                aspectRatios=emptySet(),
                imageSizes=emptySet(),
                searchGrounding=false,
                thinkingConfiguration=false,
                responsesImageGeneration=false,
                outputCompression=false,
                interactionStorage=false,
                continuationProtocol=ContinuationProtocol.NONE,
                streamingPreview=false,
                parallelVariants=false,
                qualityLevels=setOf("auto","low","medium","high"),
                outputFormats=setOf("png","jpeg","webp"),
                promptRevision=false
            )
        }

        private fun declaredNotes(modelId:String):String = when(modelId) {
            "inclusionai/ming-image-0.1-design" ->
                "OpenRouter Ming Design: prompt-only text-to-image; reference editing is not supported."
            "inclusionai/ming-image-0.1-design-layer" ->
                "OpenRouter Ming Design Layer: image-to-image only; requires exactly one reference image; PNG/WebP output."
            else ->
                "OpenRouter unified Image API route. Unknown custom models default to conservative generation-only capabilities until explicitly declared."
        }
    }
}
