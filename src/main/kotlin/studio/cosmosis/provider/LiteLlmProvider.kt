package studio.cosmosis.provider

class LiteLlmProvider(apiKey:()->String?={System.getenv("LITELLM_API_KEY")},baseUrl:String=System.getenv("LITELLM_BASE_URL")?:"http://127.0.0.1:4000/v1") : OpenAiProvider(apiKey,baseUrl.trimEnd('/'),"litellm")
class OpenAiCompatibleProvider(providerId:String,apiKey:()->String?,baseUrl:String) : OpenAiProvider(apiKey,baseUrl.trimEnd('/'),providerId)
