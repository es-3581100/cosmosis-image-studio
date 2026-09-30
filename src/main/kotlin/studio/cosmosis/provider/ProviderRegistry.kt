package studio.cosmosis.provider

class ProviderRegistry(private val providers:MutableMap<String,ImageProvider> = linkedMapOf()) {
    fun register(provider:ImageProvider)=apply{providers[provider.id]=provider}
    fun get(id:String):ImageProvider=requireNotNull(providers[id]){"Unknown provider '$id'"}
    fun all():List<ImageProvider> = providers.values.toList()
    fun models():List<ModelDefinition> = providers.values.flatMap{it.models()}
    fun model(providerId:String,model:String):ModelDefinition=get(providerId).modelDefinition(model)
    fun capabilities(providerId:String,model:String):ProviderCapabilities=model(providerId,model).capabilities
    fun validate(providerId:String,request:GenerationRequest,editing:Boolean=false):ModelDefinition {
        val definition=model(providerId,request.model)
        CapabilityValidator.validate(request,definition,editing)
        return definition
    }
    fun route(providerId:String,request:GenerationRequest,editing:Boolean=false):GenerationResult {
        val p=get(providerId)
        validate(providerId,request,editing)
        return if(editing)p.edit(request) else p.generate(request)
    }
    companion object { fun default()=ProviderRegistry().register(LocalPreviewProvider()).register(OpenAiProvider()).register(GeminiProvider()).register(LiteLlmProvider()) }
}
