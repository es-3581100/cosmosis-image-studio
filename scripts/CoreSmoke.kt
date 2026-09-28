import studio.cosmosis.*
import studio.cosmosis.analysis.LocalVisualAnalyzer
import studio.cosmosis.docs.AgentIndex
import studio.cosmosis.export.HtmlReportExporter
import studio.cosmosis.image.ImageToPrompt
import studio.cosmosis.image.PromptOutputMode
import studio.cosmosis.lineage.VersionGraph
import studio.cosmosis.lineage.VersionGraphLayout
import studio.cosmosis.mask.MaskDocument
import studio.cosmosis.mask.MaskStroke
import studio.cosmosis.mask.SmartMask
import studio.cosmosis.orml.OrmlRegistry
import studio.cosmosis.orml.OrmlExecutor
import studio.cosmosis.orml.OrmlInvocation
import studio.cosmosis.prompt.PromptExchange
import studio.cosmosis.prompt.PromptLibrary
import studio.cosmosis.provider.*
import studio.cosmosis.security.Redaction
import studio.cosmosis.workers.Director
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO

fun check(name:String, block:()->Unit){ block();println("PASS $name") }
fun main(){
    val temp=Files.createTempDirectory("cosmosis-smoke-")
    check("prompt revisions + provenance"){
        val up=PromptAsset(id="up",title="Upstream",body="portrait studio lighting",treePath="PREMADE PROMPTS/Portrait",origin=PromptOrigin.UPSTREAM,source="fixture",sourceLicense="MIT",readOnly=true)
        val lib=PromptLibrary(listOf(up));val local=lib.copyUpstream("up");require(local.id!="up"&&local.derivedFrom=="up"&&!local.readOnly);lib.update(local.id){it.body="changed"};require(lib.revisions(local.id).size==1&&lib.revisions(local.id).first().snapshot.body=="portrait studio lighting");require(lib.search("studio").isNotEmpty())
    }
    check("prompt exchange"){
        val p=PromptAsset(title="A \"quoted\" prompt",body="line1\nline2",explicitKeywords=setOf("poster","warm"));val f=temp.resolve("prompt.json");PromptExchange.exportPrompt(p,f);val q=PromptExchange.importPrompt(f);require(q.title==p.title&&q.body==p.body&&"poster" in q.explicitKeywords)
    }
    check("lineage branching"){
        val g=VersionGraph();val r=VersionNode(id="V0",parentId=null,assetId="a0",operation=VersionOperation.IMPORT,name="root");val a=VersionNode(id="V1",parentId="V0",assetId="a1",operation=VersionOperation.EDIT,name="a");val b=VersionNode(id="V2",parentId="V0",assetId="a2",operation=VersionOperation.REMIX,name="b");g.add(r);g.add(a);g.add(b);require(g.children("V0").map{it.id}.toSet()==setOf("V1","V2"));g.validateAcyclic();val layout=VersionGraphLayout.layout(g.all()).associateBy{it.id};require(layout.getValue("V1").x>layout.getValue("V0").x);require(layout.getValue("V1").y!=layout.getValue("V2").y)
    }
    check("mask undo redo invert feather persistence"){
        val m=MaskDocument(64,64);m.apply(MaskStroke(32,32,32,32,10,false));val c=m.coverage();require(c>0);m.undo();require(m.coverage()==0.0);m.redo();require(m.coverage()>0);m.feather(2);m.invert();val f=temp.resolve("mask.png");m.save(f);require(Files.size(f)>0)
    }
    val image=temp.resolve("fixture.png")
    run { val bi=BufferedImage(96,64,BufferedImage.TYPE_INT_RGB);for(y in 0 until 64)for(x in 0 until 96)bi.setRGB(x,y,if(x in 25..69 && y in 12..53) Color(230,210,160).rgb else Color(20,20,18).rgb);ImageIO.write(bi,"png",image.toFile()) }
    check("local image analysis + image-to-prompt + saliency mask"){
        val a=LocalVisualAnalyzer.analyze(image);require(a.width==96&&a.height==64&&a.palette.isNotEmpty());val p=ImageToPrompt.extract(image,PromptOutputMode.SUBJECT_REPLACEABLE);require(p.plainText.contains("STYLE DNA")&&p.regions.any{it.kind=="saliency"});val m=SmartMask.saliency(image);require(m.coverage()>0.05&&m.coverage()<0.95)
    }
    check("offline local preview provider"){
        val local=LocalPreviewProvider();val g=local.generate(GenerationRequest(prompt="offline pipeline",model="local-preview-v1",width=160,height=96,variants=2));require(g.images.size==2&&g.images.all{it.bytes.size>100});val mask=SmartMask.saliency(image);val mp=temp.resolve("smart.png");mask.save(mp);val e=local.edit(GenerationRequest(prompt="tint selected subject",model="local-preview-v1",references=listOf(ReferenceImage(image)),mask=ReferenceImage(mp)));require(e.images.single().bytes.size>100);require(local.testConnection().ok)
    }
    check("provider spend estimator"){val req=GenerationRequest(prompt="x",model="gemini-3.1-flash-image",variants=2,metadata=mapOf("imageSize" to "2K"));require(ProviderCostEstimator.estimateUsd("local",req)==0.0);require(ProviderCostEstimator.estimateUsd("gemini",req)==null);require(ProviderCostEstimator.estimateUsd("openai",req)==null)}
    check("capability validation"){
        val cap=ProviderCapabilities(textToImage=true,imageToImage=false,maskEditing=false,maxReferenceImages=0,outputFormats=setOf("png"));val req=GenerationRequest(prompt="x",model="m",references=listOf(ReferenceImage(image,"image/png")));val err=runCatching{CapabilityValidator.validate(req,cap,false)}.exceptionOrNull();require(err!=null && (err.message.orEmpty().contains("reference",true)||err.message.orEmpty().contains("image-to-image",true)))
    }
    check("provider wire translation"){
        val req=GenerationRequest(prompt="draw a square",model="gpt-image-2.5-flare",width=1024,height=1024,quality="high",transparent=true,metadata=mapOf("compression" to "80"));val o=OpenAiWire.generationBody(req);require(o.contains("\"size\":\"1024x1024\"")&&o.contains("\"output_compression\":80"));val g=GeminiWire.interactionsBody(GenerationRequest(prompt="draw",model="gemini-3.1-flash-image",aspectRatio="16:9",previousResponseId="ix_123",metadata=mapOf("imageSize" to "2K","thinkingLevel" to "high","searchGrounding" to "true")));require(g.contains("\"response_format\"")&&g.contains("\"image_size\":\"2K\"")&&g.contains("\"previous_interaction_id\":\"ix_123\"")&&g.contains("\"google_search\"")&&g.contains("\"thinking_level\":\"high\""))
        val parsed=JsonUtil.imageBlocks("""{"id":"ix","steps":[{"type":"function_result","data":"do-not-treat-as-image"},{"type":"model_output","content":[{"type":"image","mime_type":"image/png","data":"aGVsbG8="}]}]}""");require(parsed.size==1&&parsed.single().data=="aGVsbG8=")
    }
    check("model registry parse + migration"){
        val r=ModelRegistry.parse("""{"schemaVersion":0,"models":[{"provider":"x","id":"m","textToImage":true,"imageToImage":false,"maskEditing":false,"multipleReferences":false,"transparentBackground":false,"parallelVariants":false,"maxReferenceImages":0,"qualityLevels":"auto","outputFormats":"png"}]}""").migrate();require(r.schemaVersion==ModelRegistry.CURRENT_SCHEMA&&r.find("x","m")!=null)
    }
    check("secret redaction"){
        val s=Redaction.sanitize("Authorization: Bearer abcdefghijklmnop API_KEY=supersecret " + "sk-proj-" + "abcdefghijklmnopqrstuvwxyz");require(!s.contains("supersecret")&&!s.contains("sk-proj-")&&!s.contains("abcdefghijklmnop"))
    }
    check("ORML capability registry"){
        val all=OrmlRegistry.capabilities;require(all.any{it.module=="orml-u2net"}&&all.any{it.module=="orml-super-resolution"});val r=OrmlExecutor().invoke(OrmlInvocation("smart-subject-mask","/tmp/x"));require(!r.ok)
    }
    check("HTML export secret sanitation"){
        val out=temp.resolve("report.html");HtmlReportExporter.projectReport(ProjectRecord(name="Smoke",root=temp.toString()),emptyList(),listOf(GenerationRecord(parentImageId=null,inputImageIds=emptyList(),maskId=null,promptId=null,userPrompt="sk-proj-" + "abcdefghijklmnopqrstuvwxyz",compiledPrompt="safe",providerId="openai",model="m",endpoint="e",settings=mapOf("api_key" to "supersecret"),outputImageIds=emptyList(),durationMs=1,retryCount=0,workerId="w")),out);val t=Files.readString(out);require(!t.contains("supersecret")&&!t.contains("sk-proj-"));require(t.contains("#10100e",true))
    }
    check("bounded director planning"){
        val plan=Director.plan("replace background and preserve subject",WorkflowMode.AGENT_BUILD,JobBudget(maxGenerations=3,maxRetries=1,maxParallelWorkers=2),hasImage=true,hasMask=false)
        require(plan.steps.count{it.role==WorkerRole.GENERATION}==3);require(plan.steps.any{it.role==WorkerRole.CRITIC});require(plan.budget.maxRetries==1)
    }
    check("agent index parsing"){
        val d=temp.resolve("docs");Files.createDirectories(d);Files.writeString(d.resolve("u2net.html"),"""<html><section id="smart-subject-mask">x</section><script type="application/json" id="agent-index">{"capability":"smart-subject-mask","module":"orml-u2net","intents":["remove-background"],"aliases":["cutout"]}</script></html>""");val idx=AgentIndex(d);idx.rebuild();require(idx.lookup("remove background").isNotEmpty());require(idx.contextPacket("remove background").contains("smart-subject-mask"))
    }
    println("CORE_SMOKE_PASS temp=$temp")
}
