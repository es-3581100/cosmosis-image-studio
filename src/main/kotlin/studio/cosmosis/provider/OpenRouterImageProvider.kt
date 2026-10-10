package studio.cosmosis.provider

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
        capabilities=ProviderCapabilities(
            textToImage=true,
            imageToImage=false,
            maskEditing=false,
            multipleReferences=false,
            multiTurnEditing=false,
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
            maxReferenceImages=0,
            qualityLevels=setOf("auto","low","medium","high"),
            outputFormats=setOf("png","jpeg","webp"),
            promptRevision=false
        ),
        notes="OpenRouter unified Image API route. Exact image capability is verified against /images/models by TEST SELECTED PROVIDER."
    )

    override fun models()=listOf(definition)

    private fun headers():Map<String,String> {
        val key=apiKey()?.takeIf{it.isNotBlank()} ?: error("OpenRouter/custom API key is not configured")
        return mapOf("Authorization" to "Bearer $key")
    }

    override fun generate(request:GenerationRequest):GenerationResult {
        val declared=modelDefinition(request.model)
        CapabilityValidator.validate(request,declared,false)
        val start=System.nanoTime()
        val fields=mutableListOf<Pair<String,String?>>(
            "model" to JsonUtil.quote(request.model),
            "prompt" to JsonUtil.quote(request.prompt),
            "n" to request.variants.takeIf{it>1}?.toString(),
            "quality" to request.quality?.takeIf{it.isNotBlank()&&!it.equals("auto",true)}?.let(JsonUtil::quote),
            "output_format" to JsonUtil.quote(request.outputFormat),
            "aspect_ratio" to request.aspectRatio?.let(JsonUtil::quote),
            "resolution" to request.metadata["imageSize"]?.let(JsonUtil::quote)
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

    override fun edit(request:GenerationRequest):GenerationResult {
        throw CapabilityMismatchException(
            provider=id,
            model=request.model,
            capability="imageToImage",
            option="editing",
            declared="OpenRouter reference/edit capability must be discovered from /images/models before admission"
        )
    }

    override fun testConnection():ConnectionStatus {
        val start=System.nanoTime()
        return try {
            val(code,json)=http.json("GET",baseUrl.trimEnd('/')+"/images/models",headers(),timeoutSeconds=20)
            if(code !in 200..299) {
                ConnectionStatus(false,"OpenRouter image catalog HTTP $code "+json.take(160),(System.nanoTime()-start)/1_000_000)
            } else {
                val imageModelIds=JsonUtil.allStringFields(json,"id").toSet()
                if(modelId in imageModelIds) {
                    ConnectionStatus(true,"OpenRouter Image API ready / model is image-capable",(System.nanoTime()-start)/1_000_000)
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
}
