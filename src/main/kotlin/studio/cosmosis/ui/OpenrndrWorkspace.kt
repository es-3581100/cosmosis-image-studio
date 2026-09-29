package studio.cosmosis.ui

import org.openrndr.*
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.loadFont
import org.openrndr.draw.loadImage
import org.openrndr.math.IntVector2
import org.openrndr.math.Vector2
import studio.cosmosis.JobState
import studio.cosmosis.lineage.VersionGraphLayout
import studio.cosmosis.theme.OffworldTheme
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.math.min

private fun java.awt.Color.toOrColor()=ColorRGBa(red/255.0,green/255.0,blue/255.0,alpha/255.0)
private val BG=OffworldTheme.background.toOrColor()
private val FG=OffworldTheme.foreground.toOrColor()
private val MUTED=OffworldTheme.muted.toOrColor()
private val SECOND=OffworldTheme.secondary.toOrColor()
private val GREEN=OffworldTheme.positive.toOrColor()
private val RED=OffworldTheme.destructive.toOrColor()
private val SURFACE=ColorRGBa(21/255.0,21/255.0,17/255.0,1.0)

data class WorkspaceSmokeConfig(
    val frames:Int=20,
    val reportPath:Path=Path.of("build/ui-smoke/report.txt"),
    val screenshotPath:Path=Path.of("build/ui-smoke/workstation.png")
)

private fun near(rgb:Int,target:java.awt.Color,tolerance:Int=14):Boolean {
    val r=(rgb shr 16) and 255;val g=(rgb shr 8) and 255;val b=rgb and 255
    return kotlin.math.abs(r-target.red)<=tolerance && kotlin.math.abs(g-target.green)<=tolerance && kotlin.math.abs(b-target.blue)<=tolerance
}

private fun brightNeutral(rgb:Int):Boolean {
    val r=(rgb shr 16) and 255;val g=(rgb shr 8) and 255;val b=rgb and 255
    return r>=238&&g>=238&&b>=238&&(maxOf(r,g,b)-minOf(r,g,b)<=12)
}

private fun sha256(path:Path):String=MessageDigest.getInstance("SHA-256")
    .digest(Files.readAllBytes(path)).joinToString(""){"%02x".format(it)}

private fun writeWorkspaceSmoke(config:WorkspaceSmokeConfig,dock:ControlDock,frame:Long,workspaceWidth:Int,workspaceHeight:Int):Boolean{
    config.reportPath.parent?.let{Files.createDirectories(it)}
    config.screenshotPath.parent?.let{Files.createDirectories(it)}
    val errors=mutableListOf<String>()
    val size=Toolkit.getDefaultToolkit().screenSize
    if(size.width<=0||size.height<=0)errors+="virtual display has invalid dimensions"
    if(!dock.isShowing)errors+="Swing control dock is not showing"
    var dark=0L;var ivory=0L;var sampled=0L;var screenshotHash="unavailable"
    var dockSampled=0L;var dockDark=0L;var dockBrightNeutral=0L
    runCatching{
        val capture=Robot().createScreenCapture(Rectangle(size))
        if(!ImageIO.write(capture,"png",config.screenshotPath.toFile()))error("Unable to encode UI smoke screenshot")
        for(y in 0 until capture.height step 3)for(x in 0 until capture.width step 3){
            val rgb=capture.getRGB(x,y);sampled++
            if(near(rgb,OffworldTheme.background))dark++
            if(near(rgb,OffworldTheme.foreground))ivory++
        }
        val db=dock.bounds
        val x0=db.x.coerceAtLeast(0);val y0=db.y.coerceAtLeast(0)
        val x1=(db.x+db.width).coerceAtMost(capture.width);val y1=(db.y+db.height).coerceAtMost(capture.height)
        for(y in y0 until y1 step 3)for(x in x0 until x1 step 3){
            val rgb=capture.getRGB(x,y);dockSampled++
            if(near(rgb,OffworldTheme.background,22)||near(rgb,Color(0x13,0x13,0x10),22)||near(rgb,Color(0x1C,0x1C,0x18),22))dockDark++
            if(brightNeutral(rgb))dockBrightNeutral++
        }
        screenshotHash=sha256(config.screenshotPath)
    }.onFailure{errors+="screenshot: "+(it.message?:it.javaClass.simpleName)}
    if(OffworldTheme.radius!=0)errors+="Offworld hard-corner authority regressed"
    if(dark<=1000)errors+="Offworld warm-dark surface not visible enough (count=$dark)"
    if(ivory<=10)errors+="Offworld ivory foreground not visible enough (count=$ivory)"
    if(dockSampled<=1000)errors+="Control dock capture too small to evaluate"
    if(dockSampled>0&&dockDark*100<dockSampled*45)errors+="Control dock is not predominantly Offworld-dark (dark=$dockDark sampled=$dockSampled)"
    if(dockSampled>0&&dockBrightNeutral*100>dockSampled*3)errors+="Control dock contains too much bright neutral native chrome (bright=$dockBrightNeutral sampled=$dockSampled)"
    val passed=errors.isEmpty()
    val report=buildString{
        appendLine(if(passed)"UI_SMOKE_PASS" else "UI_SMOKE_FAIL")
        appendLine("frame=$frame")
        appendLine("workspace=${workspaceWidth}x$workspaceHeight")
        appendLine("desktop=${size.width}x${size.height}")
        appendLine("dock.showing=${dock.isShowing}")
        appendLine("theme.background=#10100E")
        appendLine("theme.foreground=#FFFFE3")
        appendLine("theme.radius=${OffworldTheme.radius}")
        appendLine("sampled=$sampled")
        appendLine("warmDarkPixels=$dark")
        appendLine("ivoryPixels=$ivory")
        appendLine("dock.bounds=${dock.x},${dock.y},${dock.width},${dock.height}")
        appendLine("dock.sampled=$dockSampled")
        appendLine("dock.darkPixels=$dockDark")
        appendLine("dock.brightNeutralPixels=$dockBrightNeutral")
        appendLine("screenshot=${config.screenshotPath}")
        appendLine("screenshot.sha256=$screenshotHash")
        errors.forEach{appendLine("error=$it")}
    }
    Files.writeString(config.reportPath,report)
    return passed
}

fun launchWorkspace(state:StudioState,controller:StudioController,dock:ControlDock,smoke:WorkspaceSmokeConfig?=null)=application {
    val desktop=Toolkit.getDefaultToolkit().screenSize
    val rightOfDock=dock.x+dock.width+20
    val tileBesideDock=desktop.width>=rightOfDock+960
    val workspaceX=if(tileBesideDock)rightOfDock else 20
    val workspaceY=if(tileBesideDock)dock.y.coerceAtLeast(20) else 20
    val workspaceWidth=if(tileBesideDock)minOf(1480,desktop.width-workspaceX-20) else minOf(1480,(desktop.width-40).coerceAtLeast(640))
    val workspaceHeight=minOf(900,(desktop.height-workspaceY-20).coerceAtLeast(640))
    configure { width=workspaceWidth;height=workspaceHeight;title="COSMOSIS / IMAGE STUDIO";position=IntVector2(workspaceX,workspaceY) }
    program {
        val monoPath=listOf("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf","/usr/share/fonts/truetype/liberation2/LiberationMono-Regular.ttf").firstOrNull{Files.exists(Path.of(it))}
        val serifPath=listOf("/usr/share/fonts/truetype/dejavu/DejaVuSerif.ttf","/usr/share/fonts/truetype/liberation2/LiberationSerif-Regular.ttf").firstOrNull{Files.exists(Path.of(it))}
        val mono=monoPath?.let{loadFont(it,13.0)};val monoSmall=monoPath?.let{loadFont(it,10.0)};val serif=serifPath?.let{loadFont(it,24.0)}
        var loadedPath:String?=null;var image:ColorBuffer?=null;var compareLoaded:String?=null;var compareImage:ColorBuffer?=null;var maskLoaded:String?=null;var mask:ColorBuffer?=null
        var pan=Vector2.ZERO;var zoom=1.0;var lastDrag:Vector2?=null
        var smokeDone=false
        window.drop.listen { dropped -> dropped.files.firstOrNull{File(it).extension.lowercase() in listOf("png","jpg","jpeg","webp") }?.let{runCatching{controller.importImage(Path.of(it))}} }
        mouse.scrolled.listen { zoom=(zoom*(if(it.rotation.y<0)1.1 else .9)).coerceIn(.1,8.0);state.update{s->s.copy(zoom=zoom)} }
        mouse.buttonDown.listen { lastDrag=it.position }
        mouse.buttonUp.listen { lastDrag=null }
        mouse.dragged.listen { val prev=lastDrag?:it.position;pan += it.position-prev;lastDrag=it.position;state.update{s->s.copy(panX=pan.x,panY=pan.y)} }
        keyboard.keyDown.listen { ev -> val ctrl=ev.modifiers.any { it.name=="CTRL" || it.name=="META" || it.name=="SUPER" };when {
            ctrl && ev.name=="k" -> SwingUtilities.invokeLater{dock.openCommandPalette()}
            ctrl && ev.name=="enter" -> { val s=state.get();if(s.promptBody.isNotBlank())runCatching{controller.generate(s.promptBody,s.provider.lowercase(),s.model)} }
            ev.name=="g" -> controller.setWorkflowMode(studio.cosmosis.WorkflowMode.QUICK_GENERATE)
            ev.name=="e" -> controller.setWorkflowMode(studio.cosmosis.WorkflowMode.EDIT_EXISTING)
            ev.name=="m" -> controller.setWorkflowMode(studio.cosmosis.WorkflowMode.MASK_EDIT)
            ev.name=="h" -> controller.setMaskVisible(!state.get().maskVisible)
            ev.name=="c" -> state.update{it.copy(compareMode=if(it.compareMode=="OFF"&&it.comparePath!=null)"SPLIT" else "OFF",message="COMPARE / "+if(it.compareMode=="OFF"&&it.comparePath!=null)"SPLIT" else "OFF")}
            ev.name=="0" -> {pan=Vector2.ZERO;zoom=1.0;controller.resetView()}
        } }
        extend {
            val s=state.get();drawer.clear(BG)
            // quiet technical grid
            drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.035);drawer.strokeWeight=1.0
            for(x in 82 until width-300 step 34)drawer.lineSegment(x.toDouble(),72.0,x.toDouble(),height-170.0)
            for(y in 72 until height-170 step 34)drawer.lineSegment(82.0,y.toDouble(),(width-300).toDouble(),y.toDouble())
            // workstation planes
            drawer.fill=SURFACE;drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.12);drawer.rectangle(0.0,0.0,width.toDouble(),72.0);drawer.rectangle(0.0,72.0,82.0,(height-72).toDouble());drawer.rectangle((width-300).toDouble(),72.0,300.0,(height-72).toDouble());drawer.rectangle(82.0,(height-170).toDouble(),(width-382).toDouble(),120.0);drawer.rectangle(82.0,(height-50).toDouble(),(width-82).toDouble(),50.0)
            if(mono!=null){drawer.fontMap=mono;drawer.fill=FG;drawer.text("COSMOSIS / IMAGE STUDIO",21.0,30.0);drawer.fontMap=monoSmall!!;drawer.fill=MUTED;drawer.text("${s.projectName}  /  ${s.currentVersion}",21.0,52.0);drawer.text("${s.provider} / ${s.model}",(width-560).toDouble(),30.0);drawer.fill=if(s.jobState=="FAILED")RED else if(s.jobState=="COMPLETE")GREEN else FG;drawer.text("JOB ${s.jobState}",(width-180).toDouble(),30.0)}
            val tools=listOf("SEL","MASK","CROP","AI","FIT","CMP");if(monoSmall!=null){drawer.fontMap=monoSmall;tools.forEachIndexed{i,t->drawer.fill=if(t==s.selectedTool)FG else MUTED;drawer.text(t,23.0,118.0+i*54)}}
            // image field / split compare instrument
            s.imagePath?.let { p ->
                if(p!=loadedPath){runCatching{image?.destroy();image=loadImage(p);loadedPath=p}}
                image?.let{img->
                    val fieldX=103.0;val fieldY=93.0;val fieldW=width-424.0;val fieldH=height-290.0
                    val comparePath=s.comparePath.takeIf{s.compareMode=="SPLIT"}
                    if(comparePath!=null){
                        if(comparePath!=compareLoaded){runCatching{compareImage?.destroy();compareImage=loadImage(comparePath);compareLoaded=comparePath}}
                        val gap=13.0;val half=(fieldW-gap)/2.0
                        val fitA=min(half/img.width,fieldH/img.height);val aw=img.width*fitA*zoom;val ah=img.height*fitA*zoom;val ax=fieldX+(half-aw)/2+pan.x;val ay=fieldY+(fieldH-ah)/2+pan.y
                        drawer.image(img,ax,ay,aw,ah)
                        compareImage?.let{cmp->val fitB=min(half/cmp.width,fieldH/cmp.height);val bw=cmp.width*fitB*zoom;val bh=cmp.height*fitB*zoom;val bx=fieldX+half+gap+(half-bw)/2+pan.x;val by=fieldY+(fieldH-bh)/2+pan.y;drawer.image(cmp,bx,by,bw,bh)}
                        drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.18);drawer.lineSegment(fieldX+half+gap/2,fieldY,fieldX+half+gap/2,fieldY+fieldH)
                        if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("CURRENT",fieldX,fieldY+14);drawer.text("COMPARE",fieldX+half+gap,fieldY+14)}
                        Unit
                    } else {
                        val fit=min(fieldW/img.width,fieldH/img.height);val dw=img.width*fit*zoom;val dh=img.height*fit*zoom;val x=fieldX+(fieldW-dw)/2+pan.x;val y=fieldY+(fieldH-dh)/2+pan.y
                        drawer.image(img,x,y,dw,dh);drawer.fill=null;drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.55);val m=12.0;drawer.lineSegment(x,y,x+m,y);drawer.lineSegment(x,y,x,y+m);drawer.lineSegment(x+dw,y,x+dw-m,y);drawer.lineSegment(x+dw,y,x+dw,y+m);drawer.lineSegment(x,y+dh,x+m,y+dh);drawer.lineSegment(x,y+dh,x,y+dh-m);drawer.lineSegment(x+dw,y+dh,x+dw-m,y+dh);drawer.lineSegment(x+dw,y+dh,x+dw,y+dh-m)
                        s.maskOverlayPath?.takeIf{s.maskVisible}?.let{mp->if(mp!=maskLoaded){runCatching{mask?.destroy();mask=loadImage(mp);maskLoaded=mp}};mask?.let{drawer.image(it,x,y,dw,dh)}}
                        if(s.analysisVisible&&s.analysisRegions.isNotEmpty()){
                            drawer.fill=null;drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.62);drawer.strokeWeight=1.0
                            s.analysisRegions.take(80).forEachIndexed{index,r->
                                val rx=x+(r.x.toDouble()/img.width)*dw;val ry=y+(r.y.toDouble()/img.height)*dh
                                val rw=(r.width.toDouble()/img.width)*dw;val rh=(r.height.toDouble()/img.height)*dh
                                drawer.rectangle(rx,ry,rw,rh)
                                if(monoSmall!=null&&index<14){drawer.fontMap=monoSmall;drawer.fill=FG;drawer.text((if(r.kind=="ocr")"TXT" else "ROI")+" / "+r.label.take(22),rx+3,ry+12);drawer.fill=null}
                            }
                        }
                        Unit
                    }
                }
            } ?: run {
                if(serif!=null){drawer.fontMap=serif;drawer.fill=FG;drawer.text("No image loaded",width/2.0-100,height/2.0-15)}
                if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("DROP IMAGE HERE  /  or use Control → Import Image",width/2.0-150,height/2.0+15)}
            }
            // inspector
            if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("// INSPECTOR",(width-278).toDouble(),105.0);drawer.fill=FG;drawer.text("PROMPT",(width-278).toDouble(),140.0);drawer.fill=MUTED;val preview=s.promptBody.replace('\n',' ').take(220);preview.chunked(34).take(6).forEachIndexed{i,line->drawer.text(line,(width-278).toDouble(),164.0+i*16)};drawer.fill=FG;drawer.text("REFERENCES",(width-278).toDouble(),285.0);drawer.fill=MUTED;drawer.text((if(s.imagePath==null)0 else 1).toString()+" source + "+s.referencePaths.size+" attached",(width-278).toDouble(),307.0);drawer.fill=FG;drawer.text("WORKFLOW",(width-278).toDouble(),333.0);drawer.fill=MUTED;drawer.text(s.workflowMode.name,(width-278).toDouble(),351.0);drawer.fill=FG;drawer.text("MASK",(width-278).toDouble(),382.0);drawer.fill=if(s.maskPath==null)MUTED else GREEN;drawer.text(if(s.maskPath==null)"○ OFF" else if(s.maskVisible)"● ACTIVE / VISIBLE" else "● ACTIVE / HIDDEN",(width-278).toDouble(),404.0);drawer.fill=FG;drawer.text("ANALYSIS",(width-278).toDouble(),446.0);drawer.fill=if(s.analysisVisible&&s.analysisRegions.isNotEmpty())FG else MUTED;drawer.text(if(s.analysisRegions.isEmpty())"○ NONE" else (if(s.analysisVisible)"● "+s.analysisRegions.size+" REGION(S)" else "● HIDDEN"),(width-278).toDouble(),468.0);drawer.fill=FG;drawer.text("WORKERS",(width-278).toDouble(),505.0);s.jobs.takeLast(7).forEachIndexed{i,j->drawer.fill=if(j.state==JobState.COMPLETE)GREEN else if(j.state==JobState.FAILED)RED else MUTED;drawer.text("${j.type.take(16)}  ${j.state}",(width-278).toDouble(),529.0+i*18)} }
            // branching lineage instrument: geometry communicates branches instead of color.
            if(monoSmall!=null){
                drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("// VERSION GRAPH",103.0,(height-145).toDouble())
                val nodes=s.versions.takeLast(48)
                val layout=VersionGraphLayout.layout(nodes)
                val byId=layout.associateBy{it.id}
                val gx0=110.0;val gx1=(width-338).toDouble();val gy0=(height-128).toDouble();val gy1=(height-67).toDouble()
                drawer.stroke=ColorRGBa(1.0,1.0,227/255.0,.22);drawer.strokeWeight=1.0
                layout.forEach{pt->pt.parentId?.let{pid->byId[pid]?.let{parent->
                    val px=gx0+(gx1-gx0)*parent.x;val py=gy0+(gy1-gy0)*parent.y
                    val cx=gx0+(gx1-gx0)*pt.x;val cy=gy0+(gy1-gy0)*pt.y
                    val elbow=(px+cx)/2.0;drawer.lineSegment(px,py,elbow,py);drawer.lineSegment(elbow,py,elbow,cy);drawer.lineSegment(elbow,cy,cx,cy)
                }}}
                layout.forEach{pt->val x=gx0+(gx1-gx0)*pt.x;val y=gy0+(gy1-gy0)*pt.y;val active=pt.id==s.currentVersion;drawer.stroke=if(active)FG else ColorRGBa(1.0,1.0,227/255.0,.28);drawer.fill=if(active)FG else SURFACE;drawer.rectangle(x-4,y-4,8.0,8.0);if(active){drawer.fill=FG;drawer.text(pt.id.takeLast(6),x+8,y+4)}}
            }
            if(monoSmall!=null){drawer.fontMap=monoSmall;drawer.fill=MUTED;drawer.text("${s.imageWidth}×${s.imageHeight}  │  ${"%.0f".format(zoom*100)}%  │  ${s.workflowMode.name}  │  REF ${s.referencePaths.size}  │  MASK ${if(s.maskPath==null)"off" else if(s.maskVisible)"visible" else "hidden"}  │  ${s.message}",103.0,(height-20).toDouble())}
            if(smoke!=null&&!smokeDone&&frameCount.toLong()>=smoke.frames.toLong()){
                smokeDone=true
                runCatching{writeWorkspaceSmoke(smoke,dock,frameCount.toLong(),width,height)}
                    .onFailure{e->
                        smoke.reportPath.parent?.let{Files.createDirectories(it)}
                        Files.writeString(smoke.reportPath,"UI_SMOKE_FAIL\nerror="+(e.message?:e.javaClass.simpleName)+"\n")
                    }
                SwingUtilities.invokeLater{dock.dispose()}
                application.exit()
            }
        }
    }
}
