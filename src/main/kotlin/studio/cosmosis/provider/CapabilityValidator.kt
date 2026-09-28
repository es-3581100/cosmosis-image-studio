package studio.cosmosis.provider

object CapabilityValidator {
    fun validate(request:GenerationRequest, caps:ProviderCapabilities, editing:Boolean=false) {
        if(editing && !caps.imageToImage) throw CapabilityException("This model does not support image-to-image editing.")
        if(request.mask!=null && !caps.maskEditing) throw CapabilityException("This model does not support mask editing. Remove the mask or choose a mask-capable model.")
        if(request.references.isNotEmpty() && !caps.imageToImage && !caps.multipleReferences) throw CapabilityException("This model does not accept reference images.")
        if(request.references.size>caps.maxReferenceImages && caps.maxReferenceImages>=0) throw CapabilityException("This model supports at most ${caps.maxReferenceImages} reference images. You currently have ${request.references.size} attached.")
        if(request.transparent && !caps.transparentBackground) throw CapabilityException("This model does not support transparent output.")
        if(request.transparent && request.outputFormat.lowercase()=="jpeg") throw CapabilityException("JPEG cannot preserve transparent output; choose PNG or WebP.")
        if(request.outputFormat.lowercase() !in caps.outputFormats) throw CapabilityException("Output format ${request.outputFormat} is not supported; choose ${caps.outputFormats.joinToString()}.")
        request.quality?.let { if(caps.qualityLevels.isNotEmpty() && it !in caps.qualityLevels) throw CapabilityException("Quality '$it' is unsupported; choose ${caps.qualityLevels.joinToString()}.") }
        request.aspectRatio?.let { if(caps.aspectRatios.isNotEmpty() && it !in caps.aspectRatios) throw CapabilityException("Aspect ratio '$it' is unsupported by this model.") }
        request.metadata["imageSize"]?.takeIf{it.isNotBlank()}?.let { if(caps.imageSizes.isNotEmpty() && it !in caps.imageSizes) throw CapabilityException("Image size '$it' is unsupported; choose ${caps.imageSizes.joinToString()}.") }
        if(request.variants<1 || request.variants>10) throw CapabilityException("Variant count must be between 1 and 10.")
        if(request.variants>1 && !caps.parallelVariants) throw CapabilityException("This model/route supports one image per request; use Agent Build or separate jobs for multiple concepts.")
        if((request.width==null)!=(request.height==null)) throw CapabilityException("Custom dimensions require both width and height.")
        if((request.width!=null || request.height!=null) && !caps.customDimensions) throw CapabilityException("Custom width/height is not supported by this model.")
        request.width?.let{if(it<=0)throw CapabilityException("Width must be positive.")}
        request.height?.let{if(it<=0)throw CapabilityException("Height must be positive.")}
        request.references.forEach { ref ->
            require(ref.mime in setOf("image/png","image/jpeg","image/webp")) { "Unsupported reference MIME ${ref.mime}" }
            require(ref.path.toFile().isFile) { "Reference image not found: ${ref.path}" }
        }
        request.mask?.let { require(it.path.toFile().isFile) { "Mask file not found: ${it.path}" } }
    }
}
