package studio.cosmosis.provider

private fun genericOpenAiCompatibleDefinition(providerId:String,modelId:String)=ModelDefinition(
    provider=providerId,
    id=modelId,
    label=modelId,
    capabilities=ProviderCapabilities(
        textToImage=true,
        imageToImage=true,
        maskEditing=true,
        multipleReferences=true,
        multiTurnEditing=false,
        transparentBackground=true,
        customDimensions=true,
        responsesImageGeneration=false,
        outputCompression=false,
        interactionStorage=false,
        continuationProtocol=ContinuationProtocol.NONE,
        parallelVariants=true,
        maxReferenceImages=16,
        qualityLevels=setOf("auto","low","medium","high"),
        outputFormats=setOf("png","jpeg","webp"),
        promptRevision=true
    ),
    notes="User-declared OpenAI-compatible image route. Wire compatibility is assumed; provider-specific semantics remain disabled."
)

class LiteLlmProvider(
    apiKey:()->String?={System.getenv("LITELLM_API_KEY")},
    baseUrl:String=System.getenv("LITELLM_BASE_URL")?:"http://127.0.0.1:4000/v1"
) : OpenAiProvider(apiKey,baseUrl.trimEnd('/'),"litellm")

class OpenAiCompatibleProvider(
    providerId:String,
    apiKey:()->String?,
    baseUrl:String,
    modelId:String="route-configured"
) : OpenAiProvider(
    apiKey=apiKey,
    baseUrl=baseUrl.trimEnd('/'),
    id=providerId,
    modelDefinitions=listOf(genericOpenAiCompatibleDefinition(providerId,modelId.trim()))
)
