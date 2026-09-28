package studio.cosmosis.provider

import studio.cosmosis.newId
import java.nio.file.Path

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
    fun capabilities(model:String):ProviderCapabilities
    fun models():List<ModelDefinition>
    fun generate(request:GenerationRequest):GenerationResult
    fun edit(request:GenerationRequest):GenerationResult
    fun testConnection():ConnectionStatus
}

class CapabilityException(message:String):IllegalArgumentException(message)
