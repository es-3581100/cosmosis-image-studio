package studio.cosmosis.ui

import studio.cosmosis.*
import studio.cosmosis.provider.ModelRegistry
import studio.cosmosis.theme.OffworldTheme
import java.awt.*
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.UndoableEditEvent
import javax.swing.event.UndoableEditListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.undo.UndoManager

class ControlDock(private val controller:StudioController,private val state:StudioState):JFrame("COSMOSIS / CONTROL") {
    private val prompt=JTextArea(14,40)
    private val promptTitle=JTextField("Untitled prompt")
    private val treePath=JTextField("MY PROMPTS/General")
    private val promptSummary=JTextArea(2,22).apply{lineWrap=true;wrapStyleWord=true}
    private val negativeConstraints=JTextArea(3,22).apply{lineWrap=true;wrapStyleWord=true}
    private val explicitKeywords=JTextField()
    private val styleTags=JTextField()
    private val subjectTags=JTextField()
    private val compositionTags=JTextField()
    private val lightingTags=JTextField()
    private val cameraTags=JTextField()
    private val materialTags=JTextField()
    private val promptVariables=JTextArea(4,22).apply{lineWrap=true;wrapStyleWord=true}
    private val promptNotes=JTextArea(4,22).apply{lineWrap=true;wrapStyleWord=true}
    private val promptFavorite=JCheckBox("★ FAVORITE").apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private val provider=JComboBox(arrayOf("local","openai","gemini","litellm","custom"))
    private val model=JComboBox<String>().apply{isEditable=true}
    private val workflowMode=JComboBox(WorkflowMode.entries.toTypedArray())
    private val outputFormat=JComboBox(arrayOf("png","jpeg","webp"))
    private val outputWidth=JTextField("")
    private val outputHeight=JTextField("")
    private val status=JLabel("READY")
    private val localTree=JTree(DefaultMutableTreeNode("MY PROMPTS"))
    private val premadeTree=JTree(DefaultMutableTreeNode("PREMADE PROMPTS"))
    private val versionList=JList<VersionRef>()
    private val workers=JTextArea()
    private val workerList=JList<JobRef>()
    private val referenceList=JList<ReferenceRef>()
    private val directiveList=JList<DirectiveRef>()
    private val directiveTitle=JTextField()
    private val directiveBody=JTextArea(14,30).apply{lineWrap=true;wrapStyleWord=true}
    private val directiveEnabled=JCheckBox("ENABLED").apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private var selectedDirectiveId:String?=null
    private val maskVisible=JCheckBox("MASK OVERLAY VISIBLE",true).apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private val maskColor=JTextField("#EF4444")
    private val maskOpacity=JSpinner(SpinnerNumberModel(42,5,100,1))
    private val motionLevel=JComboBox(arrayOf("off","subtle","normal"))
    private val uiDensity=JComboBox(arrayOf("compact","comfortable","spacious"))
    private val reducedMotion=JCheckBox("REDUCED MOTION").apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private val undo=UndoManager()
    private val searchScope=JComboBox(arrayOf("ALL","MY PROMPTS","PREMADE PROMPTS"))
    private val capabilityLabel=JTextArea(5,36).apply{isEditable=false;lineWrap=true;wrapStyleWord=true}
    private val variants=JSpinner(SpinnerNumberModel(1,1,10,1))
    private val quality=JComboBox(arrayOf("auto","preview","low","medium","high","xhigh","max")).apply{isEditable=true}
    private val aspectRatio=JTextField("")
    private val imageSize=JComboBox(arrayOf("","0.5K","1K","2K","4K")).apply{isEditable=true}
    private val thinkingLevel=JComboBox(arrayOf("","minimal","low","medium","high")).apply{isEditable=true}
    private val searchGrounding=JCheckBox("SEARCH GROUNDING").apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private val transparentOutput=JCheckBox("TRANSPARENT").apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private val providerWorkflow=JComboBox(arrayOf("direct","responses"))
    private val reasoningModel=JTextField("").apply{toolTipText="Required for OpenAI Responses image workflow; can also use OPENAI_RESPONSES_MODEL"}
    private var dirty=false
    private var selectedPromptId:String?=null
    private var premadeVisible=true

    init {
        applyTheme();defaultCloseOperation=DO_NOTHING_ON_CLOSE;layout=BorderLayout();minimumSize=Dimension(720,780);preferredSize=Dimension(880,920)
        val tabs=JTabbedPane();tabs.addTab("PROMPT",promptPanel());tabs.addTab("LIBRARY",libraryPanel());tabs.addTab("REFERENCES",referencePanel());tabs.addTab("VERSIONS",versionPanel());tabs.addTab("WORKERS",workerPanel());tabs.addTab("DIRECTIVES",directivePanel());tabs.addTab("SETTINGS",settingsPanel());add(tabs,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);status.border=BorderFactory.createEmptyBorder(8,13,8,13)
        prompt.document.addUndoableEditListener(UndoableEditListener{e:UndoableEditEvent->undo.addEdit(e.edit)})
        prompt.document.addDocumentListener(object:DocumentListener{override fun insertUpdate(e:DocumentEvent)=mark();override fun removeUpdate(e:DocumentEvent)=mark();override fun changedUpdate(e:DocumentEvent)=mark();fun mark(){dirty=true;status.text="UNSAVED PROMPT"}})
        provider.addActionListener{refreshModels()};workflowMode.addActionListener{(workflowMode.selectedItem as? WorkflowMode)?.let(controller::setWorkflowMode)};refreshModels()
        state.listen { s -> SwingUtilities.invokeLater {
            status.text=s.message;workers.text=s.jobs.joinToString("\n"){"${it.id.take(12)}  ${it.state}  ${it.type}  retry=${it.retryCount}"}
            workerList.setListData(s.jobs.map{JobRef(it.id,it.id.take(12)+" / "+it.state+" / "+it.type+" / retry="+it.retryCount)}.toTypedArray())
            if(!prompt.hasFocus() && !dirty && s.promptBody.isNotBlank()){prompt.text=s.promptBody;promptTitle.text=s.promptTitle}
            if(workflowMode.selectedItem!=s.workflowMode)workflowMode.selectedItem=s.workflowMode
            maskVisible.isSelected=s.maskVisible;maskColor.text=s.maskOverlayColor;maskOpacity.value=(s.maskOverlayOpacity*100).toInt()
            reducedMotion.isSelected=s.reducedMotion;motionLevel.selectedItem=s.motionLevel;uiDensity.selectedItem=s.uiDensity
            refreshTrees();refreshReferences();refreshVersions();refreshDirectives()
        } }
        installKeys();pack();setLocation(30,70);isVisible=true
    }

    private fun promptPanel():JPanel = panel().apply {
        layout=BorderLayout(0,8)
        val head=panel().apply{layout=GridLayout(0,1,0,5);add(label("CURRENT / TREE / PROMPT"));add(promptTitle);add(label("BRANCH PATH"));add(treePath)}
        val generation=panel(GridLayout(0,4,8,5)).apply{add(label("PROVIDER"));add(provider);add(label("MODEL"));add(model);add(label("WORKFLOW"));add(workflowMode);add(label("VARIANTS"));add(variants);add(label("QUALITY"));add(quality);add(label("OUTPUT FORMAT"));add(outputFormat);add(label("ASPECT RATIO"));add(aspectRatio);add(label("IMAGE SIZE"));add(imageSize);add(label("WIDTH"));add(outputWidth);add(label("HEIGHT"));add(outputHeight);add(searchGrounding);add(transparentOutput);add(label("THINKING"));add(thinkingLevel);add(label("PROVIDER FLOW"));add(providerWorkflow);add(label("RESPONSES MODEL"));add(reasoningModel)}
        val north=panel(BorderLayout(0,8)).apply{add(head,BorderLayout.NORTH);add(generation,BorderLayout.SOUTH)}
        add(north,BorderLayout.NORTH);prompt.lineWrap=true;prompt.wrapStyleWord=true
        val meta=panel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);preferredSize=Dimension(260,620);add(label("SUMMARY"));add(JScrollPane(promptSummary));add(label("NEGATIVE / AVOID"));add(JScrollPane(negativeConstraints));add(label("EXPLICIT KEYWORDS"));add(explicitKeywords);add(label("STYLE TAGS"));add(styleTags);add(label("SUBJECT TAGS"));add(subjectTags);add(label("COMPOSITION TAGS"));add(compositionTags);add(label("LIGHTING TAGS"));add(lightingTags);add(label("CAMERA TAGS"));add(cameraTags);add(label("MATERIAL TAGS"));add(materialTags);add(label("VARIABLES  key=value"));add(JScrollPane(promptVariables));add(label("NOTES"));add(JScrollPane(promptNotes));add(promptFavorite)}
        val split=JSplitPane(JSplitPane.HORIZONTAL_SPLIT,JScrollPane(prompt),JScrollPane(meta)).apply{resizeWeight=.70;dividerLocation=520;border=null}
        add(split,BorderLayout.CENTER)
        val actions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(actions,"＋ NEW"){newPrompt()};button(actions,"SAVE / UPDATE"){savePrompt()};button(actions,"UNDO"){if(undo.canUndo())undo.undo()};button(actions,"REDO"){if(undo.canRedo())undo.redo()};button(actions,"RUN MODE"){runSelectedWorkflow()};button(actions,"QUICK"){workflowMode.selectedItem=WorkflowMode.QUICK_GENERATE;runSelectedWorkflow()};button(actions,"EDIT"){workflowMode.selectedItem=WorkflowMode.EDIT_EXISTING;runSelectedWorkflow()};button(actions,"IMAGE → PROMPT"){openImageToPrompt()};button(actions,"PLAN AGENT"){val text=JOptionPane.showInputDialog(this@ControlDock,"Intent","Agent Build",JOptionPane.PLAIN_MESSAGE)?:return@button;JOptionPane.showMessageDialog(this@ControlDock,controller.planAgentBuild(text),"DIRECTOR PLAN",JOptionPane.INFORMATION_MESSAGE)};button(actions,"RUN AGENT"){runAgentBuildDialog()}
        add(actions,BorderLayout.SOUTH)
    }

    private fun libraryPanel():JPanel=panel().apply {
        layout=BorderLayout(0,8)
        val top=panel(BorderLayout(5,5));val q=JTextField();top.add(q,BorderLayout.CENTER);top.add(searchScope,BorderLayout.EAST);add(top,BorderLayout.NORTH)
        val split=JSplitPane(JSplitPane.HORIZONTAL_SPLIT,JScrollPane(localTree),JScrollPane(premadeTree)).apply{resizeWeight=.55;dividerLocation=280;border=null}
        add(split,BorderLayout.CENTER)
        fun bind(tree:JTree){tree.addTreeSelectionListener{val ref=(tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? NodeRef?:return@addTreeSelectionListener;loadPrompt(ref.id)}}
        bind(localTree);bind(premadeTree)
        val actions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(actions,"SEARCH"){refreshTrees(q.text)};button(actions,"PREMADE ▸"){premadeVisible=!premadeVisible;if(premadeVisible){split.rightComponent=JScrollPane(premadeTree);split.dividerSize=8;split.dividerLocation=280}else{split.rightComponent=JPanel();split.dividerSize=0;split.setDividerLocation(1.0)}}
        button(actions,"COPY PREMADE"){selectedNodeId(premadeTree)?.let{runCatching{controller.copyUpstream(it)}.onSuccess{loadPrompt(it.id);refreshTrees()}.onFailure(::showError)}}
        button(actions,"DUPLICATE"){selectedPromptId?.let{runCatching{controller.duplicatePrompt(it)}.onSuccess{p->loadPrompt(p.id);refreshTrees()}.onFailure(::showError)}}
        button(actions,"MOVE"){val id=selectedPromptId?:return@button;val p=JOptionPane.showInputDialog(this@ControlDock,"New tree path",treePath.text)?:return@button;runCatching{controller.movePrompt(id,p)}.onSuccess{refreshTrees()}.onFailure(::showError)}
        button(actions,"TRASH"){selectedPromptId?.let{runCatching{controller.trashPrompt(it);newPrompt();refreshTrees()}.onFailure(::showError)}}
        button(actions,"IMPORT"){importPrompt()};button(actions,"EXPORT"){exportPrompt()}
        add(actions,BorderLayout.SOUTH)
    }

    private fun referencePanel():JPanel=panel().apply{
        layout=BorderLayout(0,8)
        add(label("ACTIVE REFERENCE IMAGES / provider capability limits still apply"),BorderLayout.NORTH)
        referenceList.selectionMode=ListSelectionModel.SINGLE_SELECTION;add(JScrollPane(referenceList),BorderLayout.CENTER)
        val actions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(actions,"＋ ADD"){chooseReference()};button(actions,"REMOVE"){referenceList.selectedValue?.let{controller.removeReferenceImage(it.id)}}
        button(actions,"CLEAR"){controller.clearReferenceImages()}
        add(actions,BorderLayout.SOUTH)
    }

    private fun directivePanel():JPanel=panel().apply{
        layout=BorderLayout(0,8)
        directiveList.selectionMode=ListSelectionModel.SINGLE_SELECTION
        directiveList.addListSelectionListener{if(!it.valueIsAdjusting)directiveList.selectedValue?.let{x->loadDirective(x.id)}}
        val editor=panel().apply{layout=BoxLayout(this,BoxLayout.Y_AXIS);add(label("TITLE"));add(directiveTitle);add(label("DIRECTIVE BODY"));add(JScrollPane(directiveBody));add(directiveEnabled)}
        val split=JSplitPane(JSplitPane.HORIZONTAL_SPLIT,JScrollPane(directiveList),editor).apply{resizeWeight=.35;dividerLocation=240;border=null}
        add(split,BorderLayout.CENTER)
        val actions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(actions,"＋ NEW"){newDirective()};button(actions,"SAVE"){saveDirective()};button(actions,"DELETE"){selectedDirectiveId?.let{controller.deleteDirective(it);newDirective();refreshDirectives()}}
        add(actions,BorderLayout.SOUTH)
    }

    private fun versionPanel():JPanel=panel().apply {
        layout=BorderLayout(0,8);versionList.selectionMode=ListSelectionModel.SINGLE_SELECTION;add(JScrollPane(versionList),BorderLayout.CENTER)
        val actions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(actions,"OPEN VERSION"){versionList.selectedValue?.let{controller.setCurrentVersion(it.id)}}
        button(actions,"COMPARE"){versionList.selectedValue?.let{controller.setCompareVersion(it.id)}};button(actions,"COMPARE OFF"){controller.setCompareVersion(null)}
        button(actions,"RENAME"){val v=versionList.selectedValue?:return@button;val name=JOptionPane.showInputDialog(this@ControlDock,"Version name",v.label.substringAfterLast(" / "))?:return@button;runCatching{controller.renameVersion(v.id,name)}.onFailure(::showError)}
        button(actions,"★ FAVORITE"){versionList.selectedValue?.let{runCatching{controller.toggleVersionFavorite(it.id)}.onFailure(::showError)}}
        button(actions,"EXPORT CURRENT"){exportCurrentImage()}
        add(actions,BorderLayout.SOUTH)
    }

    private fun workerPanel():JPanel=panel().apply{
        layout=BorderLayout(0,8);workerList.selectionMode=ListSelectionModel.SINGLE_SELECTION;add(JScrollPane(workerList),BorderLayout.CENTER)
        val actions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(actions,"RESUME SELECTED"){workerList.selectedValue?.let{runCatching{controller.resumeJob(it.id)}.onSuccess{status.text="RESUME QUEUED / "+it.id}.onFailure(::showError)}}
        button(actions,"CANCEL ACTIVE"){runCatching{controller.cancelActiveJobs()}.onSuccess{n->status.text=if(n==0)"NO ACTIVE JOBS" else "CANCEL REQUESTED / "+n+" JOB(S)"}.onFailure(::showError)}
        add(actions,BorderLayout.SOUTH)
    }

    private fun settingsPanel():JPanel=panel().apply {
        layout=BoxLayout(this,BoxLayout.Y_AXIS)
        add(label("PROVIDER ROUTE"));add(Box.createVerticalStrut(8))
        button(this,"TEST CONNECTION"){val id=provider.selectedItem.toString();runCatching{controller.testProvider(id)}.onSuccess{r->status.text=(if(r.ok)"● READY" else "× FAILED")+" "+id+" / "+r.message+" / "+r.latencyMs+"ms"}.onFailure(::showError)}
        val customUrl=JTextField("http://127.0.0.1:4000/v1");val customEnv=JTextField("CUSTOM_OPENAI_API_KEY")
        add(label("CUSTOM OPENAI-COMPATIBLE BASE URL"));add(customUrl);add(label("CUSTOM KEY ENV NAME"));add(customEnv)
        button(this,"REGISTER CUSTOM ROUTE"){runCatching{controller.configureCustomProvider(customUrl.text.trim(),customEnv.text.trim())}.onSuccess{provider.selectedItem="custom"}.onFailure(::showError)}

        add(Box.createVerticalStrut(13));add(label("MODEL CAPABILITIES"))
        capabilityLabel.background=Color(0x0D,0x0D,0x0B);capabilityLabel.foreground=OffworldTheme.muted;capabilityLabel.font=Font(Font.MONOSPACED,Font.PLAIN,10)
        add(JScrollPane(capabilityLabel));model.addActionListener{refreshCapabilities()};refreshCapabilities()

        add(Box.createVerticalStrut(13));add(label("OFFWORLD APPEARANCE / DARK ONLY"))
        add(maskVisible);add(label("MASK OVERLAY COLOR  #RRGGBB"));add(maskColor);add(label("MASK OVERLAY OPACITY %"));add(maskOpacity)
        add(reducedMotion);add(label("MOTION LEVEL"));add(motionLevel);add(label("UI DENSITY"));add(uiDensity)
        button(this,"APPLY APPEARANCE"){runCatching{
            controller.setMaskVisible(maskVisible.isSelected)
            controller.setMaskOverlayStyle(maskColor.text,(maskOpacity.value as Number).toDouble()/100.0)
            controller.setAppearance(reducedMotion.isSelected,motionLevel.selectedItem.toString(),uiDensity.selectedItem.toString())
        }.onFailure(::showError)}

        add(Box.createVerticalStrut(13));add(label("PROJECT / EDITOR ACTIONS"))
        listOf(
            "NEW LOCAL PROJECT" to {chooseProject(true)},
            "OPEN PROJECT" to {chooseProject(false)},
            "IMPORT IMAGE" to {chooseImage()},
            "ADD REFERENCE" to {chooseReference()},
            "PASTE IMAGE" to {runCatching{controller.importClipboardImage()}.onFailure(::showError)},
            "MASK EDITOR" to {openMask()},
            "SMART SUBJECT MASK" to {runCatching{controller.smartSaliencyMask()}.onFailure(::showError)},
            "ROTATE 90°" to {runCatching{controller.rotateCurrent(true)}.onFailure(::showError)},
            "FLIP HORIZONTAL" to {runCatching{controller.flipCurrent(true)}.onFailure(::showError)},
            "CROP…" to {cropDialog()},
            "RESIZE…" to {resizeDialog()},
            "UPSCALE…" to {upscaleDialog()},
            "RUN SELECTED WORKFLOW" to {runSelectedWorkflow()},
            "EXPORT CURRENT IMAGE" to {exportCurrentImage()},
            "EXPORT HTML REPORT" to {runCatching{controller.exportReport()}.onSuccess{status.text="REPORT "+it.toAbsolutePath()}.onFailure(::showError)},
            "EXPORT DIAGNOSTICS" to {runCatching{controller.exportDiagnostics()}.onSuccess{status.text="DIAGNOSTICS "+it.toAbsolutePath()}.onFailure(::showError)},
            "DOCUMENTATION / HYPER INDEX" to {val p=Path.of("docs/AGENT_USER_README.html").toAbsolutePath();if(Files.exists(p))Desktop.getDesktop().browse(p.toUri()) else showError(IllegalStateException("Documentation not found: "+p))},
            "FIT / RESET VIEW" to {controller.resetView()}
        ).forEach{(text,fn)->button(this,text){fn()}}
    }

    fun openCommandPalette(){
        val d=JDialog(this,"COSMOSIS / COMMAND",false);d.layout=BorderLayout();d.preferredSize=Dimension(650,460);d.background=OffworldTheme.background
        val query=JTextField();val list=DefaultListModel<CommandRef>();val results=JList(list);d.add(query,BorderLayout.NORTH);d.add(JScrollPane(results),BorderLayout.CENTER)
        val base=listOf(CommandRef("ACTIONS / Import image"){chooseImage()},CommandRef("ACTIONS / Paste image"){runCatching{controller.importClipboardImage()}.onFailure(::showError)},CommandRef("ACTIONS / Mask editor"){openMask()},CommandRef("ACTIONS / Smart saliency mask"){runCatching{controller.smartSaliencyMask()}.onFailure(::showError)},CommandRef("ACTIONS / Rotate 90°"){runCatching{controller.rotateCurrent(true)}.onFailure(::showError)},CommandRef("ACTIONS / Flip horizontal"){runCatching{controller.flipCurrent(true)}.onFailure(::showError)},CommandRef("ACTIONS / Crop"){cropDialog()},CommandRef("ACTIONS / Generate"){generate(false)},CommandRef("ACTIONS / Edit current"){generate(true)},CommandRef("ACTIONS / Agent Build"){runAgentBuildDialog()},CommandRef("ACTIONS / Fit image"){controller.resetView()},CommandRef("DOCUMENTATION / Hyper Index"){val p=Path.of("docs/AGENT_USER_README.html").toAbsolutePath();Desktop.getDesktop().browse(p.toUri())})
        fun rebuild(){list.clear();val q=query.text.trim();base.filter{q.isBlank()||it.label.contains(q,true)}.forEach(list::addElement);if(q.isNotBlank()){controller.promptSearch(q).take(8).forEach{p->list.addElement(CommandRef("PROMPT / ${p.treePath} / ${p.title}"){loadPrompt(p.id)})};controller.agentIndex.lookup(q,6).forEach{e->list.addElement(CommandRef("DOC / ${e.capability} / ${e.module}"){status.text=controller.agentIndex.contextPacket(q).replace('\n',' ').take(240)})}}}
        query.document.addDocumentListener(object:DocumentListener{override fun insertUpdate(e:DocumentEvent)=rebuild();override fun removeUpdate(e:DocumentEvent)=rebuild();override fun changedUpdate(e:DocumentEvent)=rebuild()});query.addActionListener{results.selectedValue?.let{it.action();d.dispose()}};results.addMouseListener(object:java.awt.event.MouseAdapter(){override fun mouseClicked(e:java.awt.event.MouseEvent){if(e.clickCount==2)results.selectedValue?.let{it.action();d.dispose()}}});rebuild();d.pack();d.setLocationRelativeTo(this);d.isVisible=true;query.requestFocusInWindow()
    }

    private fun runAgentBuildDialog(){
        val intent=JOptionPane.showInputDialog(this,"Describe the bounded image task","Agent Build",JOptionPane.PLAIN_MESSAGE)?.trim().orEmpty();if(intent.isBlank())return
        val selectedVariants=(variants.value as Number).toInt().coerceAtMost(4)
        val modelId=model.editor.item?.toString()?.trim().orEmpty();if(modelId.isBlank())return showError(IllegalArgumentException("Select a model before Agent Build"))
        val budget=JobBudget(maxGenerations=selectedVariants,maxRetries=1,maxParallelWorkers=2,timeoutSeconds=180)
        val plan=runCatching{controller.planAgentBuild(intent,budget)}.getOrElse{return showError(it)}
        val summary="""$plan

ROUTE  ${provider.selectedItem} / $modelId
BOUND  generations=$selectedVariants retries=1 parallel=2 timeout=180s

Run this bounded plan?""".trimIndent()
        if(JOptionPane.showConfirmDialog(this,summary,"COSMOSIS / AGENT BUILD",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
        runCatching{controller.runAgentBuild(intent,provider.selectedItem.toString(),modelId,selectedVariants,budget)}.onSuccess{id->status.text="AGENT BUILD / $id / QUEUED"}.onFailure(::showError)
    }

    private fun refreshModels(){val id=provider.selectedItem?.toString()?:return;val current=model.editor.item?.toString();val defs=runCatching{controller.modelsFor(id)}.getOrDefault(emptyList());model.removeAllItems();defs.forEach{model.addItem(it.id)};if(current!=null&&defs.none{it.id==current})model.editor.item=current else if(model.itemCount>0)model.selectedIndex=0;refreshCapabilities()}
    private fun chooseProject(create:Boolean){val fc=JFileChooser().apply{fileSelectionMode=JFileChooser.DIRECTORIES_ONLY};if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{if(create)controller.createProject(fc.selectedFile.toPath(),fc.selectedFile.name)else controller.openProject(fc.selectedFile.toPath())}.onFailure(::showError)}
    private fun chooseImage(){val fc=JFileChooser();if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{controller.importImage(fc.selectedFile.toPath())}.onFailure(::showError)}
    private fun openImageToPrompt(){val path=state.get().imagePath?:return showError(IllegalStateException("Import/select an image first"));val dlg=ImageToPromptDialog(this,Path.of(path));dlg.isVisible=true;if(!dlg.accepted)return;val text=dlg.promptText();prompt.text=text;promptTitle.text=dlg.titleField.text.trim().ifBlank{"Image → Prompt"};treePath.text=dlg.pathField.text.trim().ifBlank{"MY PROMPTS/Image to Prompt"};dirty=true;if(dlg.saveToLibrary)runCatching{controller.savePrompt(promptTitle.text,text,treePath.text)}.onSuccess{p->selectedPromptId=p.id;dirty=false;refreshTrees();status.text="IMAGE → PROMPT SAVED / ${p.id}"}.onFailure(::showError)}
    private fun openMask(){val path=state.get().imagePath?:return showError(IllegalStateException("Import/select an image first"));val dlg=MaskEditorDialog(this,Path.of(path));dlg.isVisible=true;if(dlg.accepted)runCatching{controller.saveMaskDocument(dlg.maskDocument())}.onFailure(::showError)}
    private fun savePrompt(){
        val draft=PromptAsset(title=promptTitle.text,body=prompt.text,treePath=treePath.text,summary=promptSummary.text,negativeConstraints=negativeConstraints.text,explicitKeywords=parseTags(explicitKeywords.text),styleTags=parseList(styleTags.text),subjectTags=parseList(subjectTags.text),compositionTags=parseList(compositionTags.text),lightingTags=parseList(lightingTags.text),cameraTags=parseList(cameraTags.text),materialTags=parseList(materialTags.text),variables=parseVariables(promptVariables.text),notes=promptNotes.text,favorite=promptFavorite.isSelected)
        val id=selectedPromptId;runCatching{if(id==null)controller.savePromptAsset(draft) else controller.updatePromptAsset(id,draft)}.onSuccess{p->selectedPromptId=p.id;dirty=false;undo.discardAllEdits();status.text="PROMPT SAVED / ${controller.promptRevisions(p.id).size} REV / ${p.allKeywords().size} INDEX TERMS";refreshTrees()}.onFailure(::showError)
    }
    private fun newPrompt(){if(dirty && JOptionPane.showConfirmDialog(this,"Creating a new prompt will clear unsaved text.\nDiscard changes?","UNSAVED PROMPT",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;selectedPromptId=null;promptTitle.text="Untitled prompt";treePath.text="MY PROMPTS/General";prompt.text="";promptSummary.text="";negativeConstraints.text="";explicitKeywords.text="";styleTags.text="";subjectTags.text="";compositionTags.text="";lightingTags.text="";cameraTags.text="";materialTags.text="";promptVariables.text="";promptNotes.text="";promptFavorite.isSelected=false;dirty=false;undo.discardAllEdits()}
    private fun generate(edit:Boolean){val body=prompt.text.trim();if(body.isBlank())return showError(IllegalArgumentException("Prompt is empty"));val modelId=model.editor.item?.toString()?.trim().orEmpty();val q=quality.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()};val meta=linkedMapOf<String,String>();imageSize.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()}?.let{meta["imageSize"]=it};thinkingLevel.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()}?.let{meta["thinkingLevel"]=it};if(searchGrounding.isSelected)meta["searchGrounding"]="true";if(providerWorkflow.selectedItem=="responses")meta["openAiWorkflow"]="responses";reasoningModel.text.trim().takeIf{it.isNotBlank()}?.let{meta["reasoningModel"]=it};runCatching{controller.generate(body,provider.selectedItem.toString(),modelId,variants=(variants.value as Number).toInt(),edit=edit,transparent=transparentOutput.isSelected,quality=q,promptId=selectedPromptId,aspectRatio=aspectRatio.text.trim().takeIf{it.isNotBlank()},metadata=meta)}.onFailure(::showError)}
    private fun loadPrompt(id:String){controller.allPrompts().find{it.id==id}?.let{p->selectedPromptId=if(p.readOnly)null else p.id;promptTitle.text=p.title;treePath.text=p.treePath;prompt.text=p.body;promptSummary.text=p.summary;negativeConstraints.text=p.negativeConstraints;explicitKeywords.text=p.explicitKeywords.joinToString(", ");styleTags.text=p.styleTags.joinToString(", ");subjectTags.text=p.subjectTags.joinToString(", ");compositionTags.text=p.compositionTags.joinToString(", ");lightingTags.text=p.lightingTags.joinToString(", ");cameraTags.text=p.cameraTags.joinToString(", ");materialTags.text=p.materialTags.joinToString(", ");promptVariables.text=p.variables.entries.joinToString("\n"){it.key+"="+it.value};promptNotes.text=p.notes;promptFavorite.isSelected=p.favorite;dirty=false;undo.discardAllEdits();status.text="${if(p.readOnly)"UPSTREAM / READ ONLY" else "LOCAL"} / ${p.title} / ${p.allKeywords().size} indexed terms"}}
    private fun selectedNodeId(tree:JTree)=((tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? NodeRef)?.id
    private fun refreshTrees(q:String=""){
        val scope=searchScope.selectedItem?.toString()?:"ALL";val all=if(q.isBlank())controller.allPrompts() else controller.promptSearch(q)
        buildTree(localTree,"MY PROMPTS",all.filter{!it.readOnly&&(scope=="ALL"||scope=="MY PROMPTS")});buildTree(premadeTree,"PREMADE PROMPTS",all.filter{it.readOnly&&(scope=="ALL"||scope=="PREMADE PROMPTS")})
    }
    private fun buildTree(tree:JTree,rootName:String,nodes:List<PromptAsset>){val root=DefaultMutableTreeNode(rootName);for(p in nodes.sortedBy{it.treePath+it.title}){var cur=root;for(seg in p.treePath.split('/').drop(1)){if(seg.isBlank())continue;var found:DefaultMutableTreeNode?=null;val en=cur.children();while(en.hasMoreElements()){val n=en.nextElement() as DefaultMutableTreeNode;if(n.userObject.toString()==seg){found=n;break}};cur=found?:DefaultMutableTreeNode(seg).also{cur.add(it)}};cur.add(DefaultMutableTreeNode(NodeRef(p.id,"${if(p.readOnly)"UPSTREAM · " else ""}${p.title}")))};tree.model=DefaultTreeModel(root);for(i in 0 until tree.rowCount)tree.expandRow(i)}
    private fun refreshVersions(){val refs=controller.versionNodes().map{VersionRef(it.id,"${it.id.takeLast(8)} / ${it.operation} / ${it.name}")};versionList.setListData(refs.toTypedArray())}
    private fun importPrompt(){val fc=JFileChooser();if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{controller.importPrompt(fc.selectedFile.toPath())}.onSuccess{loadPrompt(it.id);refreshTrees()}.onFailure(::showError)}
    private fun exportPrompt(){val id=selectedPromptId?:return showError(IllegalStateException("Select an editable local prompt first"));val fc=JFileChooser().apply{selectedFile=java.io.File("cosmosis-prompt.json")};if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{controller.exportPrompt(id,fc.selectedFile.toPath())}.onFailure(::showError)}
    private fun parseList(text:String)=text.split(',','\n').map{it.trim()}.filter{it.isNotEmpty()}
    private fun parseTags(text:String)=parseList(text).toSet()
    private fun parseVariables(text:String)=text.lineSequence().mapNotNull{line->val i=line.indexOf('=');if(i<=0)null else line.substring(0,i).trim() to line.substring(i+1)}.toMap()
    private fun refreshCapabilities(){
        val pid=provider.selectedItem?.toString()?:return
        val mid=model.editor.item?.toString()?.trim().orEmpty()
        if(mid.isBlank()){capabilityLabel.text="No model selected";return}
        runCatching{controller.capabilitiesFor(pid,mid)}.onSuccess{c->
            capabilityLabel.text=listOf(
                "text→image=${c.textToImage}","image→image=${c.imageToImage}","mask=${c.maskEditing}",
                "multi-ref=${c.multipleReferences} / max ${c.maxReferenceImages}","multi-turn=${c.multiTurnEditing}",
                "transparent=${c.transparentBackground}","search=${c.searchGrounding}",
                "ratios=${c.aspectRatios.joinToString().ifBlank{"provider default"}}",
                "sizes=${c.imageSizes.joinToString().ifBlank{"provider default"}}",
                "formats=${c.outputFormats.joinToString()}"
            ).joinToString("  │  ")
            transparentOutput.isEnabled=c.transparentBackground;if(!c.transparentBackground)transparentOutput.isSelected=false
            searchGrounding.isEnabled=c.searchGrounding;if(!c.searchGrounding)searchGrounding.isSelected=false
            imageSize.isEnabled=c.imageSizes.isNotEmpty();if(!imageSize.isEnabled)imageSize.selectedItem=""
            aspectRatio.isEnabled=c.aspectRatios.isNotEmpty()||c.customDimensions;if(!aspectRatio.isEnabled)aspectRatio.text=""
            val responses=pid.equals("openai",true);providerWorkflow.isEnabled=responses;reasoningModel.isEnabled=responses
            if(!responses){providerWorkflow.selectedItem="direct";reasoningModel.text=""}
        }.onFailure{capabilityLabel.text="Unavailable: ${it.message}"}
    }
    private fun cropDialog(){val s=state.get();if(s.imagePath==null)return showError(IllegalStateException("Import/select an image first"));val x=JTextField("0");val y=JTextField("0");val w=JTextField(s.imageWidth.toString());val h=JTextField(s.imageHeight.toString());val form=JPanel(GridLayout(0,2,8,8)).apply{add(label("X"));add(x);add(label("Y"));add(y);add(label("WIDTH"));add(w);add(label("HEIGHT"));add(h)};if(JOptionPane.showConfirmDialog(this,form,"CROP / PIXELS",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;runCatching{controller.cropCurrent(x.text.toInt(),y.text.toInt(),w.text.toInt(),h.text.toInt())}.onFailure(::showError)}
    private fun installKeys(){KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher{e->if(e.id!=KeyEvent.KEY_PRESSED)return@addKeyEventDispatcher false;val ctrl=e.isControlDown||e.isMetaDown;when{ctrl&&e.keyCode==KeyEvent.VK_Z&&prompt.hasFocus()->{if(e.isShiftDown){if(undo.canRedo())undo.redo()}else if(undo.canUndo())undo.undo();true};ctrl&&e.keyCode==KeyEvent.VK_Y&&prompt.hasFocus()->{if(undo.canRedo())undo.redo();true};ctrl&&e.keyCode==KeyEvent.VK_S->{savePrompt();true};ctrl&&e.keyCode==KeyEvent.VK_ENTER->{generate(false);true};ctrl&&e.keyCode==KeyEvent.VK_K->{openCommandPalette();true};ctrl&&e.keyCode==KeyEvent.VK_V&&!prompt.hasFocus()->{runCatching{controller.importClipboardImage()}.onFailure(::showError);true};e.keyCode==KeyEvent.VK_0&&!prompt.hasFocus()->{controller.resetView();true};else->false}}}
    private fun applyTheme(){UIManager.put("Panel.background",OffworldTheme.background);UIManager.put("TabbedPane.background",OffworldTheme.background);UIManager.put("TabbedPane.foreground",OffworldTheme.foreground);UIManager.put("Label.foreground",OffworldTheme.foreground);UIManager.put("Button.background",Color(0x1C,0x1C,0x18));UIManager.put("Button.foreground",OffworldTheme.foreground);UIManager.put("TextField.background",Color(0x13,0x13,0x10));UIManager.put("TextField.foreground",OffworldTheme.foreground);UIManager.put("TextArea.background",Color(0x0D,0x0D,0x0B));UIManager.put("TextArea.foreground",OffworldTheme.foreground);UIManager.put("Tree.background",Color(0x13,0x13,0x10));UIManager.put("Tree.foreground",OffworldTheme.foreground);UIManager.put("List.background",Color(0x13,0x13,0x10));UIManager.put("List.foreground",OffworldTheme.foreground);UIManager.put("ScrollPane.background",OffworldTheme.background);UIManager.put("Component.arc",0)}
    private fun panel(layout:LayoutManager=FlowLayout()):JPanel=JPanel(layout).apply{background=OffworldTheme.background;border=BorderFactory.createEmptyBorder(13,13,13,13)}
    private fun label(s:String)=JLabel(s).apply{font=Font(Font.MONOSPACED,Font.PLAIN,11);foreground=OffworldTheme.muted}
    private fun button(p:Container,text:String,action:()->Unit){p.add(JButton(text).apply{font=Font(Font.MONOSPACED,Font.PLAIN,11);isFocusPainted=false;border=BorderFactory.createLineBorder(OffworldTheme.secondary);addActionListener{action()}})}
    private fun showError(t:Throwable){JOptionPane.showMessageDialog(this,t.message?:t.toString(),"COSMOSIS / ERROR",JOptionPane.ERROR_MESSAGE)}
    data class NodeRef(val id:String,val label:String){override fun toString()=label}
    data class VersionRef(val id:String,val label:String){override fun toString()=label}
    data class CommandRef(val label:String,val action:()->Unit){override fun toString()=label}
}
