package studio.cosmosis.provider

import java.nio.file.Files
import java.nio.file.Path

data class RegistryModel(
    val provider:String,
    val id:String,
    val label:String=id,
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
    val promptRevision:Boolean=false,
    val deprecated:Boolean=false,
    val notes:String=""
){
    fun definition()=ModelDefinition(provider,id,label,ProviderCapabilities(
        textToImage=textToImage,imageToImage=imageToImage,maskEditing=maskEditing,multipleReferences=multipleReferences,
        multiTurnEditing=multiTurnEditing,transparentBackground=transparentBackground,customDimensions=customDimensions,
        aspectRatios=aspectRatios,imageSizes=imageSizes,searchGrounding=searchGrounding,streamingPreview=streamingPreview,parallelVariants=parallelVariants,
        maxReferenceImages=maxReferenceImages,qualityLevels=qualityLevels,outputFormats=outputFormats,promptRevision=promptRevision
    ),deprecated,notes)
}

class ModelRegistry private constructor(val schemaVersion:Int,val models:List<RegistryModel>){
    fun forProvider(provider:String)=models.filter{it.provider.equals(provider,true)}
    fun definitionsFor(provider:String)=forProvider(provider).map{it.definition()}
    fun find(provider:String,id:String)=models.firstOrNull{it.provider.equals(provider,true)&&it.id==id}
    fun migrate():ModelRegistry = when {
        schemaVersion >= CURRENT_SCHEMA -> this
        else -> ModelRegistry(CURRENT_SCHEMA,models)
    }
    companion object {
        const val CURRENT_SCHEMA=3
        fun load(path:Path):ModelRegistry=parse(Files.readString(path)).migrate()
        fun bundled():ModelRegistry { val stream=ModelRegistry::class.java.getResourceAsStream("/config/model-registry.json") ?: return ModelRegistry(CURRENT_SCHEMA,emptyList());return parse(stream.bufferedReader().use{it.readText()}).migrate() }
        fun parse(json:String):ModelRegistry{
            val version=Regex("\\\"schemaVersion\\\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()?:1
            val objectRx=Regex("\\{([^{}]*\\\"provider\\\"[^{}]*)}")
            fun str(b:String,k:String)=Regex("\\\"$k\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(b)?.groupValues?.get(1).orEmpty()
            fun bool(b:String,k:String)=Regex("\\\"$k\\\"\\s*:\\s*(true|false)").find(b)?.groupValues?.get(1)?.toBoolean()?:false
            fun int(b:String,k:String)=Regex("\\\"$k\\\"\\s*:\\s*(\\d+)").find(b)?.groupValues?.get(1)?.toIntOrNull()?:0
            fun set(b:String,k:String)=str(b,k).split(',').map{it.trim()}.filter{it.isNotBlank()}.toSet()
            val models=objectRx.findAll(json).map{m->
                val b=m.groupValues[1]
                RegistryModel(
                    provider=str(b,"provider"),id=str(b,"id"),label=str(b,"label").ifBlank{str(b,"id")},
                    textToImage=bool(b,"textToImage"),imageToImage=bool(b,"imageToImage"),maskEditing=bool(b,"maskEditing"),
                    multipleReferences=bool(b,"multipleReferences"),multiTurnEditing=bool(b,"multiTurnEditing"),transparentBackground=bool(b,"transparentBackground"),
                    customDimensions=bool(b,"customDimensions"),aspectRatios=set(b,"aspectRatios"),imageSizes=set(b,"imageSizes"),searchGrounding=bool(b,"searchGrounding"),
                    streamingPreview=bool(b,"streamingPreview"),parallelVariants=bool(b,"parallelVariants"),maxReferenceImages=int(b,"maxReferenceImages"),
                    qualityLevels=set(b,"qualityLevels"),outputFormats=set(b,"outputFormats").ifEmpty{setOf("png")},promptRevision=bool(b,"promptRevision"),
                    deprecated=bool(b,"deprecated"),notes=str(b,"notes")
                )
            }.filter{it.provider.isNotBlank()&&it.id.isNotBlank()}.toList()
            return ModelRegistry(version,models)
        }
    }
}
