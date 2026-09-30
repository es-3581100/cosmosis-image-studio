package studio.cosmosis.provider

import studio.cosmosis.newId
import java.nio.file.Path

enum class ContinuationProtocol {
    NONE,
    OPENAI_RESPONSES,
    GEMINI_INTERACTIONS
}

data class ProviderCapabilities(
    val textToImage:Boolean=false,
    val imageToImage:Boolean=false,
    val maskEditing:Boolean=false,
    val multipleReferences:Boolean=false,
    val multiTurnEditing:Boolean=false,
    val transparentBackground:Boolean=false,
    val customDimensions:Boolean=false,
    val aspectRatios:Set<String> = emptySet(),
    val imageSizes:Set<String> = emptySet(),
    val searchGrounding:Boolean=false,
    val thinkingConfiguration:Boolean=false,
    val responsesImageGeneration:Boolean=false,
    val outputCompression:Boolean=false,
    val interactionStorage:Boolean=false,
    val continuationProtocol:ContinuationProtocol=ContinuationProtocol.NONE,
    val streamingPreview:Boolean=false,
    val parallelVariants:Boolean=false,
    val maxReferenceImages:Int=0,
    val qualityLevels:Set<String> = emptySet(),
    val outputFormats:Set<String> = setOf("png"),
    val promptRevision:Boolean=false
)

data class ModelDefinition(val provider:String,val id:String,val label:String,val capabilities:ProviderCapabilities,val deprecated:Boolean=false,val notes:String="")
data class ReferenceImage(val path:Path,val mime:String="image/png")
data class GenerationRequest(
    val id:String= newId("req"),
    val prompt:String,
    val model:String,
    val references:List<ReferenceImage> = emptyList(),
    val mask:ReferenceImage?=null,
    val variants:Int=1,
    val width:Int?=null,
    val height:Int?=null,
    val aspectRatio:String?=null,
    val quality:String?=null,
    val outputFormat:String="png",
    val transparent:Boolean=false,
    val previousResponseId:String?=null,
    val metadata:Map<String,String> = emptyMap()
)

data class GeneratedImage(val bytes:ByteArray,val mime:String,val revisedPrompt:String?=null,val providerAssetId:String?=null)
data class GenerationResult(val requestId:String,val provider:String,val model:String,val images:List<GeneratedImage>,val durationMs:Long,val rawMetadata:Map<String,String> = emptyMap())
data class ConnectionStatus(val ok:Boolean,val message:String,val latencyMs:Long)

interface ImageProvider {
    val id:String
    fun capabilities(model:String):ProviderCapabilities = modelDefinition(model).capabilities
    fun models():List<ModelDefinition>
    fun modelDefinition(model:String):ModelDefinition =
        models().firstOrNull{it.id==model}
            ?: throw CapabilityMismatchException(
                provider=id,
                model=model,
                capability="declaredModelRoute",
                option="model",
                declared="available="+models().joinToString(","){it.id}
            )
    fun generate(request:GenerationRequest):GenerationResult
    fun edit(request:GenerationRequest):GenerationResult
    fun testConnection():ConnectionStatus
}

open class CapabilityException(message:String):IllegalArgumentException(message)

class CapabilityMismatchException(
    val provider:String,
    val model:String,
    val capability:String,
    val option:String,
    val declared:String
):CapabilityException(
    "CapabilityMismatch: route $provider/$model does not declare $capability required by request option $option; declared=$declared"
)
