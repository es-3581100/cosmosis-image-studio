package studio.cosmosis.provider

data class RequestRequirements(
    val searchGrounding:Boolean=false,
    val thinkingConfiguration:Boolean=false,
    val responsesImageGeneration:Boolean=false,
    val outputCompression:Boolean=false,
    val interactionStorage:Boolean=false,
    val imageSize:Boolean=false,
    val sizePreset:Boolean=false,
    val continuation:Boolean=false
)

object CapabilityValidator {
    private val trueValues=setOf("1","true","yes","on")
    private val falseValues=setOf("0","false","no","off")

    fun requirements(request:GenerationRequest)=RequestRequirements(
        searchGrounding=request.metadata.containsKey("searchGrounding"),
        thinkingConfiguration=request.metadata.containsKey("thinkingLevel"),
        responsesImageGeneration=request.metadata.containsKey("openAiWorkflow") || request.metadata.containsKey("reasoningModel"),
        outputCompression=request.metadata.containsKey("compression"),
        interactionStorage=request.metadata.containsKey("store"),
        imageSize=request.metadata.containsKey("imageSize"),
        sizePreset=request.metadata.containsKey("size"),
        continuation=request.previousResponseId!=null
    )

    fun validate(request:GenerationRequest,caps:ProviderCapabilities,editing:Boolean=false) =
        validate(request,ModelDefinition("unscoped",request.model,request.model,caps),editing)

    fun validate(request:GenerationRequest,model:ModelDefinition,editing:Boolean=false) {
        val caps=model.capabilities
        val req=requirements(request)

        if(!editing && !caps.textToImage) mismatch(model,"textToImage","generation",caps)
        if(editing && !caps.imageToImage) mismatch(model,"imageToImage","editing",caps)
        if(request.mask!=null && !caps.maskEditing) mismatch(model,"maskEditing","mask",caps)
        if(request.references.isNotEmpty() && !caps.imageToImage && !caps.multipleReferences) mismatch(model,"imageToImage/multipleReferences","references",caps)
        if(request.references.size>caps.maxReferenceImages && caps.maxReferenceImages>=0) throw CapabilityException("This model supports at most ${caps.maxReferenceImages} reference images. You currently have ${request.references.size} attached.")
        if(request.transparent && !caps.transparentBackground) mismatch(model,"transparentBackground","transparent",caps)
        if(request.transparent && request.outputFormat.lowercase()=="jpeg") throw CapabilityException("JPEG cannot preserve transparent output; choose PNG or WebP.")
        if(request.outputFormat.lowercase() !in caps.outputFormats) throw CapabilityException("Output format ${request.outputFormat} is not supported; choose ${caps.outputFormats.joinToString()}.")

        request.quality?.let {
            if(caps.qualityLevels.isEmpty()) mismatch(model,"qualityLevels","quality",caps)
            if(it !in caps.qualityLevels) throw CapabilityException("Quality '$it' is unsupported; choose ${caps.qualityLevels.joinToString()}.")
        }
        request.aspectRatio?.let {
            if(caps.aspectRatios.isEmpty()) mismatch(model,"aspectRatios","aspectRatio",caps)
            if(it !in caps.aspectRatios) throw CapabilityException("Aspect ratio '$it' is unsupported by this model.")
        }

        if(req.imageSize) {
            val value=request.metadata["imageSize"].orEmpty()
            require(value.isNotBlank()){"Image size metadata must not be blank"}
            if(caps.imageSizes.isEmpty()) mismatch(model,"imageSizes","metadata.imageSize",caps)
            if(value !in caps.imageSizes) throw CapabilityException("Image size '$value' is unsupported; choose ${caps.imageSizes.joinToString()}.")
        }
        if(req.searchGrounding) {
            parseBooleanOption(request.metadata.getValue("searchGrounding"),"metadata.searchGrounding")
            if(!caps.searchGrounding) mismatch(model,"searchGrounding","metadata.searchGrounding",caps)
        }
        if(req.thinkingConfiguration) {
            require(request.metadata.getValue("thinkingLevel").isNotBlank()){"metadata.thinkingLevel must not be blank"}
            if(!caps.thinkingConfiguration) mismatch(model,"thinkingConfiguration","metadata.thinkingLevel",caps)
        }
        if(req.interactionStorage) {
            parseBooleanOption(request.metadata.getValue("store"),"metadata.store")
            if(!caps.interactionStorage) mismatch(model,"interactionStorage","metadata.store",caps)
        }
        if(req.responsesImageGeneration) {
            val workflow=request.metadata["openAiWorkflow"]
            if(workflow!=null && !workflow.equals("responses",true)) {
                throw CapabilityException("Unsupported openAiWorkflow '$workflow'; only 'responses' is a declared provider workflow")
            }
            if(request.metadata.containsKey("reasoningModel")) {
                require(!request.metadata["reasoningModel"].isNullOrBlank()){"metadata.reasoningModel must not be blank"}
                require(workflow?.equals("responses",true)==true){"metadata.reasoningModel requires metadata.openAiWorkflow=responses"}
            }
            if(!caps.responsesImageGeneration) mismatch(model,"responsesImageGeneration",if(workflow!=null)"metadata.openAiWorkflow" else "metadata.reasoningModel",caps)
        }
        if(req.outputCompression) {
            if(!caps.outputCompression) mismatch(model,"outputCompression","metadata.compression",caps)
            val raw=request.metadata.getValue("compression")
            val value=raw.toIntOrNull()?:throw CapabilityException("Output compression must be an integer from 0 to 100")
            require(value in 0..100){"Output compression must be between 0 and 100"}
            require(request.outputFormat.lowercase() in setOf("jpeg","webp")){"Output compression is only valid for JPEG or WebP"}
        }
        if(req.sizePreset) {
            if(!caps.customDimensions) mismatch(model,"customDimensions","metadata.size",caps)
            require(!request.metadata["size"].isNullOrBlank()){"metadata.size must not be blank"}
            require(request.width==null&&request.height==null){"metadata.size cannot be combined with explicit width/height"}
        }

        if(req.continuation) {
            require(!request.previousResponseId.isNullOrBlank()){"previousResponseId must not be blank"}
            if(!caps.multiTurnEditing) mismatch(model,"multiTurnEditing","previousResponseId",caps)
            if(caps.continuationProtocol==ContinuationProtocol.NONE) mismatch(model,"continuationProtocol","previousResponseId",caps)
            if(caps.continuationProtocol==ContinuationProtocol.OPENAI_RESPONSES &&
                request.metadata["openAiWorkflow"]?.equals("responses",true)!=true
            ) {
                throw CapabilityMismatchException(
                    model.provider,model.id,"OPENAI_RESPONSES continuation","previousResponseId",
                    "continuationProtocol=${caps.continuationProtocol}; metadata.openAiWorkflow must be responses"
                )
            }
        }

        if(request.variants<1 || request.variants>10) throw CapabilityException("Variant count must be between 1 and 10.")
        if(request.variants>1 && !caps.parallelVariants) mismatch(model,"parallelVariants","variants",caps)
        if((request.width==null)!=(request.height==null)) throw CapabilityException("Custom dimensions require both width and height.")
        if((request.width!=null || request.height!=null) && !caps.customDimensions) mismatch(model,"customDimensions","width/height",caps)
        request.width?.let{if(it<=0)throw CapabilityException("Width must be positive.")}
        request.height?.let{if(it<=0)throw CapabilityException("Height must be positive.")}
        request.references.forEach { ref ->
            require(ref.mime in setOf("image/png","image/jpeg","image/webp")) { "Unsupported reference MIME ${ref.mime}" }
            require(ref.path.toFile().isFile) { "Reference image not found: ${ref.path}" }
        }
        request.mask?.let { require(it.path.toFile().isFile) { "Mask file not found: ${it.path}" } }
    }

    private fun parseBooleanOption(value:String,option:String):Boolean {
        val normalized=value.trim().lowercase()
        if(normalized in trueValues)return true
        if(normalized in falseValues)return false
        throw CapabilityException("$option must be one of true/false, 1/0, yes/no, on/off")
    }

    private fun mismatch(model:ModelDefinition,capability:String,option:String,caps:ProviderCapabilities):Nothing =
        throw CapabilityMismatchException(model.provider,model.id,capability,option,declared(caps))

    private fun declared(caps:ProviderCapabilities)=buildString {
        append("searchGrounding=").append(caps.searchGrounding)
        append(",thinkingConfiguration=").append(caps.thinkingConfiguration)
        append(",responsesImageGeneration=").append(caps.responsesImageGeneration)
        append(",outputCompression=").append(caps.outputCompression)
        append(",interactionStorage=").append(caps.interactionStorage)
        append(",continuationProtocol=").append(caps.continuationProtocol)
        append(",multiTurnEditing=").append(caps.multiTurnEditing)
    }
}
