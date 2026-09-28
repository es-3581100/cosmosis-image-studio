package studio.cosmosis.ui

import studio.cosmosis.*
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
import studio.cosmosis.security.Redaction
import studio.cosmosis.storage.ProjectStore
import studio.cosmosis.storage.SqliteStore
import studio.cosmosis.workers.Director
import studio.cosmosis.workers.ImageCritic
import studio.cosmosis.workers.JobEngine
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.nio.file.*
import java.security.MessageDigest
import javax.imageio.ImageIO

class StudioController(val state:StudioState,private val docsRoot:Path=Path.of("docs")) : AutoCloseable {
    private var project:ProjectRecord?=null
    private var paths:ProjectPaths?=null
    private var db:SqliteStore?=null
    private var engine:JobEngine?=null
    private var prompts=PromptLibrary()
    private var graph=VersionGraph()
    private val assets=linkedMapOf<String,ImageAsset>()
    private val generations=mutableListOf<GenerationRecord>()
    private var currentMaskId:String?=null

    val providers=ProviderRegistry.default()
    val agentIndex=AgentIndex(docsRoot)

    fun createProject(root:Path,name:String){
        closeProject()
        val(p,pp)=ProjectStore.create(root,name)
        project=p;paths=pp
        db=SqliteStore(pp.db).also{it.migrate();it.recoverInterruptedJobs()}
        prompts=PromptLibrary(db!!.loadPrompts(),db!!.loadPromptRevisions())
        graph=VersionGraph(db!!.loadVersions())
        engine=JobEngine(providers,db!!,2)
        seedPremade();agentIndex.rebuild();sync("PROJECT CREATED")
    }

    fun openProject(root:Path){
        closeProject()
        val pp=ProjectStore.paths(root);require(Files.exists(pp.db)){"project.db not found in $root"}
        paths=pp
        db=SqliteStore(pp.db).also{it.migrate();it.recoverInterruptedJobs()}
        val meta=readProjectJson(root.resolve("project.json"))
        project=db!!.loadProject()?:ProjectRecord(id=meta["id"]?:newId("project"),name=meta["name"]?:root.fileName.toString(),root=root.toAbsolutePath().toString())
        prompts=PromptLibrary(db!!.loadPrompts(),db!!.loadPromptRevisions())
        graph=VersionGraph(db!!.loadVersions())
        db!!.loadAssets().forEach{assets[it.id]=it}
        generations+=db!!.loadGenerations()
        engine=JobEngine(providers,db!!,2)
        seedPremade();agentIndex.rebuild()
        val a=assetForCurrent()
        state.update{it.copy(imagePath=a?.let{pp.root.resolve(it.path).toString()},imageWidth=a?.width?:0,imageHeight=a?.height?:0)}
        sync("PROJECT OPEN / INTERRUPTED JOBS PRESERVED")
    }

    fun importImage(source:Path){
        val pp=requireNotNull(paths){"Create/open a project first"}
        val a=ProjectStore.importImage(pp,source);assets[a.id]=a;db!!.saveAsset(a)
        val v=VersionNode(parentId=currentVersionId(),assetId=a.id,operation=VersionOperation.IMPORT,name=source.fileName.toString())
        graph.add(v);db!!.saveVersion(v)
        project!!.currentVersionId=v.id;project!!.updatedAt=nowIso();db!!.saveProject(project!!)
        state.update{it.copy(imagePath=pp.root.resolve(a.path).toString(),imageWidth=a.width,imageHeight=a.height,currentVersion=v.id,versions=graph.all(),message="IMPORTED / ${a.width}×${a.height}")}
    }

    fun importClipboardImage(){
        val pp=requireNotNull(paths){"Create/open a project first"}
        val clip=Toolkit.getDefaultToolkit().systemClipboard
        require(clip.isDataFlavorAvailable(DataFlavor.imageFlavor)){"Clipboard does not contain an image"}
        val awt=clip.getData(DataFlavor.imageFlavor) as java.awt.Image
        val w=awt.getWidth(null);val h=awt.getHeight(null);require(w>0&&h>0){"Clipboard image dimensions unavailable"}
        val image=BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);val g=image.createGraphics();g.drawImage(awt,0,0,null);g.dispose()
        val temp=pp.metadata.resolve("clipboard-${System.currentTimeMillis()}.png");ImageIO.write(image,"png",temp.toFile())
        try{importImage(temp)}finally{Files.deleteIfExists(temp)}
    }

    fun analyzeCurrent():String{
        val img=Path.of(requireNotNull(state.get().imagePath){"No image loaded"})
        val result=ImageToPrompt.extract(img,PromptOutputMode.SUBJECT_REPLACEABLE)
        state.update{it.copy(promptBody=result.plainText,promptTitle="Image → Prompt / ${img.fileName}",message="LOCAL ANALYSIS COMPLETE")}
        return result.plainText
    }

    fun savePrompt(title:String,body:String,treePath:String="MY PROMPTS/General"):PromptAsset=
        savePromptAsset(PromptAsset(title=title.ifBlank{"Untitled prompt"},body=body,treePath=treePath))

    fun savePromptAsset(draft:PromptAsset):PromptAsset{
        val p=draft.copy(
            id=newId("prompt"),title=draft.title.ifBlank{"Untitled prompt"},treePath=draft.treePath.ifBlank{"MY PROMPTS/General"},
            origin=if(draft.origin==PromptOrigin.UPSTREAM)PromptOrigin.DERIVED else draft.origin,readOnly=false,
            createdAt=nowIso(),updatedAt=nowIso()
        )
        prompts.create(p);db!!.savePrompt(p)
        state.update{it.copy(promptTitle=p.title,promptBody=p.body,message="PROMPT SAVED / ${p.id}")}
        return p
    }

    fun promptSearch(q:String)=prompts.search(q)
    fun allPrompts()=prompts.all()
    fun upstreamPrompts()=prompts.all().filter{it.readOnly}
    fun addUpstream(seed:PromptAsset){require(seed.readOnly);val all=prompts.all(true);if(all.none{it.id==seed.id})prompts=PromptLibrary(all+seed)}
    fun copyUpstream(id:String):PromptAsset{val p=prompts.copyUpstream(id);db!!.savePrompt(p);sync("COPIED TO MY PROMPTS");return p}
    fun updatePrompt(id:String,title:String,body:String,treePath:String):PromptAsset=updatePromptAsset(id,PromptAsset(title=title,body=body,treePath=treePath))

    fun updatePromptAsset(id:String,draft:PromptAsset):PromptAsset{
        val p=prompts.update(id){target->
            target.title=draft.title.ifBlank{target.title};target.summary=draft.summary;target.body=draft.body
            target.negativeConstraints=draft.negativeConstraints;target.intent=draft.intent;target.workflow=draft.workflow
            target.treePath=draft.treePath.ifBlank{target.treePath};target.providerHints=draft.providerHints;target.modelHints=draft.modelHints
            target.aspectRatioHints=draft.aspectRatioHints;target.styleTags=draft.styleTags;target.subjectTags=draft.subjectTags
            target.compositionTags=draft.compositionTags;target.lightingTags=draft.lightingTags;target.cameraTags=draft.cameraTags
            target.materialTags=draft.materialTags;target.textRenderingTags=draft.textRenderingTags;target.variables=draft.variables
            target.referenceImageHints=draft.referenceImageHints;target.notes=draft.notes;target.explicitKeywords=draft.explicitKeywords
            target.favorite=draft.favorite
        }
        prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)}
        db!!.savePrompt(p);sync("PROMPT UPDATED / ${p.id}");return p
    }

    fun duplicatePrompt(id:String):PromptAsset{val p=prompts.duplicate(id);db!!.savePrompt(p);sync("PROMPT DUPLICATED");return p}
    fun movePrompt(id:String,path:String):PromptAsset{val p=prompts.move(id,path);prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT MOVED");return p}
    fun renamePrompt(id:String,title:String):PromptAsset{val p=prompts.rename(id,title);prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT RENAMED");return p}
    fun trashPrompt(id:String){val p=prompts.trash(id);prompts.revisions(id).firstOrNull()?.let{db!!.savePromptRevision(it)};db!!.savePrompt(p);sync("PROMPT MOVED TO TRASH")}
    fun exportPrompt(id:String,path:Path):Path{PromptExchange.exportPrompt(requireNotNull(prompts.get(id)),path);sync("PROMPT EXPORTED / ${path.fileName}");return path}
    fun importPrompt(path:Path):PromptAsset{val p=PromptExchange.importPrompt(path);prompts.create(p);db!!.savePrompt(p);sync("PROMPT IMPORTED / ${p.id}");return p}
    fun promptRevisions(id:String)=prompts.revisions(id)

    fun configureCustomProvider(baseUrl:String,keyEnv:String="CUSTOM_OPENAI_API_KEY"){
        require(baseUrl.startsWith("http")){"Custom base URL must use http/https"}
        providers.register(OpenAiCompatibleProvider("custom",{System.getenv(keyEnv)},baseUrl))
        state.update{it.copy(message="CUSTOM PROVIDER REGISTERED / secret env $keyEnv")}
    }
    fun testProvider(id:String):ConnectionStatus=providers.get(id).testConnection()
    fun modelsFor(id:String)=providers.get(id).models()
    fun capabilitiesFor(id:String,model:String)=providers.get(id).capabilities(model)

    fun planAgentBuild(intent:String,budget:JobBudget=JobBudget()):String{
        val plan=Director.plan(intent,WorkflowMode.AGENT_BUILD,budget,state.get().imagePath!=null,state.get().maskPath!=null)
        state.update{it.copy(plan=plan,message="PLAN ${plan.id} / ${plan.steps.size} STEPS")}
        return plan.steps.joinToString("\n"){"${it.index}. ${it.role} — ${it.action}${if(it.optional)" [optional]" else ""}"}
    }

    fun runAgentBuild(
        intent:String,providerId:String,model:String,variants:Int=4,
        budget:JobBudget=JobBudget(maxGenerations=4,maxRetries=1,maxParallelWorkers=2,timeoutSeconds=180)
    ):String{
        require(variants in 1..budget.maxGenerations){"variants exceeds Agent Build budget"}
        val sourcePath=state.get().imagePath?.let(Path::of)
        val plan=Director.plan(intent,WorkflowMode.AGENT_BUILD,budget.copy(maxGenerations=variants),sourcePath!=null,state.get().maskPath!=null)
        state.update{it.copy(plan=plan,message="AGENT BUILD ${plan.id} / PREPARING")}
        val docsPacket=agentIndex.contextPacket(intent,5000)
        val related=prompts.search(intent).take(4)
        val localAnalysis=sourcePath?.let{runCatching{ImageToPrompt.extract(it,PromptOutputMode.SUBJECT_REPLACEABLE)}.getOrNull()}
        val shouldSeedMask=sourcePath!=null&&state.get().maskPath==null&&Regex("(?i)\\b(background|replace|preserve|subject|person|region|clothing|mask)\\b").containsMatchIn(intent)
        if(shouldSeedMask)runCatching{smartSaliencyMask()}
        val compiledIntent=buildString{
            append(intent.trim())
            localAnalysis?.plainText?.takeIf{it.isNotBlank()}?.let{append("\n\nLOCAL VISUAL CONTEXT\n");append(it)}
            if(related.isNotEmpty()){
                append("\n\nRELEVANT LOCAL PROMPT PATTERNS\n")
                related.forEach{append("- ");append(it.title);append(": ");append(it.body.take(500));append('\n')}
            }
        }
        val agentPrompt=PromptAsset(
            title="Agent Build / ${intent.take(64)}",summary="Bounded Director-compiled image job",body=compiledIntent,
            treePath="MY PROMPTS/Agent Builds",origin=PromptOrigin.AGENT,source="Director/${plan.id}",
            notes="Documentation packet consulted: ${if(docsPacket.isBlank())"none" else "indexed local agent docs"}",
            semanticKeywords=intent.lowercase().split(Regex("\\W+")).filter{it.length>2}.toSet()
        )
        val storedPrompt=savePromptAsset(agentPrompt)
        val reportDir=requireNotNull(paths).metadata.resolve("workers");Files.createDirectories(reportDir)
        val report=reportDir.resolve("agent-build-${plan.id}.txt")
        Files.writeString(report,Redaction.sanitize(buildString{
            appendLine("COSMOSIS / AGENT BUILD")
            appendLine("plan=${plan.id}");appendLine("intent=$intent");appendLine("provider=$providerId");appendLine("model=$model")
            appendLine("budget.generations=${plan.budget.maxGenerations}");appendLine("budget.retries=${plan.budget.maxRetries}")
            appendLine("budget.parallel=${plan.budget.maxParallelWorkers}");appendLine("budget.timeout=${plan.budget.timeoutSeconds}")
            appendLine("documentation:");appendLine(docsPacket.ifBlank{"none"})
            appendLine("steps:");plan.steps.forEach{appendLine("${it.index}. ${it.role}: ${it.action}")}
        }))
        val caps=capabilitiesFor(providerId,model)
        val editing=sourcePath!=null&&caps.imageToImage
        submitGeneration(
            prompt=compiledIntent,providerId=providerId,model=model,variants=variants,edit=editing,
            transparent=false,quality=null,promptId=storedPrompt.id,aspectRatio=null,metadata=emptyMap(),budget=plan.budget
        ){done->
            done.onSuccess{candidates->
                val append=buildString{
                    appendLine()
                    appendLine("results:")
                    candidates.forEachIndexed{index,path->
                        appendLine("candidate.${index+1}=$path")
                        val critique=runCatching{ImageCritic.compare(sourcePath,path,intent)}.getOrNull()
                        critique?.observations?.forEach{appendLine("critic: ${it.category} / ${it.status} / ${it.note}")}
                    }
                    appendLine("status=COMPLETE")
                }
                Files.writeString(report,Redaction.sanitize(append),StandardOpenOption.APPEND)
                state.update{it.copy(plan=plan,jobState="COMPLETE",message="AGENT BUILD ${plan.id} / COMPLETE")}
            }.onFailure{e->
                Files.writeString(report,Redaction.sanitize("\nstatus=FAILED\nerror=${e.message}\n"),StandardOpenOption.APPEND)
                state.update{it.copy(plan=plan,jobState=if(e is kotlinx.coroutines.CancellationException)"CANCELLED" else "FAILED",message="AGENT BUILD ${plan.id} / FAILED / ${e.message}")}
            }
        }
        return plan.id
    }

    fun agentBuildReports():List<Path>{
        val dir=paths?.metadata?.resolve("workers")?:return emptyList()
        if(!Files.exists(dir))return emptyList()
        return Files.list(dir).use{it.filter{p->p.fileName.toString().startsWith("agent-build-")}.sorted().toList()}
    }

    fun saveMaskDocument(doc:MaskDocument):Path=persistMask(doc,"manual-brush")
    fun manualMask(strokes:List<MaskStroke>,invert:Boolean=false,feather:Int=0):Path{
        val img=requireNotNull(ImageIO.read(Path.of(requireNotNull(state.get().imagePath){"No image loaded"}).toFile()))
        val doc=MaskDocument(img.width,img.height);strokes.forEach(doc::apply);if(invert)doc.invert();if(feather>0)doc.feather(feather)
        return persistMask(doc,"manual-brush",feather,invert)
    }
    fun smartSaliencyMask():Path=persistMask(SmartMask.saliency(Path.of(requireNotNull(state.get().imagePath){"No image loaded"})),"smart-saliency")

    private fun persistMask(doc:MaskDocument,method:String,feather:Int=0,inverted:Boolean=false):Path{
        val pp=requireNotNull(paths);val imagePath=Path.of(requireNotNull(state.get().imagePath){"No image loaded"})
        val image=requireNotNull(ImageIO.read(imagePath.toFile()));require(doc.width==image.width&&doc.height==image.height){"Mask dimensions must match source image"}
        val stamp=System.currentTimeMillis();val out=pp.masks.resolve("mask-$stamp.png");doc.save(out)
        val overlay=pp.previews.resolve("mask-overlay-$stamp.png");val src=doc.toBufferedImage()
        val rgba=BufferedImage(src.width,src.height,BufferedImage.TYPE_INT_ARGB)
        for(y in 0 until src.height)for(x in 0 until src.width){val m=src.raster.getSample(x,y,0);val a=(m*.42).toInt().coerceIn(0,110);rgba.setRGB(x,y,(a shl 24) or (0xEF shl 16) or (0x44 shl 8) or 0x44)}
        ImageIO.write(rgba,"png",overlay.toFile())
        val source=assetForCurrent()
        val rec=MaskRecord(sourceAssetId=source?.id?:"unknown",path=pp.root.relativize(out).toString(),width=doc.width,height=doc.height,featherRadius=feather,inverted=inverted,method=method,derivedMetadata=mapOf("coverage" to "%.4f".format(doc.coverage())))
        db!!.saveMask(rec);currentMaskId=rec.id
        state.update{it.copy(maskPath=out.toString(),maskOverlayPath=overlay.toString(),message="MASK SAVED / ${rec.id}")}
        return out
    }

    fun generate(
        prompt:String,providerId:String,model:String,variants:Int=1,edit:Boolean=false,transparent:Boolean=false,
        quality:String?=null,promptId:String?=null,aspectRatio:String?=null,metadata:Map<String,String> = emptyMap()
    ){
        submitGeneration(prompt,providerId,model,variants,edit,transparent,quality,promptId,aspectRatio,metadata,JobBudget(maxGenerations=variants,maxRetries=2,maxParallelWorkers=2)){}
    }

    private fun submitGeneration(
        prompt:String,providerId:String,model:String,variants:Int,edit:Boolean,transparent:Boolean,quality:String?,
        promptId:String?,aspectRatio:String?,metadata:Map<String,String>,budget:JobBudget,
        done:(Result<List<Path>>)->Unit
    ){
        val pp=requireNotNull(paths){"Create/open a project first"}
        val current=assetForCurrent()
        val caps=capabilitiesFor(providerId,model)
        val refs=if(edit&&state.get().imagePath!=null)listOf(ReferenceImage(Path.of(state.get().imagePath!!),current?.mime?:"image/png"))else emptyList()
        val mask=if(edit&&caps.maskEditing)state.get().maskPath?.let{ReferenceImage(Path.of(it),"image/png")}else null
        val compiled=PromptCompiler.compile(prompt,preserve=if(edit)listOf("unmasked content","source subject unless explicitly changed")else emptyList())
        val parentVersion=currentVersionId();val parentAsset=current?.id;val generationId=newId("gen")
        val parentGeneration=parentVersion?.let(graph::get)?.generationId?.let{id->generations.lastOrNull{it.id==id}}
        val previousContext=if(edit&&caps.multiTurnEditing&&parentGeneration?.providerId.equals(providerId,true)&&parentGeneration?.model==model)parentGeneration.providerContextId else null
        val req=GenerationRequest(
            prompt=compiled.compiled,model=model,references=refs,mask=mask,variants=variants,quality=quality,
            aspectRatio=aspectRatio,transparent=transparent,previousResponseId=previousContext,metadata=metadata
        )
        state.update{it.copy(provider=providerId.uppercase(),model=model,jobState="QUEUED",message="REQUEST ${req.id}")}
        val eng=requireNotNull(engine)
        eng.submit(providerId,req,edit,budget){result->
            result.onSuccess{res->
                runCatching{
                    val outputIds=mutableListOf<String>();val outputPaths=mutableListOf<Path>()
                    res.images.forEachIndexed{i,gi->
                        val ext=when(gi.mime){"image/jpeg"->"jpg";"image/webp"->"webp";else->"png"}
                        val file=pp.generated.resolve("${res.requestId}-${i+1}.$ext");Files.write(file,gi.bytes);outputPaths+=file
                        val im=ImageIO.read(file.toFile())
                        val a=ImageAsset(kind=AssetKind.GENERATED,path=pp.root.relativize(file).toString(),mime=gi.mime,width=im?.width?:0,height=im?.height?:0,sha256=sha256(gi.bytes),sourceAssetId=parentAsset,provenance="$providerId/$model")
                        assets[a.id]=a;db!!.saveAsset(a);outputIds+=a.id
                        val v=VersionNode(parentId=parentVersion,assetId=a.id,operation=if(edit)VersionOperation.EDIT else VersionOperation.GENERATE,name="${if(edit)"edit" else "generate"}.${i+1}",promptId=promptId,generationId=generationId)
                        graph.add(v);db!!.saveVersion(v)
                        if(i==0){project!!.currentVersionId=v.id;project!!.updatedAt=nowIso();db!!.saveProject(project!!)}
                    }
                    val endpoint=when{
                        providerId.equals("gemini",true)->"interactions"
                        metadata["openAiWorkflow"]?.equals("responses",true)==true->"responses"
                        edit->"images/edits"
                        else->"images/generations"
                    }
                    val settings=linkedMapOf<String,String>("quality" to (quality?:"auto"),"variants" to variants.toString()).apply{
                        aspectRatio?.let{put("aspectRatio",it)};putAll(metadata)
                    }
                    val retries=eng.snapshot().firstOrNull{it.payload["requestId"]==req.id}?.retryCount?:0
                    val contextId=res.rawMetadata["interactionId"]?.takeIf{it.isNotBlank()}?:res.rawMetadata["responseId"]?.takeIf{it.isNotBlank()}
                    val gen=GenerationRecord(
                        id=generationId,parentImageId=parentAsset,inputImageIds=refs.mapNotNull{parentAsset},maskId=if(mask==null)null else currentMaskId,
                        promptId=promptId,userPrompt=prompt,compiledPrompt=compiled.compiled,providerId=providerId,model=model,endpoint=endpoint,
                        settings=settings,outputImageIds=outputIds,durationMs=res.durationMs,retryCount=retries,workerId="GEN.WORKER",
                        providerRevisedPrompt=res.images.firstOrNull()?.revisedPrompt,providerContextId=contextId
                    )
                    generations+=gen;db!!.saveGeneration(gen)
                    val firstAsset=assets[outputIds.firstOrNull()]
                    val firstPath=outputPaths.firstOrNull()?.toString()
                    state.update{it.copy(imagePath=firstPath,imageWidth=firstAsset?.width?:0,imageHeight=firstAsset?.height?:0,currentVersion=project!!.currentVersionId?:"V---",versions=graph.all(),jobs=eng.snapshot(),jobState="COMPLETE",message="GENERATION COMPLETE / ${outputIds.size} OUTPUT(S)")}
                    outputPaths
                }.onSuccess{done(Result.success(it))}.onFailure{e->
                    state.update{it.copy(jobs=eng.snapshot(),jobState="FAILED",message="FAILED / ${e.message}")}
                    done(Result.failure(e))
                }
            }.onFailure{e->
                val cancelled=e is kotlinx.coroutines.CancellationException
                state.update{it.copy(jobs=eng.snapshot(),jobState=if(cancelled)"CANCELLED" else "FAILED",message="${if(cancelled)"CANCELLED" else "FAILED"} / ${e.message}")}
                done(Result.failure(e))
            }
        }
    }

    fun cancelActiveJobs(){
        val eng=engine?:return
        eng.snapshot().filter{it.state in setOf(JobState.QUEUED,JobState.RUNNING,JobState.WAITING)}.forEach{eng.cancel(it.id)}
        state.update{it.copy(jobs=eng.snapshot(),jobState="CANCELLED",message="ACTIVE JOBS CANCELLED")}
    }

    fun rotateCurrent(clockwise:Boolean=true){
        val src=requireCurrentImage();val w=src.width;val h=src.height
        val tx=if(clockwise)AffineTransform(0.0,1.0,-1.0,0.0,h.toDouble(),0.0) else AffineTransform(0.0,-1.0,1.0,0.0,0.0,w.toDouble())
        val op=AffineTransformOp(tx,AffineTransformOp.TYPE_BILINEAR)
        val out=BufferedImage(h,w,BufferedImage.TYPE_INT_ARGB);op.filter(src,out)
        persistDerived(out,VersionOperation.ROTATE,if(clockwise)"rotate.cw" else "rotate.ccw")
    }

    fun flipCurrent(horizontal:Boolean=true){
        val src=requireCurrentImage();val tx=if(horizontal)AffineTransform(-1.0,0.0,0.0,1.0,src.width.toDouble(),0.0) else AffineTransform(1.0,0.0,0.0,-1.0,0.0,src.height.toDouble())
        val out=BufferedImage(src.width,src.height,BufferedImage.TYPE_INT_ARGB)
        AffineTransformOp(tx,AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(src,out)
        persistDerived(out,VersionOperation.FLIP,if(horizontal)"flip.horizontal" else "flip.vertical")
    }

    fun cropCurrent(x:Int,y:Int,width:Int,height:Int){
        val src=requireCurrentImage();require(x>=0&&y>=0&&width>0&&height>0&&x+width<=src.width&&y+height<=src.height){"Crop rectangle is outside the image"}
        val out=BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);val g=out.createGraphics();g.drawImage(src,0,0,width,height,x,y,x+width,y+height,null);g.dispose()
        persistDerived(out,VersionOperation.CROP,"crop.${width}x$height")
    }

    private fun requireCurrentImage():BufferedImage=requireNotNull(ImageIO.read(Path.of(requireNotNull(state.get().imagePath){"No image loaded"}).toFile())){"Unsupported current image"}

    private fun persistDerived(image:BufferedImage,operation:VersionOperation,name:String):Path{
        val pp=requireNotNull(paths);val parent=currentVersionId();val source=assetForCurrent()
        val file=pp.generated.resolve("$name-${System.currentTimeMillis()}.png");ImageIO.write(image,"png",file.toFile())
        val bytes=Files.readAllBytes(file)
        val asset=ImageAsset(kind=AssetKind.GENERATED,path=pp.root.relativize(file).toString(),mime="image/png",width=image.width,height=image.height,sha256=sha256(bytes),sourceAssetId=source?.id,provenance="local-transform/$name")
        assets[asset.id]=asset;db!!.saveAsset(asset)
        val v=VersionNode(parentId=parent,assetId=asset.id,operation=operation,name=name);graph.add(v);db!!.saveVersion(v)
        project!!.currentVersionId=v.id;project!!.updatedAt=nowIso();db!!.saveProject(project!!)
        state.update{it.copy(imagePath=file.toString(),imageWidth=image.width,imageHeight=image.height,currentVersion=v.id,versions=graph.all(),message="$operation / $name")}
        return file
    }

    fun exportReport():Path{
        val pp=requireNotNull(paths);val out=pp.exports.resolve("project-report-${System.currentTimeMillis()}.html")
        val embedded=linkedMapOf<String,Pair<String,ByteArray>>()
        graph.all().takeLast(12).forEach{v->assets[v.assetId]?.let{a->val p=pp.root.resolve(a.path);if(Files.isRegularFile(p))embedded["${v.id} / ${v.name}"]=a.mime to Files.readAllBytes(p)}}
        state.get().maskPath?.let(Path::of)?.takeIf(Files::isRegularFile)?.let{embedded["CURRENT MASK"]="image/png" to Files.readAllBytes(it)}
        HtmlReportExporter.projectReport(project!!,graph.all(),generations,out,embedded)
        state.update{it.copy(message="REPORT EXPORTED / ${out.fileName}")};return out
    }

    fun exportDiagnostics():Path{
        val pp=requireNotNull(paths);val out=pp.exports.resolve("diagnostics-${System.currentTimeMillis()}.txt")
        val s=state.get()
        val body=buildString{
            appendLine("COSMOSIS IMAGE STUDIO / SANITIZED DIAGNOSTICS")
            appendLine("project=${project?.name?:"none"}");appendLine("projectId=${project?.id?:"none"}")
            appendLine("version=${s.currentVersion}");appendLine("image=${s.imageWidth}x${s.imageHeight}")
            appendLine("provider=${s.provider}");appendLine("model=${s.model}");appendLine("jobState=${s.jobState}")
            appendLine("versions=${graph.all().size}");appendLine("generations=${generations.size}")
            engine?.snapshot().orEmpty().forEach{appendLine("job ${it.id} ${it.type} ${it.state} retries=${it.retryCount} error=${it.error?:""}")}
        }
        Files.writeString(out,Redaction.sanitize(body));state.update{it.copy(message="DIAGNOSTICS EXPORTED / ${out.fileName}")};return out
    }

    fun setCurrentVersion(id:String){
        val v=requireNotNull(graph.get(id));project!!.currentVersionId=id;project!!.updatedAt=nowIso();db!!.saveProject(project!!)
        val a=assets[v.assetId];state.update{it.copy(currentVersion=id,imagePath=a?.let{paths!!.root.resolve(it.path).toString()}?:it.imagePath,imageWidth=a?.width?:it.imageWidth,imageHeight=a?.height?:it.imageHeight,message="VERSION $id")}
    }
    fun setCompareVersion(id:String?){val path=id?.let(graph::get)?.let{assets[it.assetId]}?.let{paths!!.root.resolve(it.path).toString()};state.update{it.copy(comparePath=path,compareMode=if(path==null)"OFF" else "SPLIT",message=if(path==null)"COMPARE OFF" else "COMPARE / $id")}}
    fun versionNodes()=graph.all()
    fun zoom(delta:Double){state.update{it.copy(zoom=(it.zoom*delta).coerceIn(.1,8.0))}}
    fun resetView(){state.update{it.copy(zoom=1.0,panX=0.0,panY=0.0)}}

    private fun seedPremade(){
        val seeds=listOf(
            PromptAsset(id="builtin-editorial-cover",title="Editorial / Magazine Cover",summary="Structured cover concept with subject, hierarchy, negative space, and typography zones.",body="Create an editorial magazine cover around {{subject}}. Preserve a clear subject silhouette, one dominant headline zone, restrained secondary copy, intentional negative space, publication-grade composition, and realistic material/lighting cues.",treePath="PREMADE PROMPTS/Editorial/Magazine Cover",origin=PromptOrigin.UPSTREAM,source="Cosmosis built-in recipe",sourceVersion="0.1.0",sourceLicense="MIT",readOnly=true,importedKeywords=setOf("editorial","poster","typography","cover")),
            PromptAsset(id="builtin-product-cutout",title="Product / Clean Cutout",summary="Product hero image with controllable background and crisp material detail.",body="Present {{product}} as a clean product hero. Preserve geometry and material texture. Use controlled studio light, deliberate contact shadow, uncluttered background, and sufficient negative space for layout.",treePath="PREMADE PROMPTS/Product/Clean Cutout",origin=PromptOrigin.UPSTREAM,source="Cosmosis built-in recipe",sourceVersion="0.1.0",sourceLicense="MIT",readOnly=true,importedKeywords=setOf("product","advertising","studio","cutout")),
            PromptAsset(id="builtin-style-dna",title="Reverse Prompt / Style DNA",summary="Separates reusable visual style from replaceable subject content.",body="Analyze the reference as reusable STYLE DNA. Separate {{subject}} from composition, camera/framing, lighting, palette, materials, typography, quality constraints, and avoid constraints. Keep subject content replaceable.",treePath="PREMADE PROMPTS/Image to Prompt/Style DNA",origin=PromptOrigin.UPSTREAM,source="Cosmosis built-in recipe; concept informed by image-2-reverse-prompt",sourceUrl="https://github.com/lusouldepth-ai/image-2-reverse-prompt",sourceVersion="audit-2026-09-27",sourceLicense="MIT",readOnly=true,importedKeywords=setOf("reverse-prompt","style-dna","reference"))
        )
        val existing=prompts.all(true).map{it.id}.toSet()
        seeds.filter{it.id !in existing}.forEach{seed->prompts=PromptLibrary(prompts.all(true)+seed,prompts.all(true).flatMap{prompts.revisions(it.id)})}
    }

    private fun currentVersionId()=project?.currentVersionId
    private fun assetForCurrent():ImageAsset?=currentVersionId()?.let(graph::get)?.let{assets[it.assetId]}
    private fun sync(message:String){state.update{it.copy(projectName=project?.name?:"NO PROJECT",projectRoot=project?.root?:"",currentVersion=project?.currentVersionId?:"V---",versions=graph.all(),jobs=engine?.snapshot().orEmpty(),message=message)}}
    private fun closeProject(){engine?.close();db?.close();engine=null;db=null;project=null;paths=null;assets.clear();generations.clear();currentMaskId=null;prompts=PromptLibrary();graph=VersionGraph()}
    override fun close(){closeProject()}
    private fun readProjectJson(p:Path):Map<String,String>{if(!Files.exists(p))return emptyMap();val t=Files.readString(p);return Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(t).associate{it.groupValues[1] to it.groupValues[2]}}
    private fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
