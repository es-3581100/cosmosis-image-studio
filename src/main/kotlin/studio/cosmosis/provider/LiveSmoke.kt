package studio.cosmosis.provider

fun main(){
    check(System.getenv("COSMOSIS_LIVE_PROVIDER_TESTS")=="1") { "Set COSMOSIS_LIVE_PROVIDER_TESTS=1 explicitly" }
    val providers=ProviderRegistry.default().all()
    providers.forEach { p ->
        if(when(p.id){"openai"->System.getenv("OPENAI_API_KEY");"gemini"->System.getenv("GEMINI_API_KEY")?:System.getenv("GOOGLE_API_KEY");"litellm"->System.getenv("LITELLM_API_KEY");else->null}.isNullOrBlank()) println("${p.id}: SKIP (no credential)")
        else println("${p.id}: ${p.testConnection()}")
    }
}
