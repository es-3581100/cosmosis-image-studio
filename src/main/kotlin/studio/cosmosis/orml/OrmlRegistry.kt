package studio.cosmosis.orml

data class OrmlCapability(
    val id:String,val module:String,val displayName:String,val intents:Set<String>,val inputs:Set<String>,val outputs:Set<String>,
    val performance:String,val cpuGpu:String,val editorIntegration:String,val workerIntegration:String,val classCandidates:List<String>,val notes:String
) {
    fun runtimeAvailable():Boolean = classCandidates.any { name -> runCatching { Class.forName(name); true }.getOrDefault(false) }
}

object OrmlRegistry {
    val capabilities=listOf(
        OrmlCapability("smart-subject-mask","orml-u2net","U2Net Smart Subject Mask",setOf("remove-background","isolate-subject","create-edit-mask"),setOf("image"),setOf("mask"),"medium/high local inference","CPU supported; GPU backend requires compatible TensorFlow/CUDA stack","mask tool, background replace, outpaint prep","MASK.WORKER",listOf("org.openrndr.orml.u2net.U2Net"),"Optional runtime module; derived masks remain separate from source assets."),
        OrmlCapability("person-body-mask","orml-bodypix","BodyPix Person / Body Region Mask",setOf("select-person","select-body-region","clothing-edit"),setOf("image"),setOf("person-mask","body-part-mask"),"medium local inference","CPU/GPU TensorFlow backend","person selection and targeted edits","MASK.WORKER",listOf("org.openrndr.orml.bodypix.BodyPix"),"Body-part segmentation should be treated as derived metadata."),
        OrmlCapability("image-embedding","orml-image-classifier","Image Classifier / Embedding",setOf("tag-image","find-similar","recommend-prompts","group-style"),setOf("image"),setOf("labels","embedding"),"low/medium local inference","CPU friendly; GPU optional","project search and prompt recommendations","VISUAL.ANALYZER",listOf("org.openrndr.orml.imageclassifier.ImageClassifier"),"Use labels as generated visual metadata; never overwrite user tags."),
        OrmlCapability("super-resolution","orml-super-resolution","Super Resolution",setOf("upscale","export-preparation"),setOf("image"),setOf("image"),"high local inference","GPU recommended for large images","non-destructive upscale branch","GENERATION / EXPORT",listOf("ImageUpscaler"),"Output must become a child version, never overwrite input.")
    )
    fun byIntent(intent:String)=capabilities.filter{intent in it.intents}
}
