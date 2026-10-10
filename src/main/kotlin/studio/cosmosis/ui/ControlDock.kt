package studio.cosmosis.ui

import studio.cosmosis.*
import studio.cosmosis.provider.ModelRegistry
import studio.cosmosis.theme.OffworldTheme
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
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
import javax.swing.text.JTextComponent

class ControlDock(private val controller:StudioController,private val state:StudioState):JFrame("COSMOSIS / CONTROL") {
    private companion object {
        init { installOffworldDefaults() }

        fun installOffworldDefaults() {
            runCatching { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()) }
            val bg=OffworldTheme.background
            val fg=OffworldTheme.foreground
            val panel=Color(0x1C,0x1C,0x18)
            val field=Color(0x13,0x13,0x10)
            val text=Color(0x0D,0x0D,0x0B)
            val line=BorderFactory.createLineBorder(OffworldTheme.hairlineNormal)
            UIManager.put("Panel.background",bg)
            UIManager.put("Viewport.background",bg)
            UIManager.put("TabbedPane.background",bg)
            UIManager.put("TabbedPane.foreground",fg)
            UIManager.put("TabbedPane.selected",panel)
            UIManager.put("TabbedPane.contentAreaColor",bg)
            UIManager.put("TabbedPane.focus",OffworldTheme.hairlineStrong)
            UIManager.put("Label.foreground",fg)
            UIManager.put("Button.background",panel)
            UIManager.put("Button.foreground",fg)
            UIManager.put("Button.select",OffworldTheme.secondary)
            UIManager.put("CheckBox.background",bg)
            UIManager.put("CheckBox.foreground",fg)
            UIManager.put("TextField.background",field)
            UIManager.put("TextField.foreground",fg)
            UIManager.put("TextField.caretForeground",fg)
            UIManager.put("TextArea.background",text)
            UIManager.put("TextArea.foreground",fg)
            UIManager.put("TextArea.caretForeground",fg)
            UIManager.put("ComboBox.background",field)
            UIManager.put("ComboBox.foreground",fg)
            UIManager.put("ComboBox.selectionBackground",OffworldTheme.secondary)
            UIManager.put("ComboBox.selectionForeground",fg)
            UIManager.put("Spinner.background",field)
            UIManager.put("Spinner.foreground",fg)
            UIManager.put("Tree.background",field)
            UIManager.put("Tree.foreground",fg)
            UIManager.put("Tree.selectionBackground",OffworldTheme.secondary)
            UIManager.put("Tree.selectionForeground",fg)
            UIManager.put("List.background",field)
            UIManager.put("List.foreground",fg)
            UIManager.put("List.selectionBackground",OffworldTheme.secondary)
            UIManager.put("List.selectionForeground",fg)
            UIManager.put("ScrollPane.background",bg)
            UIManager.put("ScrollBar.background",bg)
            UIManager.put("ScrollBar.thumb",OffworldTheme.secondary)
            UIManager.put("SplitPane.background",bg)
            UIManager.put("OptionPane.background",bg)
            UIManager.put("OptionPane.messageForeground",fg)
            UIManager.put("ToolTip.background",panel)
            UIManager.put("ToolTip.foreground",fg)
            listOf("TextField","TextArea","ComboBox","Spinner","ScrollPane","Tree","List").forEach {
                UIManager.put("$it.border",line)
            }
            UIManager.put("Component.arc",0)
        }
    }

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
    private val ormlEnabled=JCheckBox("ENABLE OPTIONAL ORML RUNTIMES",true).apply{background=OffworldTheme.background;foreground=OffworldTheme.foreground}
    private val ormlStatus=JTextArea(5,30).apply{isEditable=false;lineWrap=true;wrapStyleWord=true;background=Color(0x0D,0x0D,0x0B);foreground=OffworldTheme.muted;font=Font(Font.MONOSPACED,Font.PLAIN,10)}
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
    private val openAiKey=JPasswordField().apply{toolTipText="Session only. Never written to the project or settings."}
    private val openAiKeyStatus=JLabel()
    private var syncingProviderModel=false
    private var keyDispatcher:KeyEventDispatcher?=null
    private var dirty=false
    private var selectedPromptId:String?=null
    private var premadeVisible=true

    init {
        applyTheme();defaultCloseOperation=DO_NOTHING_ON_CLOSE
        addWindowListener(object:WindowAdapter(){
            override fun windowClosing(e:WindowEvent){requestClose()}
            override fun windowClosed(e:WindowEvent){keyDispatcher?.let{KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(it)};keyDispatcher=null}
        })
        layout=BorderLayout();minimumSize=Dimension(720,780);preferredSize=Dimension(880,920)
        val tabs=JTabbedPane();tabs.addTab("PROMPT",promptPanel());tabs.addTab("LIBRARY",libraryPanel());tabs.addTab("REFERENCES",referencePanel());tabs.addTab("VERSIONS",versionPanel());tabs.addTab("WORKERS",workerPanel());tabs.addTab("DIRECTIVES",directivePanel());tabs.addTab("SETTINGS",settingsPanel());add(tabs,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);status.border=BorderFactory.createEmptyBorder(8,13,8,13)
        prompt.document.addUndoableEditListener(UndoableEditListener{e:UndoableEditEvent->undo.addEdit(e.edit)})
        prompt.document.addDocumentListener(object:DocumentListener{override fun insertUpdate(e:DocumentEvent)=mark();override fun removeUpdate(e:DocumentEvent)=mark();override fun changedUpdate(e:DocumentEvent)=mark();fun mark(){dirty=true;status.text="UNSAVED PROMPT"}})
        provider.addActionListener{
            if(!syncingProviderModel){
                refreshModels()
                val pid=provider.selectedItem?.toString()?:return@addActionListener
                val mid=model.selectedItem?.toString()?.trim().orEmpty()
                if(mid.isNotBlank())state.update{it.copy(provider=pid.uppercase(),model=mid,message="ROUTE / "+pid.uppercase()+" / "+mid)}
            }
        }
        model.addActionListener{
            refreshCapabilities()
            if(!syncingProviderModel){
                val mid=model.selectedItem?.toString()?.trim().orEmpty()
                if(mid.isNotBlank())state.update{it.copy(model=mid)}
            }
        }
        workflowMode.addActionListener{(workflowMode.selectedItem as? WorkflowMode)?.let(controller::setWorkflowMode)}
        refreshModels()
        state.listen { s -> SwingUtilities.invokeLater {
            status.text=s.message;workers.text=s.jobs.joinToString("\n"){"${it.id.take(12)}  ${it.state}  ${it.type}  retry=${it.retryCount}"}
            workerList.setListData(s.jobs.map{JobRef(it.id,it.id.take(12)+" / "+it.state+" / "+it.type+" / retry="+it.retryCount)}.toTypedArray())
            if(!prompt.hasFocus() && !dirty && s.promptBody.isNotBlank()){prompt.text=s.promptBody;promptTitle.text=s.promptTitle}
            syncProviderModelFromState(s)
            if(workflowMode.selectedItem!=s.workflowMode)workflowMode.selectedItem=s.workflowMode
            maskVisible.isSelected=s.maskVisible;maskColor.text=s.maskOverlayColor;maskOpacity.value=(s.maskOverlayOpacity*100).toInt()
            reducedMotion.isSelected=s.reducedMotion;motionLevel.selectedItem=s.motionLevel;uiDensity.selectedItem=s.uiDensity
            ormlEnabled.isSelected=s.ormlEnabled
            refreshTrees();refreshReferences();refreshVersions();refreshDirectives();refreshOrmlStatus()
        } }
        installKeys();pack();setLocation(30,70);applyThemeToTree(this);isVisible=true
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
        add(label("OPENAI API KEY / SESSION ONLY"))
        add(openAiKey)
        add(openAiKeyStatus)
        val openAiActions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(openAiActions,"USE FOR SESSION"){
            val chars=openAiKey.password
            try{
                controller.configureOpenAiSessionKey(chars)
                openAiKey.text=""
                provider.selectedItem="openai"
                refreshModels()
                refreshOpenAiKeyStatus()
                status.text="OPENAI / SESSION KEY READY — use TEST CONNECTION to verify access"
            }catch(t:Throwable){showError(t)}finally{chars.fill('\u0000')}
        }
        button(openAiActions,"CLEAR SESSION KEY"){controller.clearOpenAiSessionKey();openAiKey.text="";refreshOpenAiKeyStatus()}
        add(openAiActions)
        refreshOpenAiKeyStatus()
        button(this,"TEST CONNECTION"){val id=provider.selectedItem.toString();runCatching{controller.testProvider(id)}.onSuccess{r->status.text=(if(r.ok)"● READY" else "× FAILED")+" "+id+" / "+r.message+" / "+r.latencyMs+"ms"}.onFailure(::showError)}
        val customUrl=JTextField("http://127.0.0.1:4000/v1");val customEnv=JTextField("CUSTOM_OPENAI_API_KEY")
        add(label("CUSTOM OPENAI-COMPATIBLE BASE URL"));add(customUrl);add(label("CUSTOM KEY ENV NAME"));add(customEnv)
        button(this,"REGISTER CUSTOM ROUTE"){runCatching{controller.configureCustomProvider(customUrl.text.trim(),customEnv.text.trim())}.onSuccess{provider.selectedItem="custom"}.onFailure(::showError)}

        add(Box.createVerticalStrut(13));add(label("MODEL CAPABILITIES"))
        capabilityLabel.background=Color(0x0D,0x0D,0x0B);capabilityLabel.foreground=OffworldTheme.muted;capabilityLabel.font=Font(Font.MONOSPACED,Font.PLAIN,10)
        add(JScrollPane(capabilityLabel));refreshCapabilities()

        add(Box.createVerticalStrut(13));add(label("OFFWORLD APPEARANCE / DARK ONLY"))
        add(maskVisible);add(label("MASK OVERLAY COLOR  #RRGGBB"));add(maskColor);add(label("MASK OVERLAY OPACITY %"));add(maskOpacity)
        add(reducedMotion);add(label("MOTION LEVEL"));add(motionLevel);add(label("UI DENSITY"));add(uiDensity)
        button(this,"APPLY APPEARANCE"){runCatching{
            controller.setMaskVisible(maskVisible.isSelected)
            controller.setMaskOverlayStyle(maskColor.text,(maskOpacity.value as Number).toDouble()/100.0)
            controller.setAppearance(reducedMotion.isSelected,motionLevel.selectedItem.toString(),uiDensity.selectedItem.toString())
        }.onFailure(::showError)}

        add(Box.createVerticalStrut(13));add(label("OPTIONAL ORML / LOCAL ML"))
        add(ormlEnabled);add(JScrollPane(ormlStatus))
        button(this,"APPLY ORML STATE"){controller.setOrmlEnabled(ormlEnabled.isSelected);refreshOrmlStatus()}
        val ormlActions=panel(FlowLayout(FlowLayout.LEFT,5,5))
        button(ormlActions,"PERSON MASK"){runCatching{controller.ormlPersonMask()}.onFailure(::showError)}
        button(ormlActions,"EMBED IMAGE"){runCatching{controller.ormlImageEmbedding()}.onSuccess{status.text="EMBEDDING "+it.fileName}.onFailure(::showError)}
        button(ormlActions,"ORML UPSCALE"){runCatching{controller.ormlSuperResolution()}.onFailure(::showError)}
        add(ormlActions)

        add(Box.createVerticalStrut(13));add(label("PROJECT / EDITOR ACTIONS"))
        listOf(
            "NEW LOCAL PROJECT" to {chooseProject(true)},
            "OPEN PROJECT" to {chooseProject(false)},
            "IMPORT IMAGE" to {chooseImage()},
            "ADD REFERENCE" to {chooseReference()},
            "PASTE IMAGE" to {runCatching{controller.importClipboardImage()}.onFailure(::showError)},
            "MASK EDITOR" to {openMask()},
            "SMART SUBJECT MASK" to {runCatching{controller.smartSaliencyMask()}.onFailure(::showError)},
            "ORML PERSON MASK" to {runCatching{controller.ormlPersonMask()}.onFailure(::showError)},
            "ORML IMAGE EMBEDDING" to {runCatching{controller.ormlImageEmbedding()}.onFailure(::showError)},
            "ORML SUPER RESOLUTION" to {runCatching{controller.ormlSuperResolution()}.onFailure(::showError)},
            "TOGGLE ANALYSIS OVERLAY" to {controller.setAnalysisVisible(!state.get().analysisVisible)},
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
        val base=mutableListOf(
            CommandRef("ACTIONS / Import image"){chooseImage()},
            CommandRef("ACTIONS / Add reference image"){chooseReference()},
            CommandRef("ACTIONS / Paste image"){runCatching{controller.importClipboardImage()}.onFailure(::showError)},
            CommandRef("ACTIONS / Image to prompt"){openImageToPrompt()},
            CommandRef("ACTIONS / Mask editor"){openMask()},
            CommandRef("ACTIONS / Smart subject mask"){runCatching{controller.smartSaliencyMask()}.onFailure(::showError)},
            CommandRef("ACTIONS / Rotate 90°"){runCatching{controller.rotateCurrent(true)}.onFailure(::showError)},
            CommandRef("ACTIONS / Flip horizontal"){runCatching{controller.flipCurrent(true)}.onFailure(::showError)},
            CommandRef("ACTIONS / Crop"){cropDialog()},
            CommandRef("ACTIONS / Resize"){resizeDialog()},
            CommandRef("ACTIONS / Upscale"){upscaleDialog()},
            CommandRef("ACTIONS / Export current image"){exportCurrentImage()},
            CommandRef("ACTIONS / Agent Build"){runAgentBuildDialog()},
            CommandRef("ACTIONS / Fit image"){controller.resetView()},
            CommandRef("MASK / Toggle overlay"){controller.setMaskVisible(!state.get().maskVisible)},
            CommandRef("ANALYSIS / Toggle overlay"){controller.setAnalysisVisible(!state.get().analysisVisible)},
            CommandRef("DOCUMENTATION / Hyper Index"){val p=Path.of("docs/AGENT_USER_README.html").toAbsolutePath();if(Files.exists(p))Desktop.getDesktop().browse(p.toUri())}
        )
        WorkflowMode.entries.forEach{mode->base+=CommandRef("WORKFLOW / "+mode.name.replace('_',' ')){workflowMode.selectedItem=mode;controller.setWorkflowMode(mode)}}
        controller.resumableJobs().forEach{job->base+=CommandRef("RESUME / "+job.id.take(12)+" / "+job.type){runCatching{controller.resumeJob(job.id)}.onFailure(::showError)}}
        fun rebuild(){
            list.clear();val q=query.text.trim()
            base.filter{q.isBlank()||it.label.contains(q,true)}.forEach(list::addElement)
            if(q.isNotBlank()){
                controller.promptSearch(q).take(8).forEach{p->list.addElement(CommandRef("PROMPT / "+p.treePath+" / "+p.title){loadPrompt(p.id)})}
                controller.agentIndex.lookup(q,6).forEach{e->list.addElement(CommandRef("DOC / "+e.capability+" / "+e.module){status.text=controller.agentIndex.contextPacket(q).replace('\n',' ').take(240)})}
            }
            if(list.size()>0)results.selectedIndex=0
        }
        query.document.addDocumentListener(object:DocumentListener{override fun insertUpdate(e:DocumentEvent)=rebuild();override fun removeUpdate(e:DocumentEvent)=rebuild();override fun changedUpdate(e:DocumentEvent)=rebuild()})
        query.addActionListener{results.selectedValue?.let{it.action();d.dispose()}}
        results.addMouseListener(object:java.awt.event.MouseAdapter(){override fun mouseClicked(e:java.awt.event.MouseEvent){if(e.clickCount==2)results.selectedValue?.let{it.action();d.dispose()}}})
        results.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"run");results.actionMap.put("run",object:AbstractAction(){override fun actionPerformed(e:java.awt.event.ActionEvent?){results.selectedValue?.let{it.action();d.dispose()}}})
        rebuild();d.pack();d.setLocationRelativeTo(this);d.isVisible=true;query.requestFocusInWindow()
    }

    private fun runAgentBuildDialog(){
        val intent=JOptionPane.showInputDialog(this,"Describe the bounded image task","Agent Build",JOptionPane.PLAIN_MESSAGE)?.trim().orEmpty();if(intent.isBlank())return
        val modelId=model.editor.item?.toString()?.trim().orEmpty();if(modelId.isBlank())return showError(IllegalArgumentException("Select a model before Agent Build"))
        val generations=JSpinner(SpinnerNumberModel((variants.value as Number).toInt().coerceIn(1,4),1,4,1))
        val retries=JSpinner(SpinnerNumberModel(1,0,5,1));val parallel=JSpinner(SpinnerNumberModel(2,1,4,1));val timeout=JSpinner(SpinnerNumberModel(180,10,3600,10))
        val maxSpend=JTextField(if(provider.selectedItem.toString()=="local")"0.00" else "")
        val fallback=JComboBox(arrayOf("fail-closed","same-provider-retry"))
        val form=JPanel(GridLayout(0,2,8,8)).apply{
            add(label("MAX GENERATIONS"));add(generations);add(label("MAX RETRIES"));add(retries)
            add(label("MAX PARALLEL WORKERS"));add(parallel);add(label("TIMEOUT SECONDS"));add(timeout)
            add(label("MAX PROVIDER SPEND USD"));add(maxSpend);add(label("FALLBACK POLICY"));add(fallback)
        }
        if(JOptionPane.showConfirmDialog(this,form,"AGENT BUILD / BUDGET",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
        val spend=maxSpend.text.trim().takeIf{it.isNotBlank()}?.toDoubleOrNull()
        if(maxSpend.text.isNotBlank()&&(spend==null||spend<0.0))return showError(IllegalArgumentException("Max provider spend must be a non-negative USD amount or blank"))
        val selectedVariants=(generations.value as Number).toInt()
        val budget=JobBudget(
            maxGenerations=selectedVariants,maxRetries=(retries.value as Number).toInt(),
            maxParallelWorkers=(parallel.value as Number).toInt(),maxProviderSpendUsd=spend,
            timeoutSeconds=(timeout.value as Number).toLong(),fallbackPolicy=fallback.selectedItem.toString()
        )
        val plan=runCatching{controller.planAgentBuild(intent,budget)}.getOrElse{return showError(it)}
        val summary=buildString{
            appendLine(plan);appendLine()
            appendLine("ROUTE  "+provider.selectedItem+" / "+modelId)
            appendLine("BOUND  generations="+budget.maxGenerations+" retries="+budget.maxRetries+" parallel="+budget.maxParallelWorkers+" timeout="+budget.timeoutSeconds+"s")
            appendLine("SPEND  "+(budget.maxProviderSpendUsd?.let{"USD "+String.format("%.2f",it)}?:"not capped"))
            appendLine("FALLBACK  "+budget.fallbackPolicy)
            appendLine();append("Run this bounded plan?")
        }
        if(JOptionPane.showConfirmDialog(this,summary,"COSMOSIS / AGENT BUILD",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
        runCatching{controller.runAgentBuild(intent,provider.selectedItem.toString(),modelId,selectedVariants,budget)}.onSuccess{id->status.text="AGENT BUILD / "+id+" / QUEUED"}.onFailure(::showError)
    }

    private fun refreshModels(){
        val id=provider.selectedItem?.toString()?:return
        val current=model.selectedItem?.toString()?.trim()?.takeIf{it.isNotBlank()} ?: model.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()}
        val defs=runCatching{controller.modelsFor(id)}.getOrDefault(emptyList())
        val selected=preferredModelId(defs,current)
        model.removeAllItems()
        defs.forEach{model.addItem(it.id)}
        if(selected!=null)model.selectedItem=selected else model.editor.item=null
        refreshCapabilities()
    }
    private fun syncProviderModelFromState(snapshot:UiSnapshot){
        val pid=snapshot.provider.lowercase()
        if((0 until provider.itemCount).none{provider.getItemAt(it)==pid})return
        syncingProviderModel=true
        try{
            if(provider.selectedItem?.toString()!=pid)provider.selectedItem=pid
            refreshModels()
            val defs=runCatching{controller.modelsFor(pid)}.getOrDefault(emptyList())
            preferredModelId(defs,snapshot.model)?.let{model.selectedItem=it}
        }finally{syncingProviderModel=false}
    }
    private fun refreshOpenAiKeyStatus(){
        openAiKeyStatus.text=when(controller.openAiCredentialSource()){
            "session" -> "● Session key loaded (not persisted)"
            "environment" -> "● OPENAI_API_KEY detected in launch environment"
            else -> "○ No OpenAI key configured"
        }
    }
    private fun requestClose(){
        if(dirty){
            val choice=JOptionPane.showConfirmDialog(this,"Close Advanced and discard unsaved prompt edits?","UNSAVED PROMPT",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)
            if(choice!=JOptionPane.YES_OPTION)return
        }
        dispose()
    }
    private fun chooseProject(create:Boolean){val fc=JFileChooser().apply{fileSelectionMode=JFileChooser.DIRECTORIES_ONLY};if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{if(create)controller.createProject(fc.selectedFile.toPath(),fc.selectedFile.name)else controller.openProject(fc.selectedFile.toPath())}.onFailure(::showError)}
    private fun chooseImage(){val fc=JFileChooser();if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{controller.importImage(fc.selectedFile.toPath())}.onFailure(::showError)}
    private fun openImageToPrompt(){val path=state.get().imagePath?:return showError(IllegalStateException("Import/select an image first"));val dlg=ImageToPromptDialog(this,Path.of(path));dlg.isVisible=true;if(!dlg.accepted)return;val text=dlg.promptText();prompt.text=text;promptTitle.text=dlg.titleField.text.trim().ifBlank{"Image → Prompt"};treePath.text=dlg.pathField.text.trim().ifBlank{"MY PROMPTS/Image to Prompt"};dirty=true;if(dlg.saveToLibrary)runCatching{controller.savePrompt(promptTitle.text,text,treePath.text)}.onSuccess{p->selectedPromptId=p.id;dirty=false;refreshTrees();status.text="IMAGE → PROMPT SAVED / ${p.id}"}.onFailure(::showError)}
    private fun openMask(){val path=state.get().imagePath?:return showError(IllegalStateException("Import/select an image first"));val dlg=MaskEditorDialog(this,Path.of(path));dlg.isVisible=true;if(dlg.accepted)runCatching{controller.saveMaskDocument(dlg.maskDocument())}.onFailure(::showError)}
    private fun savePrompt(){
        val draft=PromptAsset(title=promptTitle.text,body=prompt.text,treePath=treePath.text,summary=promptSummary.text,negativeConstraints=negativeConstraints.text,explicitKeywords=parseTags(explicitKeywords.text),styleTags=parseList(styleTags.text),subjectTags=parseList(subjectTags.text),compositionTags=parseList(compositionTags.text),lightingTags=parseList(lightingTags.text),cameraTags=parseList(cameraTags.text),materialTags=parseList(materialTags.text),variables=parseVariables(promptVariables.text),notes=promptNotes.text,favorite=promptFavorite.isSelected)
        val id=selectedPromptId;runCatching{if(id==null)controller.savePromptAsset(draft) else controller.updatePromptAsset(id,draft)}.onSuccess{p->selectedPromptId=p.id;dirty=false;undo.discardAllEdits();status.text="PROMPT SAVED / ${controller.promptRevisions(p.id).size} REV / ${p.allKeywords().size} INDEX TERMS";refreshTrees()}.onFailure(::showError)
    }
    private fun newPrompt(){if(dirty && JOptionPane.showConfirmDialog(this,"Creating a new prompt will clear unsaved text.\nDiscard changes?","UNSAVED PROMPT",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;selectedPromptId=null;promptTitle.text="Untitled prompt";treePath.text="MY PROMPTS/General";prompt.text="";promptSummary.text="";negativeConstraints.text="";explicitKeywords.text="";styleTags.text="";subjectTags.text="";compositionTags.text="";lightingTags.text="";cameraTags.text="";materialTags.text="";promptVariables.text="";promptNotes.text="";promptFavorite.isSelected=false;dirty=false;undo.discardAllEdits()}
    private fun generate(edit:Boolean){
        workflowMode.selectedItem=if(edit)WorkflowMode.EDIT_EXISTING else WorkflowMode.QUICK_GENERATE
        runSelectedWorkflow()
    }

    private fun runSelectedWorkflow(){
        val mode=workflowMode.selectedItem as? WorkflowMode ?: WorkflowMode.QUICK_GENERATE
        if(mode==WorkflowMode.IMAGE_TO_PROMPT){openImageToPrompt();return}
        if(mode==WorkflowMode.UPSCALE){upscaleDialog();return}
        if(mode==WorkflowMode.AGENT_BUILD){runAgentBuildDialog();return}
        val body=prompt.text.trim();if(body.isBlank())return showError(IllegalArgumentException("Prompt is empty"))
        val modelId=model.editor.item?.toString()?.trim().orEmpty();if(modelId.isBlank())return showError(IllegalArgumentException("Select a model"))
        val q=quality.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()}
        val meta=linkedMapOf<String,String>()
        imageSize.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()}?.let{meta["imageSize"]=it}
        thinkingLevel.editor.item?.toString()?.trim()?.takeIf{it.isNotBlank()}?.let{meta["thinkingLevel"]=it}
        if(searchGrounding.isSelected)meta["searchGrounding"]="true"
        if(providerWorkflow.selectedItem=="responses")meta["openAiWorkflow"]="responses"
        reasoningModel.text.trim().takeIf{it.isNotBlank()}?.let{meta["reasoningModel"]=it}
        val w=parseOptionalInt(outputWidth.text,"width");val h=parseOptionalInt(outputHeight.text,"height")
        if((w==null)!=(h==null))return showError(IllegalArgumentException("Width and height must be supplied together"))
        val editing=mode in setOf(
            WorkflowMode.EDIT_EXISTING,WorkflowMode.MASK_EDIT,WorkflowMode.REFERENCE_REMIX,
            WorkflowMode.STYLE_TRANSFER,WorkflowMode.BACKGROUND_REPLACE,WorkflowMode.SUBJECT_PRESERVE
        )
        runCatching{
            controller.generate(
                body,provider.selectedItem.toString(),modelId,
                variants=(variants.value as Number).toInt(),edit=editing,transparent=transparentOutput.isSelected,
                quality=q,promptId=selectedPromptId,aspectRatio=aspectRatio.text.trim().takeIf{it.isNotBlank()},
                metadata=meta,width=w,height=h,outputFormat=outputFormat.selectedItem?.toString()?:"png",workflowMode=mode
            )
        }.onFailure(::showError)
    }
    private fun chooseReference(){
        val fc=JFileChooser().apply{isMultiSelectionEnabled=true}
        if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return
        val chosen=fc.selectedFiles.takeIf{it.isNotEmpty()}?.toList()?:listOf(fc.selectedFile)
        chosen.filterNotNull().forEach{file->runCatching{controller.addReferenceImage(file.toPath())}.onFailure(::showError)}
        refreshReferences()
    }

    private fun refreshReferences(){
        val root=state.get().projectRoot.takeIf{it.isNotBlank()}?.let(Path::of)
        val refs=controller.referenceImages().map{a->
            val labelPath=root?.resolve(a.path)?.fileName?.toString()?:a.path
            ReferenceRef(a.id,a.id.takeLast(8)+" / "+a.width+"×"+a.height+" / "+labelPath)
        }
        referenceList.setListData(refs.toTypedArray())
    }

    private fun newDirective(){selectedDirectiveId=null;directiveTitle.text="";directiveBody.text="";directiveEnabled.isSelected=true;directiveList.clearSelection()}
    private fun loadDirective(id:String){controller.allDirectives().firstOrNull{it.id==id}?.let{d->selectedDirectiveId=d.id;directiveTitle.text=d.title;directiveBody.text=d.body;directiveEnabled.isSelected=d.enabled}}
    private fun refreshDirectives(){directiveList.setListData(controller.allDirectives().map{DirectiveRef(it.id,(if(it.enabled)"● " else "○ ")+it.title)}.toTypedArray())}
    private fun saveDirective(){
        val title=directiveTitle.text.trim();val body=directiveBody.text.trim()
        runCatching{
            val id=selectedDirectiveId
            if(id==null)controller.saveDirective(title,body,directiveEnabled.isSelected)
            else controller.updateDirective(id,title,body,directiveEnabled.isSelected)
        }.onSuccess{d->selectedDirectiveId=d.id;refreshDirectives();status.text="DIRECTIVE SAVED / "+d.id}.onFailure(::showError)
    }

    private fun resizeDialog(){
        val s=state.get();if(s.imagePath==null)return showError(IllegalStateException("Import/select an image first"))
        val w=JTextField(s.imageWidth.toString());val h=JTextField(s.imageHeight.toString())
        val form=JPanel(GridLayout(0,2,8,8)).apply{add(label("WIDTH"));add(w);add(label("HEIGHT"));add(h)}
        if(JOptionPane.showConfirmDialog(this,form,"RESIZE / LOCAL",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
        runCatching{controller.resizeCurrent(w.text.toInt(),h.text.toInt())}.onFailure(::showError)
    }

    private fun upscaleDialog(){
        val scale=JComboBox(arrayOf("2","3","4"))
        if(JOptionPane.showConfirmDialog(this,scale,"UPSCALE FACTOR / LOCAL BICUBIC",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return
        runCatching{controller.upscaleCurrent(scale.selectedItem.toString().toInt())}.onFailure(::showError)
    }

    private fun exportCurrentImage(){
        if(state.get().imagePath==null)return showError(IllegalStateException("Import/select an image first"))
        val fc=JFileChooser().apply{selectedFile=java.io.File("cosmosis-current.png")}
        if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return
        runCatching{controller.exportCurrent(fc.selectedFile.toPath())}.onSuccess{status.text="IMAGE EXPORTED / "+it.toAbsolutePath()}.onFailure(::showError)
    }

    private fun parseOptionalInt(text:String,label:String):Int?{
        val v=text.trim();if(v.isBlank())return null
        return v.toIntOrNull()?.takeIf{it>0}?:throw IllegalArgumentException("$label must be a positive integer")
    }

    private fun loadPrompt(id:String){controller.allPrompts().find{it.id==id}?.let{p->selectedPromptId=if(p.readOnly)null else p.id;promptTitle.text=p.title;treePath.text=p.treePath;prompt.text=p.body;promptSummary.text=p.summary;negativeConstraints.text=p.negativeConstraints;explicitKeywords.text=p.explicitKeywords.joinToString(", ");styleTags.text=p.styleTags.joinToString(", ");subjectTags.text=p.subjectTags.joinToString(", ");compositionTags.text=p.compositionTags.joinToString(", ");lightingTags.text=p.lightingTags.joinToString(", ");cameraTags.text=p.cameraTags.joinToString(", ");materialTags.text=p.materialTags.joinToString(", ");promptVariables.text=p.variables.entries.joinToString("\n"){it.key+"="+it.value};promptNotes.text=p.notes;promptFavorite.isSelected=p.favorite;dirty=false;undo.discardAllEdits();status.text="${if(p.readOnly)"UPSTREAM / READ ONLY" else "LOCAL"} / ${p.title} / ${p.allKeywords().size} indexed terms"}}
    private fun selectedNodeId(tree:JTree)=((tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? NodeRef)?.id
    private fun refreshTrees(q:String=""){
        val scope=searchScope.selectedItem?.toString()?:"ALL";val all=if(q.isBlank())controller.allPrompts() else controller.promptSearch(q)
        buildTree(localTree,"MY PROMPTS",all.filter{!it.readOnly&&(scope=="ALL"||scope=="MY PROMPTS")});buildTree(premadeTree,"PREMADE PROMPTS",all.filter{it.readOnly&&(scope=="ALL"||scope=="PREMADE PROMPTS")})
    }
    private fun buildTree(tree:JTree,rootName:String,nodes:List<PromptAsset>){val root=DefaultMutableTreeNode(rootName);for(p in nodes.sortedBy{it.treePath+it.title}){var cur=root;for(seg in p.treePath.split('/').drop(1)){if(seg.isBlank())continue;var found:DefaultMutableTreeNode?=null;val en=cur.children();while(en.hasMoreElements()){val n=en.nextElement() as DefaultMutableTreeNode;if(n.userObject.toString()==seg){found=n;break}};cur=found?:DefaultMutableTreeNode(seg).also{cur.add(it)}};cur.add(DefaultMutableTreeNode(NodeRef(p.id,"${if(p.readOnly)"UPSTREAM · " else ""}${p.title}")))};tree.model=DefaultTreeModel(root);for(i in 0 until tree.rowCount)tree.expandRow(i)}
    private fun refreshVersions(){val refs=controller.versionNodes().map{VersionRef(it.id,(if(it.favorite)"★ " else "")+it.id.takeLast(8)+" / "+it.operation+" / "+it.name)};versionList.setListData(refs.toTypedArray())}
    private fun importPrompt(){val fc=JFileChooser();if(fc.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{controller.importPrompt(fc.selectedFile.toPath())}.onSuccess{loadPrompt(it.id);refreshTrees()}.onFailure(::showError)}
    private fun exportPrompt(){val id=selectedPromptId?:return showError(IllegalStateException("Select an editable local prompt first"));val fc=JFileChooser().apply{selectedFile=java.io.File("cosmosis-prompt.json")};if(fc.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;runCatching{controller.exportPrompt(id,fc.selectedFile.toPath())}.onFailure(::showError)}
    private fun parseList(text:String)=text.split(',','\n').map{it.trim()}.filter{it.isNotEmpty()}
    private fun parseTags(text:String)=parseList(text).toSet()
    private fun parseVariables(text:String)=text.lineSequence().mapNotNull{line->val i=line.indexOf('=');if(i<=0)null else line.substring(0,i).trim() to line.substring(i+1)}.toMap()
    private fun refreshOrmlStatus(){
        val d=controller.ormlDiagnostics()
        ormlStatus.text=buildString{
            d.statuses.forEach{(id,status)->appendLine(id+"  "+if(state.get().ormlEnabled)status else "DISABLED")}
            d.errors.forEach{appendLine("! "+it)}
            if(d.adapters.isEmpty())append("No external ORML runner/SPI adapter installed.")
        }
    }

    private fun refreshCapabilities(){
        val pid=provider.selectedItem?.toString()?:return
        val mid=model.editor.item?.toString()?.trim().orEmpty()
        if(mid.isBlank()){capabilityLabel.text="No model selected";return}
        runCatching{controller.capabilitiesFor(pid,mid)}.onSuccess{caps->
            capabilityLabel.text=listOf(
                "text→image="+caps.textToImage,"image→image="+caps.imageToImage,"mask="+caps.maskEditing,
                "multi-ref="+caps.multipleReferences+" / max "+caps.maxReferenceImages,"multi-turn="+caps.multiTurnEditing,
                "transparent="+caps.transparentBackground,"search="+caps.searchGrounding,
                "ratios="+caps.aspectRatios.joinToString().ifBlank{"provider default"},
                "sizes="+caps.imageSizes.joinToString().ifBlank{"provider default"},
                "formats="+caps.outputFormats.joinToString()
            ).joinToString("  │  ")
            transparentOutput.isEnabled=caps.transparentBackground;if(!caps.transparentBackground)transparentOutput.isSelected=false
            searchGrounding.isEnabled=caps.searchGrounding;if(!caps.searchGrounding)searchGrounding.isSelected=false
            imageSize.isEnabled=caps.imageSizes.isNotEmpty();if(!imageSize.isEnabled)imageSize.selectedItem=""
            aspectRatio.isEnabled=caps.aspectRatios.isNotEmpty()||caps.customDimensions;if(!aspectRatio.isEnabled)aspectRatio.text=""
            outputWidth.isEnabled=caps.customDimensions;outputHeight.isEnabled=caps.customDimensions
            if(!caps.customDimensions){outputWidth.text="";outputHeight.text=""}
            val currentFormat=outputFormat.selectedItem?.toString()
            outputFormat.removeAllItems();caps.outputFormats.sorted().forEach(outputFormat::addItem)
            if(currentFormat!=null&&currentFormat in caps.outputFormats)outputFormat.selectedItem=currentFormat else if(outputFormat.itemCount>0)outputFormat.selectedIndex=0
            val responses=pid.equals("openai",true);providerWorkflow.isEnabled=responses;reasoningModel.isEnabled=responses
            if(!responses){providerWorkflow.selectedItem="direct";reasoningModel.text=""}
        }.onFailure{capabilityLabel.text="Unavailable: "+it.message}
    }
    private fun cropDialog(){val s=state.get();if(s.imagePath==null)return showError(IllegalStateException("Import/select an image first"));val x=JTextField("0");val y=JTextField("0");val w=JTextField(s.imageWidth.toString());val h=JTextField(s.imageHeight.toString());val form=JPanel(GridLayout(0,2,8,8)).apply{add(label("X"));add(x);add(label("Y"));add(y);add(label("WIDTH"));add(w);add(label("HEIGHT"));add(h)};if(JOptionPane.showConfirmDialog(this,form,"CROP / PIXELS",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;runCatching{controller.cropCurrent(x.text.toInt(),y.text.toInt(),w.text.toInt(),h.text.toInt())}.onFailure(::showError)}
    private fun installKeys(){
        val dispatcher=KeyEventDispatcher{e->
        if(e.id!=KeyEvent.KEY_PRESSED)return@addKeyEventDispatcher false
        val ctrl=e.isControlDown||e.isMetaDown
        val focus=KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        val typing=focus is JTextComponent || focus is JComboBox<*>
        when{
            ctrl&&e.keyCode==KeyEvent.VK_Z&&prompt.hasFocus()->{if(e.isShiftDown){if(undo.canRedo())undo.redo()}else if(undo.canUndo())undo.undo();true}
            ctrl&&e.keyCode==KeyEvent.VK_Y&&prompt.hasFocus()->{if(undo.canRedo())undo.redo();true}
            ctrl&&e.keyCode==KeyEvent.VK_S->{savePrompt();true}
            ctrl&&e.keyCode==KeyEvent.VK_ENTER->{runSelectedWorkflow();true}
            ctrl&&e.keyCode==KeyEvent.VK_K->{openCommandPalette();true}
            ctrl&&e.keyCode==KeyEvent.VK_V&&!typing->{runCatching{controller.importClipboardImage()}.onFailure(::showError);true}
            !typing&&e.keyCode==KeyEvent.VK_G->{workflowMode.selectedItem=WorkflowMode.QUICK_GENERATE;controller.setWorkflowMode(WorkflowMode.QUICK_GENERATE);true}
            !typing&&e.keyCode==KeyEvent.VK_E->{workflowMode.selectedItem=WorkflowMode.EDIT_EXISTING;controller.setWorkflowMode(WorkflowMode.EDIT_EXISTING);true}
            !typing&&e.keyCode==KeyEvent.VK_M->{workflowMode.selectedItem=WorkflowMode.MASK_EDIT;controller.setWorkflowMode(WorkflowMode.MASK_EDIT);true}
            !typing&&e.keyCode==KeyEvent.VK_C->{controller.setCompareVersion(if(state.get().compareMode=="OFF")versionList.selectedValue?.id else null);true}
            !typing&&e.keyCode==KeyEvent.VK_0->{controller.resetView();true}
            else->false
        }
        }
        keyDispatcher=dispatcher
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
    }
    private fun applyTheme(){installOffworldDefaults()}
    private fun applyThemeToTree(root:Component){
        val field=Color(0x13,0x13,0x10)
        val text=Color(0x0D,0x0D,0x0B)
        val panel=Color(0x1C,0x1C,0x18)
        val line=BorderFactory.createLineBorder(OffworldTheme.hairlineNormal)
        when(root){
            is JTextComponent->{root.background=if(root is JTextArea)text else field;root.foreground=OffworldTheme.foreground;root.caretColor=OffworldTheme.foreground;root.selectionColor=OffworldTheme.secondary;root.selectedTextColor=OffworldTheme.foreground;root.border=line}
            is JComboBox<*>->{root.background=field;root.foreground=OffworldTheme.foreground;root.border=line}
            is JSpinner->{root.background=field;root.foreground=OffworldTheme.foreground;root.border=line}
            is JButton->{root.background=panel;root.foreground=OffworldTheme.foreground;root.isFocusPainted=false;root.border=line}
            is JCheckBox->{root.background=OffworldTheme.background;root.foreground=OffworldTheme.foreground;root.isFocusPainted=false}
            is JTree->{root.background=field;root.foreground=OffworldTheme.foreground;root.border=line}
            is JList<*>->{root.background=field;root.foreground=OffworldTheme.foreground;root.border=line}
            is JScrollPane->{root.background=OffworldTheme.background;root.viewport.background=field;root.border=line}
            is JSplitPane->{root.background=OffworldTheme.background;root.dividerSize=1;root.border=null}
            is JTabbedPane->{root.background=OffworldTheme.background;root.foreground=OffworldTheme.foreground;root.border=line}
            is JPanel->root.background=OffworldTheme.background
        }
        if(root is Container)root.components.forEach(::applyThemeToTree)
    }
    private fun panel(layout:LayoutManager=FlowLayout()):JPanel=JPanel(layout).apply{background=OffworldTheme.background;border=BorderFactory.createEmptyBorder(13,13,13,13)}
    private fun label(s:String)=JLabel(s).apply{font=Font(Font.MONOSPACED,Font.PLAIN,11);foreground=OffworldTheme.muted}
    private fun button(p:Container,text:String,action:()->Unit){p.add(JButton(text).apply{font=Font(Font.MONOSPACED,Font.PLAIN,11);isFocusPainted=false;border=BorderFactory.createLineBorder(OffworldTheme.secondary);addActionListener{action()}})}
    private fun showError(t:Throwable){JOptionPane.showMessageDialog(this,t.message?:t.toString(),"COSMOSIS / ERROR",JOptionPane.ERROR_MESSAGE)}
    data class NodeRef(val id:String,val label:String){override fun toString()=label}
    data class VersionRef(val id:String,val label:String){override fun toString()=label}
    data class ReferenceRef(val id:String,val label:String){override fun toString()=label}
    data class DirectiveRef(val id:String,val label:String){override fun toString()=label}
    data class JobRef(val id:String,val label:String){override fun toString()=label}
    data class CommandRef(val label:String,val action:()->Unit){override fun toString()=label}
}
