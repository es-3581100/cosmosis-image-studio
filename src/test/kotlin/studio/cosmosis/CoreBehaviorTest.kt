package studio.cosmosis

import kotlin.test.*
import studio.cosmosis.docs.AgentIndex
import studio.cosmosis.lineage.VersionGraph
import studio.cosmosis.lineage.VersionGraphLayout
import studio.cosmosis.mask.*
import studio.cosmosis.orml.*
import studio.cosmosis.prompt.*
import studio.cosmosis.provider.*
import studio.cosmosis.security.Redaction
import studio.cosmosis.storage.SqliteStore
import studio.cosmosis.workers.JobEngine
import studio.cosmosis.ui.preferredModelId
import java.nio.file.Files
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

class CoreBehaviorTest {
    @Test fun promptCopyKeepsProvenanceAndCreatesRevision(){
        val upstream=PromptAsset(id="u",title="Up",body="body",origin=PromptOrigin.UPSTREAM,readOnly=true,source="fixture",sourceLicense="MIT",treePath="PREMADE PROMPTS/X")
        val lib=PromptLibrary(listOf(upstream));val local=lib.copyUpstream("u")
        assertNotEquals(upstream.id,local.id);assertEquals("u",local.derivedFrom);assertFalse(local.readOnly)
        lib.update(local.id){it.body="changed"};assertEquals("body",lib.revisions(local.id).single().snapshot.body)
    }
    @Test fun promptImportExportRoundTrips(){
        val p=PromptAsset(title="A",body="line1\nline2",explicitKeywords=setOf("x"));val q=PromptExchange.fromJson(PromptExchange.toJson(p));assertEquals(p.body,q.body);assertTrue("x" in q.explicitKeywords)
    }
    @Test fun lineageBranchesWithoutFlattening(){
        val g=VersionGraph();g.add(VersionNode("v0",null,"a0",VersionOperation.IMPORT,"root"));g.add(VersionNode("v1","v0","a1",VersionOperation.EDIT,"one"));g.add(VersionNode("v2","v0","a2",VersionOperation.REMIX,"two"))
        assertEquals(setOf("v1","v2"),g.children("v0").map{it.id}.toSet());g.validateAcyclic()
        val layout=VersionGraphLayout.layout(g.all());assertTrue(layout.any{it.id=="v1"&&it.depth==1});assertTrue(layout.any{it.id=="v2"&&it.depth==1})
    }
    @Test fun maskUndoRedoWorks(){val m=MaskDocument(32,32);m.apply(MaskStroke(16,16,16,16,5,false));assertTrue(m.coverage()>0);assertTrue(m.undo());assertEquals(0.0,m.coverage());assertTrue(m.redo())}
    @Test fun validatorRejectsUnsupportedMask(){val f=Files.createTempFile("mask",".png");val req=GenerationRequest(prompt="x",model="m",mask=ReferenceImage(f));assertFailsWith<CapabilityException>{CapabilityValidator.validate(req,ProviderCapabilities(textToImage=true),false)}}
    @Test fun wireFormatsAreProviderSpecific(){
        val openAiRequest=GenerationRequest(prompt="x",model="gpt-image-2.5-flare",quality="high",width=1024,height=1024,outputFormat="webp",metadata=mapOf("compression" to "70"))
        val o=OpenAiWire.generationBody(openAiRequest);assertContains(o,"output_format");assertContains(o,"1024x1024");assertContains(o,"70")
        val rt=OpenAiWire.responsesImageTool(openAiRequest);assertContains(rt,"1024x1024");assertContains(rt,"output_compression")
        val g=GeminiWire.interactionsBody(GenerationRequest(prompt="x",model="gemini-3.1-flash-image",aspectRatio="16:9",previousResponseId="ix_1",metadata=mapOf("searchGrounding" to "true","thinkingLevel" to "high")))
        assertContains(g,"response_format");assertContains(g,"previous_interaction_id");assertContains(g,"google_search");assertContains(g,"thinking_level")
    }
    @Test fun validatorRejectsDroppedProviderSemantics(){
        val singleOnly=ProviderCapabilities(textToImage=true,parallelVariants=false,outputFormats=setOf("png"))
        assertFailsWith<CapabilityException>{CapabilityValidator.validate(GenerationRequest(prompt="x",model="m",variants=2),singleOnly)}
        val transparent=ProviderCapabilities(textToImage=true,transparentBackground=true,outputFormats=setOf("png","jpeg"))
        assertFailsWith<CapabilityException>{CapabilityValidator.validate(GenerationRequest(prompt="x",model="m",transparent=true,outputFormat="jpeg"),transparent)}
    }
    @Test fun openAiRejectsInvalidCurrentDimensionContractBeforeNetwork(){
        val provider=OpenAiProvider(apiKey={"test"})
        assertFailsWith<IllegalArgumentException>{provider.generate(GenerationRequest(prompt="x",model="gpt-image-2.5-flare",width=1000,height=1000))}
        assertFailsWith<IllegalArgumentException>{provider.generate(GenerationRequest(prompt="x",model="gpt-image-2.5-flare",variants=2,metadata=mapOf("openAiWorkflow" to "responses","reasoningModel" to "test-reasoning")))}
    }
    @Test fun providerSpecificRequirementsAreCapabilityBound(){
        val capable=ModelDefinition(
            provider="gemini",id="capable",label="capable",
            capabilities=ProviderCapabilities(
                textToImage=true,outputFormats=setOf("png"),searchGrounding=true,
                thinkingConfiguration=true,interactionStorage=true,
                multiTurnEditing=true,continuationProtocol=ContinuationProtocol.GEMINI_INTERACTIONS
            )
        )
        CapabilityValidator.validate(
            GenerationRequest(
                prompt="x",model="capable",previousResponseId="ix_1",
                metadata=mapOf("searchGrounding" to "true","thinkingLevel" to "high","store" to "false")
            ),
            capable
        )
        val incapable=capable.copy(id="incapable",capabilities=capable.capabilities.copy(searchGrounding=false,thinkingConfiguration=false))
        val search=assertFailsWith<CapabilityMismatchException>{
            CapabilityValidator.validate(GenerationRequest(prompt="x",model="incapable",metadata=mapOf("searchGrounding" to "true")),incapable)
        }
        assertEquals("searchGrounding",search.capability)
        val thinking=assertFailsWith<CapabilityMismatchException>{
            CapabilityValidator.validate(GenerationRequest(prompt="x",model="incapable",metadata=mapOf("thinkingLevel" to "high")),incapable)
        }
        assertEquals("thinkingConfiguration",thinking.capability)
    }

    @Test fun responsesAndContinuationRequireDeclaredSemanticRoute(){
        val responses=ModelDefinition(
            provider="openai",id="responses",label="responses",
            capabilities=ProviderCapabilities(
                textToImage=true,imageToImage=true,multiTurnEditing=true,outputFormats=setOf("png"),
                responsesImageGeneration=true,continuationProtocol=ContinuationProtocol.OPENAI_RESPONSES
            )
        )
        CapabilityValidator.validate(
            GenerationRequest(
                prompt="x",model="responses",previousResponseId="resp_1",
                metadata=mapOf("openAiWorkflow" to "responses","reasoningModel" to "reasoner")
            ),
            responses
        )
        val generic=responses.copy(id="generic",capabilities=responses.capabilities.copy(responsesImageGeneration=false,continuationProtocol=ContinuationProtocol.NONE,multiTurnEditing=false))
        assertFailsWith<CapabilityMismatchException>{
            CapabilityValidator.validate(
                GenerationRequest(prompt="x",model="generic",metadata=mapOf("openAiWorkflow" to "responses","reasoningModel" to "reasoner")),
                generic
            )
        }
        assertFailsWith<CapabilityMismatchException>{
            CapabilityValidator.validate(GenerationRequest(prompt="x",model="responses",previousResponseId="resp_1"),responses)
        }
    }

    @Test fun providerUiKeepsValidModelAndFallsBackFromInvalidModel(){
        val definitions=listOf(
            ModelDefinition("openai","image-a","Image A",ProviderCapabilities(textToImage=true)),
            ModelDefinition("openai","image-b","Image B",ProviderCapabilities(textToImage=true))
        )
        assertEquals("image-b",preferredModelId(definitions,"image-b"))
        assertEquals("image-a",preferredModelId(definitions,"local-preview-v1"))
        assertEquals("image-a",preferredModelId(definitions,null))
        assertNull(preferredModelId(emptyList(),null))
    }

    @Test fun activeCustomRouteRebindsWhenManualModelChanges(){
        val state=studio.cosmosis.ui.StudioState()
        val controller=studio.cosmosis.ui.StudioController(state,Files.createTempDirectory("cosmosis-docs"))
        try{
            controller.configureCustomProvider("https://openrouter.ai/api/v1","model/first")
            controller.activateCustomProvider()
            assertEquals("CUSTOM",state.get().provider)
            assertEquals("model/first",state.get().model)

            controller.configureCustomProvider("https://openrouter.ai/api/v1","model/second")
            assertEquals("model/second",state.get().model)
            assertEquals("model/second",controller.modelsFor("custom").single().id)
            assertEquals("openrouter-image",controller.customRouteKind())

            controller.selectCustomModel("model/third")
            assertEquals("model/third",state.get().model)
            assertEquals("model/third",controller.modelsFor("custom").single().id)
        }finally{controller.close()}
    }

    @Test fun openRouterAdapterUsesUnifiedImagesEndpointAndChecksImageCatalog(){
        val requested=AtomicReference<String>("")
        val server=HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(),0),0)
        val image=BufferedImage(8,8,BufferedImage.TYPE_INT_RGB)
        val png=ByteArrayOutputStream().use{out->ImageIO.write(image,"png",out);out.toByteArray()}
        val b64=Base64.getEncoder().encodeToString(png)
        fun send(exchange:com.sun.net.httpserver.HttpExchange,status:Int,body:String){
            requested.set(exchange.requestURI.path)
            val bytes=body.toByteArray()
            exchange.sendResponseHeaders(status,bytes.size.toLong())
            exchange.responseBody.use{it.write(bytes)}
        }
        server.createContext("/api/v1/images/models"){exchange->
            send(exchange,200,"""{"data":[{"id":"image/model"}]}""")
        }
        server.createContext("/api/v1/images"){exchange->
            val body=exchange.requestBody.readAllBytes().decodeToString()
            assertContains(body,"\"model\":\"image/model\"")
            send(exchange,200,"""{"data":[{"b64_json":"$b64","media_type":"image/png"}]}""")
        }
        server.start()
        try{
            val base="http://127.0.0.1:"+server.address.port+"/api/v1"
            val provider=OpenRouterImageProvider(apiKey={"test"},baseUrl=base,modelId="image/model")
            assertTrue(provider.testConnection().ok)
            assertEquals("/api/v1/images/models",requested.get())
            val result=provider.generate(GenerationRequest(prompt="x",model="image/model"))
            assertEquals("/api/v1/images",requested.get())
            assertEquals("image/png",result.images.single().mime)

            val wrong=OpenRouterImageProvider(apiKey={"test"},baseUrl=base,modelId="text/vision-only")
            val status=wrong.testConnection()
            assertFalse(status.ok)
            assertContains(status.message,"not in OpenRouter /images/models")
        }finally{server.stop(0)}
    }

    @Test fun customOpenAiCompatibleRouteUsesExplicitManualModelId(){
        val provider=OpenAiCompatibleProvider(
            providerId="custom",
            apiKey={"unused"},
            baseUrl="http://127.0.0.1:1/v1",
            modelId="free-provider/free-image-model"
        )
        assertEquals("free-provider/free-image-model",provider.models().single().id)
        assertTrue(provider.capabilities("free-provider/free-image-model").textToImage)
        assertFailsWith<CapabilityMismatchException>{provider.capabilities("route-configured")}
    }

    @Test fun customRouteRegistrationDoesNotHijackCurrentProvider(){
        val state=studio.cosmosis.ui.StudioState()
        val controller=studio.cosmosis.ui.StudioController(state,Files.createTempDirectory("cosmosis-docs"))
        try{
            val before=state.get()
            val secret="custom-secret".toCharArray()
            try{
                controller.configureCustomProvider(
                    baseUrl="http://127.0.0.1:1/v1",
                    modelId="free/model",
                    secret=secret,
                    keyEnv="CUSTOM_TEST_KEY"
                )
            }finally{secret.fill('\u0000')}
            assertEquals(before.provider,state.get().provider)
            assertEquals(before.model,state.get().model)
            assertEquals("free/model",controller.modelsFor("custom").single().id)
            assertEquals("session",controller.customCredentialSource())
            assertFalse(state.get().message.contains("custom-secret"))
        }finally{controller.close()}
    }

    @Test fun geminiSessionCredentialIsMemoryOnlyAndCurrentModelsAreRegistered(){
        val state=studio.cosmosis.ui.StudioState()
        val controller=studio.cosmosis.ui.StudioController(state,Files.createTempDirectory("cosmosis-docs"))
        try{
            val secret="gemini-test-secret".toCharArray()
            try{controller.configureGeminiSessionKey(secret)}finally{secret.fill('\u0000')}
            assertEquals("session",controller.geminiCredentialSource())
            val ids=controller.modelsFor("gemini").map{it.id}.toSet()
            assertTrue("gemini-nano-banana-2.1" in ids)
            assertTrue("gemini-3.1-flash-image" in ids)
            assertTrue("gemini-3.1-flash-lite-image" in ids)
            assertTrue("gemini-3-pro-image" in ids)
            assertFalse(state.get().message.contains("gemini-test-secret"))
        }finally{controller.close()}
    }

    @Test fun exactModelResolutionRejectsUndeclaredAliases(){
        val provider=GeminiProvider(apiKey={"unused"})
        assertFailsWith<CapabilityMismatchException>{provider.capabilities("gemini-3.1-flash-image-alias")}
    }

    @Test fun litellmWireCompatibilityDoesNotGrantResponsesSemantics(){
        val provider=LiteLlmProvider(apiKey={"unused"},baseUrl="http://127.0.0.1:1")
        val error=assertFailsWith<CapabilityMismatchException>{
            provider.generate(
                GenerationRequest(
                    prompt="x",model="route-configured",
                    metadata=mapOf("openAiWorkflow" to "responses","reasoningModel" to "reasoner")
                )
            )
        }
        assertEquals("responsesImageGeneration",error.capability)
    }

    @Test fun geminiImageParsingIgnoresUnrelatedData(){
        val blocks=JsonUtil.imageBlocks("""{"steps":[{"type":"tool_result","data":"bm90LWltYWdl"},{"type":"model_output","content":[{"type":"image","mime_type":"image/png","data":"aGVsbG8="}]}]}""")
        assertEquals(1,blocks.size);assertEquals("aGVsbG8=",blocks.single().data)
    }
    @Test fun secretsAreRedacted(){val r=Redaction.sanitize("api_key=supersecret " + "sk-proj-" + "abcdefghijklmnopqrstuvwxyz");assertFalse(r.contains("supersecret"));assertFalse(r.contains("sk-proj-"))}
    @Test fun ormlFailsClosedWithoutAdapter(){val result=OrmlExecutor().invoke(OrmlInvocation("smart-subject-mask","x"));assertFalse(result.ok)}
    @Test fun ormlAdapterRequiresConcreteOutput(){
        val input=Files.createTempFile("orml-input",".png")
        val output=Files.createTempFile("orml-output",".png")
        val capabilityId="smart-subject-mask"
        val adapter=object:OrmlAdapter{
            override val capabilityId=capabilityId
            override fun invoke(request:OrmlInvocation)=OrmlResult(capabilityId,true,listOf(output.toString()),message="ok")
        }
        val executor=OrmlExecutor(listOf(adapter))
        assertEquals("READY",executor.status(capabilityId))
        assertTrue(executor.invoke(OrmlInvocation(capabilityId,input.toString())).ok)
        assertTrue(executor.diagnostics().adapters.containsKey(capabilityId))
        val missing=output.resolveSibling("missing-output.png")
        val bad=object:OrmlAdapter{
            override val capabilityId="smart-subject-mask"
            override fun invoke(request:OrmlInvocation)=OrmlResult(capabilityId,true,listOf(missing.toString()),message="claimed")
        }
        val rejected=OrmlExecutor(listOf(bad)).invoke(OrmlInvocation(capabilityId,input.toString()))
        assertFalse(rejected.ok);assertContains(rejected.message,"missing output")
    }
    @Test fun desktopProcessAdapterInteroperatesWithExecutableRunner(){
        if(System.getProperty("os.name").lowercase().contains("win"))return
        val dir=Files.createTempDirectory("cosmosis orml runner ")
        val input=dir.resolve("input image.png")
        ImageIO.write(BufferedImage(20,14,BufferedImage.TYPE_INT_RGB),"png",input.toFile())
        val runner=dir.resolve("fake runner.sh")
        Files.writeString(runner,listOf(
            "#!/bin/sh",
            "out=\"\"",
            "in=\"\"",
            "while [ \"\$#\" -gt 0 ]; do",
            "  case \"\$1\" in",
            "    --input) shift; in=\"\$1\" ;;",
            "    --output) shift; out=\"\$1\" ;;",
            "    --capability) shift ;;",
            "    --option) shift ;;",
            "  esac",
            "  shift",
            "done",
            "cp \"\$in\" \"\$out\"",
            "echo \"api_key=supersecret runner-ok\""
        ).joinToString("\n")+"\n")
        val perms=Files.getPosixFilePermissions(runner).toMutableSet()
        perms+=java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
        Files.setPosixFilePermissions(runner,perms)
        val adapter=ProcessOrmlAdapter("smart-subject-mask",runner,5)
        val result=adapter.invoke(OrmlInvocation("smart-subject-mask",input.toString(),mapOf("outputDir" to dir.toString(),"outputFormat" to "png")))
        assertTrue(result.ok,result.message)
        assertTrue(result.outputPaths.single().let{Files.isRegularFile(java.nio.file.Path.of(it))})
        assertFalse(result.message.contains("supersecret"))
        assertContains(result.message,"[REDACTED]")
    }

    @Test fun processOrmlDiscoveryRequiresProtocolCapabilityHandshake(){
        if(System.getProperty("os.name").lowercase().contains("win"))return
        val dir=Files.createTempDirectory("cosmosis-probe")
        val runner=dir.resolve("runner.sh")
        Files.writeString(runner,listOf(
            "#!/bin/sh",
            "if [ \"\$1\" = \"--describe\" ]; then",
            "  echo '{\"protocolVersion\":\"1\",\"capabilities\":[{\"id\":\"smart-subject-mask\",\"available\":true,\"output\":\"image_mask\"}],\"errors\":[]}'",
            "  exit 0",
            "fi",
            "exit 9"
        ).joinToString("\n")+"\n")
        val perms=Files.getPosixFilePermissions(runner).toMutableSet()
        perms+=java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
        Files.setPosixFilePermissions(runner,perms)
        val discovered=ProcessOrmlAdapters.discover(mapOf("COSMOSIS_ORML_U2NET_RUNNER" to runner.toString()))
        assertEquals(1,discovered.adapters.size)
        assertTrue(discovered.errors.isEmpty())

        Files.writeString(runner,listOf(
            "#!/bin/sh",
            "echo '{\"protocolVersion\":\"1\",\"capabilities\":[{\"id\":\"smart-subject-mask\",\"available\":false,\"output\":\"image_mask\"}],\"errors\":[]}'"
        ).joinToString("\n")+"\n")
        val rejected=ProcessOrmlAdapters.discover(mapOf("COSMOSIS_ORML_U2NET_RUNNER" to runner.toString()))
        assertTrue(rejected.adapters.isEmpty())
        assertTrue(rejected.errors.any{it.contains("does not report")})
    }

    @Test fun processOrmlDiscoveryRejectsMissingExecutable(){
        val env=mapOf("COSMOSIS_ORML_U2NET_RUNNER" to "/definitely/not/a/cosmosis-runner")
        val discovery=ProcessOrmlAdapters.discover(env)
        assertTrue(discovery.adapters.isEmpty())
        assertTrue(discovery.errors.any{it.contains("COSMOSIS_ORML_U2NET_RUNNER")})
    }
    @Test fun ormlDuplicateAdaptersAreRejected(){
        val one=object:OrmlAdapter{override val capabilityId="smart-subject-mask";override fun invoke(request:OrmlInvocation)=OrmlResult(capabilityId,false,message="one")}
        val two=object:OrmlAdapter{override val capabilityId="smart-subject-mask";override fun invoke(request:OrmlInvocation)=OrmlResult(capabilityId,false,message="two")}
        val executor=OrmlExecutor(listOf(one,two))
        assertNotEquals("READY",executor.status("smart-subject-mask"))
        assertTrue(executor.diagnostics().errors.any{it.contains("ambiguous")})
    }
    @Test fun failedJobCanBeExplicitlyResumedFromPersistedRequest(){
        val dbPath=Files.createTempFile("cosmosis-resume",".db")
        SqliteStore(dbPath).use{store->
            store.migrate()
            var fail=true
            val caps=ProviderCapabilities(textToImage=true,outputFormats=setOf("png"),maxReferenceImages=0)
            val fake=object:ImageProvider{
                override val id="resume-fake"
                override fun capabilities(model:String)=caps
                override fun models()=listOf(ModelDefinition(id,"m","resume fake",caps))
                override fun generate(request:GenerationRequest):GenerationResult{
                    if(fail)error("planned failure")
                    return GenerationResult(request.id,id,request.model,listOf(GeneratedImage(byteArrayOf(1),"image/png")),1)
                }
                override fun edit(request:GenerationRequest)=generate(request)
                override fun testConnection()=ConnectionStatus(true,"ok",0)
            }
            JobEngine(ProviderRegistry().register(fake),store,1).use{engine->
                val request=GenerationRequest(prompt="persist me",model="m")
                val job=engine.submit(fake.id,request,false,JobBudget(maxGenerations=1,maxRetries=0,timeoutSeconds=5))
                waitForJob(engine,job.id,JobState.FAILED)
                assertTrue(engine.canResume(job.id))
                assertEquals("persist me",engine.request(job.id)?.prompt)
                fail=false
                engine.resume(job.id)
                waitForJob(engine,job.id,JobState.COMPLETE)
                assertFalse(engine.canResume(job.id))
            }
        }
    }

    @Test fun modelRegistryMigrates(){val r=ModelRegistry.parse("""{"schemaVersion":0,"models":[]}""").migrate();assertEquals(ModelRegistry.CURRENT_SCHEMA,r.schemaVersion)}
    @Test fun agentIndexFindsNaturalLanguageIntent(){val d=Files.createTempDirectory("docs");Files.writeString(d.resolve("x.html"),"""<section id="s"></section><script id="agent-index" type="application/json">{"capability":"smart-subject-mask","module":"x","intents":["remove-background"],"aliases":["cutout"]}</script>""");val idx=AgentIndex(d);idx.rebuild();assertTrue(idx.lookup("remove background").isNotEmpty())}    private fun waitForJob(engine:JobEngine,id:String,state:JobState,timeoutMs:Long=5_000){
        val deadline=System.currentTimeMillis()+timeoutMs
        while(System.currentTimeMillis()<deadline){
            if(engine.snapshot().firstOrNull{it.id==id}?.state==state)return
            Thread.sleep(10)
        }
        fail("Timed out waiting for job $id -> $state; actual="+engine.snapshot().firstOrNull{it.id==id}?.state)
    }

}
