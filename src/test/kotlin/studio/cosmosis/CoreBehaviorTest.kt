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
import java.nio.file.Files

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
        val layout=VersionGraphLayout.layout(g.all());assertTrue(layout.nodes.any{it.id=="v1"&&it.depth==1});assertTrue(layout.nodes.any{it.id=="v2"&&it.depth==1})
    }
    @Test fun maskUndoRedoWorks(){val m=MaskDocument(32,32);m.apply(MaskStroke(16,16,16,16,5,false));assertTrue(m.coverage()>0);assertTrue(m.undo());assertEquals(0.0,m.coverage());assertTrue(m.redo())}
    @Test fun validatorRejectsUnsupportedMask(){val f=Files.createTempFile("mask",".png");val req=GenerationRequest(prompt="x",model="m",mask=ReferenceImage(f));assertFailsWith<CapabilityException>{CapabilityValidator.validate(req,ProviderCapabilities(textToImage=true),false)}}
    @Test fun wireFormatsAreProviderSpecific(){
        val o=OpenAiWire.generationBody(GenerationRequest(prompt="x",model="gpt-image-2.5-flare",quality="high"));assertContains(o,"output_format")
        val g=GeminiWire.interactionsBody(GenerationRequest(prompt="x",model="gemini-3.1-flash-image",aspectRatio="16:9",previousResponseId="ix_1",metadata=mapOf("searchGrounding" to "true","thinkingLevel" to "high")))
        assertContains(g,"response_format");assertContains(g,"previous_interaction_id");assertContains(g,"google_search");assertContains(g,"thinking_level")
    }
    @Test fun geminiImageParsingIgnoresUnrelatedData(){
        val blocks=JsonUtil.imageBlocks("""{"steps":[{"type":"tool_result","data":"bm90LWltYWdl"},{"type":"model_output","content":[{"type":"image","mime_type":"image/png","data":"aGVsbG8="}]}]}""")
        assertEquals(1,blocks.size);assertEquals("aGVsbG8=",blocks.single().data)
    }
    @Test fun secretsAreRedacted(){val r=Redaction.sanitize("api_key=supersecret " + "sk-proj-" + "abcdefghijklmnopqrstuvwxyz");assertFalse(r.contains("supersecret"));assertFalse(r.contains("sk-proj-"))}
    @Test fun ormlFailsClosedWithoutAdapter(){val result=OrmlExecutor().invoke(OrmlInvocation("smart-subject-mask","x"));assertFalse(result.ok)}
    @Test fun modelRegistryMigrates(){val r=ModelRegistry.parse("""{"schemaVersion":0,"models":[]}""").migrate();assertEquals(ModelRegistry.CURRENT_SCHEMA,r.schemaVersion)}
    @Test fun agentIndexFindsNaturalLanguageIntent(){val d=Files.createTempDirectory("docs");Files.writeString(d.resolve("x.html"),"""<section id="s"></section><script id="agent-index" type="application/json">{"capability":"smart-subject-mask","module":"x","intents":["remove-background"],"aliases":["cutout"]}</script>""");val idx=AgentIndex(d);idx.rebuild();assertTrue(idx.lookup("remove background").isNotEmpty())}
}
