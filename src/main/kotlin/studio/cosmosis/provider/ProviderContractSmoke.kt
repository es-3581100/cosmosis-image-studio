package studio.cosmosis.provider

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.imageio.ImageIO

/**
 * Offline transport-level contract smoke for the real provider adapters.
 *
 * No external network is used. Each provider talks through HttpSupport to an
 * embedded loopback server which validates the expected authentication header
 * and returns deterministic image payloads.
 */
fun main() {
    val reportPath=Path.of(System.getenv("COSMOSIS_PROVIDER_CONTRACT_REPORT")?:"build/provider-contract/report.txt")
    reportPath.parent?.let{Files.createDirectories(it)}
    val temp=Files.createTempDirectory("cosmosis-provider-contract-")
    val reference=temp.resolve("reference.png")
    writeFixture(reference)
    val ref=ReferenceImage(reference,"image/png")
    val lines=mutableListOf<String>()

    LocalProviderContractServer().use { fixture ->
        val openAi=OpenAiProvider(
            apiKey={"contract-openai-secret"},
            baseUrl=fixture.baseUrl+"/v1"
        )
        val openAiModel=openAi.models().firstOrNull{it.capabilities.textToImage&&it.capabilities.imageToImage}
            ?: error("No OpenAI model supports the contract smoke")
        check(openAi.testConnection().ok){"OpenAI connection contract failed"}

        val openAiGenerate=openAi.generate(
            GenerationRequest(
                prompt="offline openai contract",
                model=openAiModel.id,
                quality="auto",
                outputFormat="png"
            )
        )
        requirePng(openAiGenerate,"openai-generate")
        check(openAiGenerate.images.single().revisedPrompt=="contract-revised")

        val openAiEdit=openAi.edit(
            GenerationRequest(
                prompt="offline openai edit contract",
                model=openAiModel.id,
                references=listOf(ref),
                quality="auto",
                outputFormat="png"
            )
        )
        requirePng(openAiEdit,"openai-edit")

        val openAiResponses=openAi.generate(
            GenerationRequest(
                prompt="offline openai responses contract",
                model=openAiModel.id,
                references=listOf(ref),
                outputFormat="png",
                previousResponseId="resp_previous",
                metadata=mapOf(
                    "openAiWorkflow" to "responses",
                    "reasoningModel" to "contract-reasoning-model"
                )
            )
        )
        requirePng(openAiResponses,"openai-responses")
        check(openAiResponses.rawMetadata["responseId"]=="resp_contract")
        check(openAiResponses.images.single().providerAssetId=="resp_contract")

        val gemini=GeminiProvider(
            apiKey={"contract-gemini-secret"},
            baseUrl=fixture.baseUrl+"/v1beta"
        )
        val geminiModel=gemini.models().firstOrNull{it.capabilities.textToImage&&it.capabilities.imageToImage}
            ?: error("No Gemini model supports the contract smoke")
        check(gemini.testConnection().ok){"Gemini connection contract failed"}

        val geminiGenerate=gemini.generate(
            GenerationRequest(
                prompt="offline gemini contract",
                model=geminiModel.id,
                aspectRatio="16:9",
                outputFormat="png",
                metadata=mapOf(
                    "imageSize" to "1K",
                    "searchGrounding" to "true",
                    "thinkingLevel" to "high"
                )
            )
        )
        requirePng(geminiGenerate,"gemini-generate")
        check(geminiGenerate.rawMetadata["interactionId"]=="ix_contract")

        val geminiEdit=gemini.edit(
            GenerationRequest(
                prompt="offline gemini edit contract",
                model=geminiModel.id,
                references=listOf(ref),
                outputFormat="png",
                previousResponseId="ix_previous"
            )
        )
        requirePng(geminiEdit,"gemini-edit")

        val liteLlm=LiteLlmProvider(
            apiKey={"contract-litellm-secret"},
            baseUrl=fixture.baseUrl+"/lite/v1"
        )
        val liteModel=liteLlm.models().firstOrNull{it.capabilities.textToImage}
            ?: error("No LiteLLM model supports the contract smoke")
        check(liteLlm.testConnection().ok){"LiteLLM connection contract failed"}
        val liteGenerate=liteLlm.generate(
            GenerationRequest(
                prompt="offline litellm contract",
                model=liteModel.id,
                quality="auto",
                outputFormat="png"
            )
        )
        requirePng(liteGenerate,"litellm-generate")
        check(liteGenerate.provider=="litellm")

        var negativeCases=0
        var unsupportedHttpRequests=0
        fun expectLocalCapabilityRejection(label:String,block:()->Unit) {
            val before=fixture.requests.size
            val failure=runCatching(block).exceptionOrNull()
            check(failure is CapabilityException){"$label expected CapabilityException, got ${failure?.javaClass?.simpleName}: ${failure?.message}"}
            val delta=fixture.requests.size-before
            unsupportedHttpRequests+=delta
            check(delta==0){"$label emitted $delta provider HTTP request(s) before rejection"}
            negativeCases++
        }

        val geminiNoSearch=GeminiProvider(
            apiKey={"contract-gemini-secret"},
            baseUrl=fixture.baseUrl+"/v1beta",
            modelDefinitions=listOf(
                geminiModel.copy(
                    id="gemini-no-search",
                    capabilities=geminiModel.capabilities.copy(searchGrounding=false)
                )
            )
        )
        expectLocalCapabilityRejection("gemini.search.unsupported") {
            geminiNoSearch.generate(GenerationRequest(prompt="reject search",model="gemini-no-search",metadata=mapOf("searchGrounding" to "true")))
        }

        val geminiNoThinking=GeminiProvider(
            apiKey={"contract-gemini-secret"},
            baseUrl=fixture.baseUrl+"/v1beta",
            modelDefinitions=listOf(
                geminiModel.copy(
                    id="gemini-no-thinking",
                    capabilities=geminiModel.capabilities.copy(thinkingConfiguration=false)
                )
            )
        )
        expectLocalCapabilityRejection("gemini.thinking.unsupported") {
            geminiNoThinking.generate(GenerationRequest(prompt="reject thinking",model="gemini-no-thinking",metadata=mapOf("thinkingLevel" to "high")))
        }

        expectLocalCapabilityRejection("openai.gemini-search-option") {
            openAi.generate(GenerationRequest(prompt="reject cross-provider search",model=openAiModel.id,metadata=mapOf("searchGrounding" to "true")))
        }
        expectLocalCapabilityRejection("litellm.gemini-search-option") {
            liteLlm.generate(GenerationRequest(prompt="reject gateway search",model=liteModel.id,metadata=mapOf("searchGrounding" to "true")))
        }

        val genericOpenAi=OpenAiProvider(
            apiKey={"contract-openai-secret"},
            baseUrl=fixture.baseUrl+"/v1",
            modelDefinitions=listOf(
                openAiModel.copy(
                    id="openai-generic-only",
                    capabilities=openAiModel.capabilities.copy(
                        responsesImageGeneration=false,
                        multiTurnEditing=false,
                        continuationProtocol=ContinuationProtocol.NONE
                    )
                )
            )
        )
        expectLocalCapabilityRejection("openai.responses.generic-route") {
            genericOpenAi.generate(
                GenerationRequest(
                    prompt="reject responses",
                    model="openai-generic-only",
                    metadata=mapOf("openAiWorkflow" to "responses","reasoningModel" to "contract-reasoning-model")
                )
            )
        }
        expectLocalCapabilityRejection("litellm.responses") {
            liteLlm.generate(
                GenerationRequest(
                    prompt="reject litellm responses",
                    model=liteModel.id,
                    metadata=mapOf("openAiWorkflow" to "responses","reasoningModel" to "contract-reasoning-model")
                )
            )
        }
        expectLocalCapabilityRejection("responses.reasoning-model-smuggle") {
            openAi.generate(
                GenerationRequest(
                    prompt="reject orphan reasoning model",
                    model=openAiModel.id,
                    metadata=mapOf("reasoningModel" to "contract-reasoning-model")
                )
            )
        }
        expectLocalCapabilityRejection("responses.direct-continuation") {
            openAi.generate(
                GenerationRequest(
                    prompt="reject direct continuation",
                    model=openAiModel.id,
                    previousResponseId="resp_should_not_cross"
                )
            )
        }
        expectLocalCapabilityRejection("litellm.compression-semantic") {
            liteLlm.generate(
                GenerationRequest(
                    prompt="reject compression",
                    model=liteModel.id,
                    outputFormat="webp",
                    metadata=mapOf("compression" to "70")
                )
            )
        }
        expectLocalCapabilityRejection("cross-provider-gemini-store") {
            openAi.generate(
                GenerationRequest(
                    prompt="reject store",
                    model=openAiModel.id,
                    metadata=mapOf("store" to "true")
                )
            )
        }
        expectLocalCapabilityRejection("undeclared-model-alias") {
            openAi.generate(GenerationRequest(prompt="reject alias",model=openAiModel.id+"-alias"))
        }

        val requests=fixture.requests.toList()
        check(requests.size==9){"Expected 9 supported provider HTTP calls, got ${requests.size}"}
        check(unsupportedHttpRequests==0){"Unsupported cases emitted $unsupportedHttpRequests provider HTTP request(s)"}
        check(requests.all{it.authOk}){"A provider request reached the fixture without the required auth header"}

        val direct=requests.single{it.path=="/v1/images/generations"}
        check(direct.method=="POST")
        check(direct.body.contains("\"prompt\":\"offline openai contract\""))
        check(direct.body.contains("\"output_format\":\"png\""))

        val edit=requests.single{it.path=="/v1/images/edits"}
        check(edit.method=="POST")
        check(edit.contentType.startsWith("multipart/form-data; boundary="))
        check(edit.body.contains("name=\"image[]\""))
        check(edit.body.contains("name=\"prompt\""))
        check(edit.body.contains("offline openai edit contract"))

        val responses=requests.single{it.path=="/v1/responses"}
        check(responses.body.contains("\"type\":\"image_generation\""))
        check(responses.body.contains("\"type\":\"input_image\""))
        check(responses.body.contains("\"previous_response_id\":\"resp_previous\""))
        check(responses.body.contains("\"model\":\"contract-reasoning-model\""))

        val geminiCalls=requests.filter{it.path=="/v1beta/interactions"}
        check(geminiCalls.size==2)
        val geminiGenerateRequest=geminiCalls.first{it.body.contains("offline gemini contract")}
        check(geminiGenerateRequest.body.contains("\"google_search\""))
        check(geminiGenerateRequest.body.contains("\"thinking_level\":\"high\""))
        check(geminiGenerateRequest.body.contains("\"image_size\":\"1K\""))
        check(geminiGenerateRequest.body.contains("\"aspect_ratio\":\"16:9\""))
        val geminiEditRequest=geminiCalls.first{it.body.contains("offline gemini edit contract")}
        check(geminiEditRequest.body.contains("\"previous_interaction_id\":\"ix_previous\""))
        check(geminiEditRequest.body.contains("\"type\":\"image\""))

        val lite=requests.single{it.path=="/lite/v1/images/generations"}
        check(lite.method=="POST")
        check(lite.body.contains("offline litellm contract"))

        lines += "PROVIDER_CONTRACT_SMOKE_PASS"
        lines += "PROVIDER_CAPABILITY_CONTRACT_PASS"
        lines += "network=loopback-only"
        lines += "requests=${requests.size}"
        lines += "positive-cases=7"
        lines += "negative-cases=$negativeCases"
        lines += "unsupported-http-requests=$unsupportedHttpRequests"
        lines += "gemini.search.supported=pass"
        lines += "gemini.search.unsupported=pass"
        lines += "gemini.thinking.supported=pass"
        lines += "gemini.thinking.unsupported=pass"
        lines += "openai.responses.supported=pass"
        lines += "openai.responses.generic-route-rejected=pass"
        lines += "openai.responses.litellm-route-rejected=pass"
        lines += "cross-provider-option-smuggling=pass"
        lines += "undeclared-model-alias-rejected=pass"
        lines += "provider-compatible-not-semantic-compatible=pass"
        lines += "openai.connection=pass"
        lines += "openai.direct-generation=pass"
        lines += "openai.multipart-edit=pass"
        lines += "openai.responses-image=pass"
        lines += "gemini.connection=pass"
        lines += "gemini.generate=pass"
        lines += "gemini.edit-multiturn=pass"
        lines += "litellm.connection=pass"
        lines += "litellm.openai-compatible-generation=pass"
        lines += "auth.headers=pass"
        lines += "decoded-images=pass"
    }

    check(lines.none{it.contains("contract-openai-secret")||it.contains("contract-gemini-secret")||it.contains("contract-litellm-secret")})
    val report=lines.joinToString("\n",postfix="\n")
    Files.writeString(reportPath,report)
    print(report)
}

private data class CapturedProviderRequest(
    val method:String,
    val path:String,
    val contentType:String,
    val body:String,
    val authOk:Boolean
)

private class LocalProviderContractServer:AutoCloseable {
    val requests=CopyOnWriteArrayList<CapturedProviderRequest>()
    private val executor=Executors.newCachedThreadPool{r->Thread(r,"cosmosis-provider-contract").apply{isDaemon=true}}
    private val server=HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(),0),0)
    private val imageBase64=Base64.getEncoder().encodeToString(fixturePng())
    val baseUrl:String get()="http://127.0.0.1:${server.address.port}"

    init {
        server.executor=executor

        route("/v1/models","Authorization","Bearer contract-openai-secret") {
            """{"data":[]}"""
        }
        route("/v1/images/generations","Authorization","Bearer contract-openai-secret") {
            """{"data":[{"b64_json":"$imageBase64","revised_prompt":"contract-revised"}]}"""
        }
        route("/v1/images/edits","Authorization","Bearer contract-openai-secret") {
            """{"data":[{"b64_json":"$imageBase64","revised_prompt":"contract-edited"}]}"""
        }
        route("/v1/responses","Authorization","Bearer contract-openai-secret") {
            """{"id":"resp_contract","output":[{"type":"image_generation_call","result":"$imageBase64"}]}"""
        }

        route("/v1beta/models","x-goog-api-key","contract-gemini-secret") {
            """{"name":"contract-gemini-model"}"""
        }
        route("/v1beta/interactions","x-goog-api-key","contract-gemini-secret") {
            """{"id":"ix_contract","output":[{"type":"image","mime_type":"image/png","data":"$imageBase64"}]}"""
        }

        route("/lite/v1/models","Authorization","Bearer contract-litellm-secret") {
            """{"data":[]}"""
        }
        route("/lite/v1/images/generations","Authorization","Bearer contract-litellm-secret") {
            """{"data":[{"b64_json":"$imageBase64","revised_prompt":"contract-litellm"}]}"""
        }

        server.start()
    }

    private fun route(path:String,authHeader:String,authValue:String,response:(CapturedProviderRequest)->String) {
        server.createContext(path){exchange->
            val bytes=exchange.requestBody.readAllBytes()
            val authOk=exchange.requestHeaders.getFirst(authHeader)==authValue
            val captured=CapturedProviderRequest(
                method=exchange.requestMethod,
                path=exchange.requestURI.path,
                contentType=exchange.requestHeaders.getFirst("Content-Type").orEmpty(),
                body=String(bytes,StandardCharsets.UTF_8),
                authOk=authOk
            )
            requests += captured
            if(authOk) send(exchange,200,response(captured))
            else send(exchange,401,"""{"error":"missing expected authentication"}""")
        }
    }

    private fun send(exchange:HttpExchange,status:Int,body:String) {
        val bytes=body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type","application/json")
        exchange.sendResponseHeaders(status,bytes.size.toLong())
        exchange.responseBody.use{it.write(bytes)}
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}

private fun writeFixture(path:Path) {
    val image=BufferedImage(24,16,BufferedImage.TYPE_INT_RGB)
    for(y in 0 until image.height)for(x in 0 until image.width) {
        image.setRGB(x,y,if(x in 6..17&&y in 3..13)Color(224,199,142).rgb else Color(17,17,14).rgb)
    }
    check(ImageIO.write(image,"png",path.toFile()))
}

private fun fixturePng():ByteArray {
    val image=BufferedImage(12,8,BufferedImage.TYPE_INT_RGB)
    for(y in 0 until image.height)for(x in 0 until image.width) {
        image.setRGB(x,y,if((x+y)%2==0)Color(32,32,27).rgb else Color(240,224,176).rgb)
    }
    return ByteArrayOutputStream().use{out->
        check(ImageIO.write(image,"png",out))
        out.toByteArray()
    }
}

private fun requirePng(result:GenerationResult,label:String) {
    check(result.images.size==1){"$label expected exactly one image"}
    val image=result.images.single()
    check(image.mime=="image/png"){"$label returned unexpected MIME ${image.mime}"}
    check(ImageIO.read(ByteArrayInputStream(image.bytes))!=null){"$label returned undecodable image bytes"}
}
