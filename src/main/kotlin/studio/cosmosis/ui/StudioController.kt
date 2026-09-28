package studio.cosmosis.ui

import studio.cosmosis.*
import studio.cosmosis.analysis.LocalVisualAnalyzer
import studio.cosmosis.docs.AgentIndex
import studio.cosmosis.export.HtmlReportExporter
import studio.cosmosis.image.ImageToPrompt
import studio.cosmosis.image.PromptOutputMode
import studio.cosmosis.lineage.VersionGraph
import studio.cosmosis.mask.MaskDocument
import studio.cosmosis.mask.MaskStroke
import studio.cosmosis.mask.SmartMask
import studio.cosmosis.prompt.PromptCompiler
import studio.cosmosis.prompt.PromptLibrary
import studio.cosmosis.prompt.PromptExchange
import studio.cosmosis.provider.*
import studio.cosmosis.storage.ProjectStore
import studio.cosmosis.storage.SqliteStore
import studio.cosmosis.workers.Director
import studio.cosmosis.workers.ImageCritic
import studio.cosmosis.workers.JobEngine
import java.nio.file.*
import java.security.MessageDigest
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import javax.imageio.ImageIO

class StudioController(val state:StudioState,private val docsRoot:Path=Path.of("docs")) : AutoCloseable {
    private var project:ProjectRecord?=null; private var paths:ProjectPaths?=null; private var db:SqliteStore?=null; private var engine:JobEngine?=null
    private var prompts=PromptLibrary(); private var graph=VersionGraph(); private val assets=linkedMapOf<String,ImageAsset>(); private val generations=mutableListOf<GenerationRecord>(); private var currentMaskId:String?=null
    val providers=ProviderRegistry.default(); val agentIndex=AgentIndex(docsRoot)

    fun createProject(root:Path,name:String){ closeProject(); val(p,pp)=ProjectStore.create(root,name);project=p;paths=pp;db=SqliteStore(pp.db).also{it.migrate();it.recoverInterruptedJobs()};prompts=PromptLibrary(db!!.loadPrompts(),db!!.loadPromptRevisions());graph=VersionGraph(db!!.loadVersions());engine=JobEngine(providers,db!!,2);seedPremade();agentIndex.rebuild();sync("PROJECT CREATED") }
    fun openProject(root:Path){ closeProject(); val pp=ProjectStore.paths(root);require(Files.exists(pp.db)){"project.db not found in $root"};paths=pp;db=SqliteStore(pp.db).also{it.migrate();it.recoverInterruptedJobs()}; val meta=readProjectJson(root.resolve("project.json"));project=db!!.loadProject()?:ProjectRecord(id=meta["id"]?:newId("project"),name=meta["name"]?:root.fileName.toString(),root=root.toAbsolutePath().toString());prompts=PromptLibrary(db!!.loadPrompts(),db!!.loadPromptRevisions());graph=VersionGraph(db!!.loadVersions());db!!.loadAssets().forEach{assets[it.id]=it};generations += db!!.loadGenerations();engine=JobEngine(providers,db!!,2);seedPremade();agentIndex.rebuild(); val a=assetForCurrent();state.update{it.copy(imagePath=a?.let{pp.root.resolve(it.path).toString()},imageWidth=a?.width?:0,imageHeight=a?.height?:0)};sync("PROJECT OPEN / INTERRUPTED JOBS PRESERVED") }
    fun importImage(source:Path){ val pp=requireNotNull(paths){"Create/open a project first"}; val a=ProjectStore.importImage(pp,source);assets[a.id]=a;db!!.saveAsset(a); val v=VersionNode(parentId=currentVersionId(),assetId=a.id,operation=VersionOperation.IMPORT,name=source.fileName.toString());graph.add(v);db!!.saveVersion(v);project!!.currentVersionId=v.id;project!!.updatedAt=nowIso();db!!.saveProject(project!!);state.update{it.copy(imagePath=pp.root.resolve(a.path).toString(),imageWidth=a.width,imageHeight=a.height,currentVersion=v.id,versions=graph.all(),message="IMPORTED / ${a.width}×${a.height}")} }
    fun analyzeCurrent():String { val img=Path.of(requireNotNull(state.get().imagePath){"No image loaded"});val result=ImageToPrompt.extract(img,PromptOutputMode.SUBJECT_REPLACEABLE);state.update{it.copy(promptBody=result.plainText,promptTitle="Image → Prompt / ${img.fileName}",message="LOCAL ANALYSIS COMPLETE")};return result.plainText }
    fun savePrompt(title:String,body:String,treePath:String="MY PROMPTS/General"):PromptAsset = savePromptAsset(PromptAsset(title=title.ifBlank{"Untitled prompt"},body=body,treePath=treePath))
    fun savePromptAsset(draft:PromptAsset):PromptAsset {
        val p=draft.copy(id=newId("prompt"),title=draft.title.ifBlank{"Untitled prompt"},treePath=draft.treePath.ifBlank{"MY PROMPTS/General"},origin=if(draft.origin==PromptOrigin.UPSTREAM)PromptOrigin.DERIVED else draft.origin,readOnly=false,createdAt=nowIso(),updatedAt=nowIso())
        prompts.create(p);db!!.savePrompt(p);state.update{it.copy(promptTitle=p.title,promptBody=p.body,message="PROMPT SAVED / ${p.id}")};return p
    }
    fun promptSearch(q:String)=prompts.search(q)
    fun allPrompts()=prompts.all()
    fun upstreamPrompts()=prompts.all().filter{it.readOnly}
    fun addUpstream(seed:PromptAsset){require(seed.readOnly); val all=prompts.all(true);if(all.none{it.id==seed.id})prompts=PromptLibrary(all+seed)}
    fun copyUpstream(id:String):PromptAsset {val p=prompts.copyUpstream(id);db!!.savePrompt(p);sync("COPIED TO MY PROMPTS");return p}
    fun updatePrompt(id:String,title:String,body:String,treePath:String):PromptAsset = updatePromptAsset(id,PromptAsset(title=title,body=body,treePath=treePath))
    fun updatePromptAsset(id:String,draft:PromptAsset):PromptAsset {
        val p=prompts.update(id){target->
            target.title=draft.title.ifBlank{target.title};target.summary=draft.summary;target.body=draft.body;target.negativeConstraints=draft.negativeConstraints;target.intent=draft.intent;target.workflow=draft.workflow;target.treePath=draft.treePath.ifBlank{target.treePath};target.providerHints=draft.providerHints;target.modelHints=draft.modelHints;target.aspectRatioHints=draft.aspectRatioHints;target.styleTags=draft.styleTags;target.subjectTags=draft.subjectTags;target.compositionTags=draft.compositionTags;target.lightingTags=draft.lightingTags;target.cameraTags=draft.cameraTags;target.materialTags=draft.materialTags;target.textRenderingTags=draft.textRenderingTags;target.variables=draft.variables;target.referenceImageHints=draft.referenceImageHints;target.notes=draft.notes;target.explicitKeywords=draft.explicitKeywords;target.favorite=draft.favorite
        }
        prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT UPDATED / ${p.id}");return p
    }
    fun duplicatePrompt(id:String):PromptAsset {val p=prompts.duplicate(id);db!!.savePrompt(p);sync("PROMPT DUPLICATED");return p}
    fun movePrompt(id:String,path:String):PromptAsset {val p=prompts.move(id,path);prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT MOVED");return p}
    fun renamePrompt(id:String,title:String):PromptAsset {val p=prompts.rename(id,title);prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT RENAMED");return p}
    fun trashPrompt(id:String){val p=prompts.trash(id);prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT MOVED TO TRASH")}
    fun exportPrompt(id:String,path:Path):Path {PromptExchange.exportPrompt(requireNotNull(prompts.get(id)),path);sync("PROMPT EXPORTED / ${path.fileName}");return path}
    fun importPrompt(path:Path):PromptAsset {val p=PromptExchange.importPrompt(path);prompts.create(p);db!!.savePrompt(p);sync("PROMPT IMPORTED / ${p.id}");return p}
    fun promptRevisions(id:String)=prompts.revisions(id)
    fun configureCustomProvider(baseUrl:String,keyEnv:String="CUSTOM_OPENAI_API_KEY"){require(baseUrl.startsWith("http")){"Custom base URL must use http/https"};providers.register(OpenAiCompatibleProvider("custom",{System.getenv(keyEnv)},baseUrl));state.update{it.copy(message="CUSTOM PROVIDER REGISTERED / secret env $keyEnv")}}
    fun testProvider(id:String):ConnectionStatus=providers.get(id).testConnection()
    fun modelsFor(id:String)=providers.get(id).models()
    fun capabilitiesFor(id:String,model:String)=providers.get(id).capabilities(model)
    fun planAgentBuild(intent:String,budget:JobBudget=JobBudget()):String {val plan=Director.plan(intent,WorkflowMode.AGENT_BUILD,budget,state.get().imagePath!=null,state.get().maskPath!=null);state.update{it.copy(plan=plan,message="PLAN ${plan.id} / ${plan.steps.size} STEPS")};return plan.steps.joinToString("\n"){"${it.index}. ${it.role} — ${it.action}${if(it.optional)" [optional]" else ""}"}}
    fun runAgentBuild(intent:String,providerId:String,model:String,variants:Int=4,budget:JobBudget=JobBudget(maxGenerations=4,maxRetries=1,maxParallelWorkers=2,timeoutSeconds=180)):String {
        require(variants in 1..budget.maxGenerations){"variants exceeds Agent Build budget"}
        val sourcePath=state.get().imagePath?.let(Path::of)
        val plan=Director.plan(intent,WorkflowMode.AGENT_BUILD,budget.copy(maxGenerations=variants),sourcePath!=null,state.get().maskPath!=null)
        val docsPacket=agentIndex.contextPacket(intent,5000)
        val related=prompts.search(intent).take(4)
        val localAnalysis=sourcePath?.let{runCatching{ImageToPrompt.extract(it,PromptOutputMode.SUBJECT_REPLACEABLE)}.getOrNull()}
        val shouldSeedMask=sourcePath!=null&&state.get().maskPath==null&&Regex("(?i)\b(background|replace|preserve|subject|person|region|clothing|mask)\b").containsMatchIn(intent)
        if(shouldSeedMask)runCatching{smartSaliencyMask()}
        val compiledIntent=buildString{append(intent.trim());localAnalysis?.plainText?.takeIf{it.isNotBlank()}?.let{append("\n\nLOCAL VISUAL CONTEXT\n");append(it)};if(related.isNotEmpty()){append("\n\nRELEVANT LOCAL PROMPT PATTERNS\n");related.forEach{append("- ");append(it.title);append(": ");append(it.body.take(500));append('\n')}}}
        val agentPrompt=PromptAsset(title="Agent Build / ${intent.take(64)}",summary="Bounded Director-compiled image job",body=compiledIntent,treePath="MY PROMPTS/Agent Builds",origin=PromptOrigin.AGENT,source="Director/${plan.id}",notes="Documentation packet consulted: ${if(docsPacket.isBlank())"none" else "indexed local agent docs"}",semanticKeywords=intent.lowercase().split(Regex("\\W+")).filter{it.length>2}.toSet())
        val storedPrompt=savePromptAsset(agentPrompt)
        val reportDir=requireNotNull(paths).metadata.resolve("workers");Files.createDirectories(reportDir);val report=reportDir.resolve("agent-build-${plan.id}.txt")
        Files.writeString(report,studio.cosmosis.security.Redaction.sanitize(buildString{appendLine("COSMOSIS / AGENT BUILD");appendLine("plan=${plan.id}");appendLine("intent=$intent");appendLine("provider=$providerId");appendLine("model=$model");appendLine("budget.generations=${budget.maxGenerations}");appendLine("budget.retries=${budget.maxRetries}");appendLine("budget.parallel=${budget.maxParallelWorkers}");app