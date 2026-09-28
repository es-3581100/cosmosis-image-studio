package studio.cosmosis.provider

class ProviderRegistry(private val providers:MutableMap<String,ImageProvider> = linkedMapOf()) {
    fun register(provider:ImageProvider)=apply{providers[provider.id]=provider}
    fun get(id:String):ImageProvider=requireNotNull(providers[id]){"Unknown provider '$id'"}
    fun all():List<ImageProvider> = providers.values.toList()
    fun models():List<ModelDefinition> = providers.values.flatMap{it.models()}
    fun route(providerId:String,request:GenerationRequest,editing:Boolean=false):GenerationResult { val p=get(providerId); return if(editing)p.edit(request) else p.generate(request) }
    companion object { fun default()=ProviderRegistry().register(LocalPreviewProvider()).register(OpenAiProvider()).register(GeminiProvider()).register(LiteLlmProvider()) }
}
