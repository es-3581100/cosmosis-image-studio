package studio.cosmosis.storage

import studio.cosmosis.*
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class SqliteStore(path: Path) : AutoCloseable {
    private val conn: Connection

    init {
        Class.forName("org.sqlite.JDBC")
        conn = DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")
        conn.autoCommit = true
        conn.createStatement().use {
            it.execute("PRAGMA foreign_keys=ON")
            it.execute("PRAGMA journal_mode=WAL")
        }
    }

    fun migrate() {
        val ddl = listOf(
            "CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS projects(id TEXT PRIMARY KEY,name TEXT NOT NULL,root TEXT NOT NULL,current_version_id TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS prompts(id TEXT PRIMARY KEY,title TEXT NOT NULL,summary TEXT NOT NULL,body TEXT NOT NULL,negative_constraints TEXT NOT NULL,intent TEXT NOT NULL,workflow TEXT NOT NULL,tree_path TEXT NOT NULL,origin TEXT NOT NULL,source TEXT,source_url TEXT,source_version TEXT,source_license TEXT,parent_id TEXT,derived_from TEXT,notes TEXT NOT NULL,explicit_keywords TEXT NOT NULL,imported_keywords TEXT NOT NULL,regex_keywords TEXT NOT NULL,semantic_keywords TEXT NOT NULL,visual_keywords TEXT NOT NULL,favorite INTEGER NOT NULL,read_only INTEGER NOT NULL,deleted_at TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,provider_hints TEXT NOT NULL DEFAULT '',model_hints TEXT NOT NULL DEFAULT '',aspect_ratio_hints TEXT NOT NULL DEFAULT '',style_tags TEXT NOT NULL DEFAULT '',subject_tags TEXT NOT NULL DEFAULT '',composition_tags TEXT NOT NULL DEFAULT '',lighting_tags TEXT NOT NULL DEFAULT '',camera_tags TEXT NOT NULL DEFAULT '',material_tags TEXT NOT NULL DEFAULT '',text_rendering_tags TEXT NOT NULL DEFAULT '',variables TEXT NOT NULL DEFAULT '',reference_image_hints TEXT NOT NULL DEFAULT '')",
            "CREATE TABLE IF NOT EXISTS prompt_revisions(id TEXT PRIMARY KEY,prompt_id TEXT NOT NULL,snapshot TEXT NOT NULL,created_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS assets(id TEXT PRIMARY KEY,kind TEXT NOT NULL,path TEXT NOT NULL,mime TEXT NOT NULL,width INTEGER NOT NULL,height INTEGER NOT NULL,sha256 TEXT NOT NULL,source_asset_id TEXT,provenance TEXT NOT NULL,created_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS masks(id TEXT PRIMARY KEY,source_asset_id TEXT NOT NULL,path TEXT NOT NULL,width INTEGER NOT NULL,height INTEGER NOT NULL,feather_radius INTEGER NOT NULL,inverted INTEGER NOT NULL,method TEXT NOT NULL,metadata TEXT NOT NULL,created_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS versions(id TEXT PRIMARY KEY,parent_id TEXT,asset_id TEXT NOT NULL,operation TEXT NOT NULL,name TEXT NOT NULL,prompt_id TEXT,generation_id TEXT,favorite INTEGER NOT NULL,created_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS generations(id TEXT PRIMARY KEY,parent_image_id TEXT,input_image_ids TEXT NOT NULL,mask_id TEXT,prompt_id TEXT,user_prompt TEXT NOT NULL,compiled_prompt TEXT NOT NULL,provider_id TEXT NOT NULL,model TEXT NOT NULL,endpoint TEXT NOT NULL,settings TEXT NOT NULL,output_image_ids TEXT NOT NULL,duration_ms INTEGER NOT NULL,retry_count INTEGER NOT NULL,worker_id TEXT NOT NULL,provider_revised_prompt TEXT,provider_context_id TEXT,error TEXT,created_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS jobs(id TEXT PRIMARY KEY,type TEXT NOT NULL,state TEXT NOT NULL,payload TEXT NOT NULL,max_retries INTEGER NOT NULL,retry_count INTEGER NOT NULL,error TEXT,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS directives(id TEXT PRIMARY KEY,title TEXT NOT NULL,body TEXT NOT NULL,enabled INTEGER NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)"
        )
        ddl.forEach { sql -> conn.createStatement().use { it.execute(sql) } }
        ensureColumn("generations", "provider_context_id", "TEXT")
        ensureColumn("prompts", "provider_hints", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "model_hints", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "aspect_ratio_hints", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "style_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "subject_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "composition_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "lighting_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "camera_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "material_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "text_rendering_tags", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "variables", "TEXT NOT NULL DEFAULT ''")
        ensureColumn("prompts", "reference_image_hints", "TEXT NOT NULL DEFAULT ''")
        conn.prepareStatement("INSERT OR REPLACE INTO meta(key,value) VALUES('schema_version','5')").use { it.executeUpdate() }
    }

    fun loadProject(): ProjectRecord? = conn.createStatement().use { s ->
        s.executeQuery("SELECT * FROM projects ORDER BY updated_at DESC LIMIT 1").use { r ->
            if (!r.next()) null else ProjectRecord(
                id=r.getString("id"), name=r.getString("name"), root=r.getString("root"), currentVersionId=r.getString("current_version_id"),
                createdAt=r.getString("created_at"), updatedAt=r.getString("updated_at")
            )
        }
    }

    fun saveProject(p: ProjectRecord) {
        conn.prepareStatement("INSERT OR REPLACE INTO projects VALUES(?,?,?,?,?,?)").use { s ->
            s.setString(1,p.id); s.setString(2,p.name); s.setString(3,p.root); s.setString(4,p.currentVersionId); s.setString(5,p.createdAt); s.setString(6,p.updatedAt); s.executeUpdate()
        }
    }

    fun savePrompt(p: PromptAsset) {
        val columns=listOf("id","title","summary","body","negative_constraints","intent","workflow","tree_path","origin","source","source_url","source_version","source_license","parent_id","derived_from","notes","explicit_keywords","imported_keywords","regex_keywords","semantic_keywords","visual_keywords","favorite","read_only","deleted_at","created_at","updated_at","provider_hints","model_hints","aspect_ratio_hints","style_tags","subject_tags","composition_tags","lighting_tags","camera_tags","material_tags","text_rendering_tags","variables","reference_image_hints")
        val sql="INSERT OR REPLACE INTO prompts(${columns.joinToString()}) VALUES(${List(columns.size){"?"}.joinToString()})"
        conn.prepareStatement(sql).use { s ->
            var i=1
            listOf<String?>(p.id,p.title,p.summary,p.body,p.negativeConstraints,p.intent,p.workflow,p.treePath,p.origin.name,p.source,p.sourceUrl,p.sourceVersion,p.sourceLicense,p.parentId,p.derivedFrom,p.notes,Codec.set(p.explicitKeywords),Codec.set(p.importedKeywords),Codec.set(p.regexKeywords),Codec.set(p.semanticKeywords),Codec.set(p.visualKeywords)).forEach { s.setString(i++,it) }
            s.setInt(i++,if(p.favorite)1 else 0);s.setInt(i++,if(p.readOnly)1 else 0);s.setString(i++,p.deletedAt);s.setString(i++,p.createdAt);s.setString(i++,p.updatedAt)
            listOf(Codec.list(p.providerHints),Codec.list(p.modelHints),Codec.list(p.aspectRatioHints),Codec.list(p.styleTags),Codec.list(p.subjectTags),Codec.list(p.compositionTags),Codec.list(p.lightingTags),Codec.list(p.cameraTags),Codec.list(p.materialTags),Codec.list(p.textRenderingTags),Codec.map(p.variables),Codec.list(p.referenceImageHints)).forEach{s.setString(i++,it)}
            s.executeUpdate()
        }
    }
    fun loadPrompts(): List<PromptAsset> {
        val out=mutableListOf<PromptAsset>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM prompts ORDER BY title").use { r ->
            while(r.next()) out += PromptAsset(
                id=r.getString("id"), title=r.getString("title"), summary=r.getString("summary"), body=r.getString("body"), negativeConstraints=r.getString("negative_constraints"), intent=r.getString("intent"), workflow=r.getString("workflow"), treePath=r.getString("tree_path"), origin=PromptOrigin.valueOf(r.getString("origin")), source=r.getString("source"), sourceUrl=r.getString("source_url"), sourceVersion=r.getString("source_version"), sourceLicense=r.getString("source_license"), parentId=r.getString("parent_id"), derivedFrom=r.getString("derived_from"), notes=r.getString("notes"), providerHints=Codec.listDecoded(r.getString("provider_hints")), modelHints=Codec.listDecoded(r.getString("model_hints")), aspectRatioHints=Codec.listDecoded(r.getString("aspect_ratio_hints")), styleTags=Codec.listDecoded(r.getString("style_tags")), subjectTags=Codec.listDecoded(r.getString("subject_tags")), compositionTags=Codec.listDecoded(r.getString("composition_tags")), lightingTags=Codec.listDecoded(r.getString("lighting_tags")), cameraTags=Codec.listDecoded(r.getString("camera_tags")), materialTags=Codec.listDecoded(r.getString("material_tags")), textRenderingTags=Codec.listDecoded(r.getString("text_rendering_tags")), variables=Codec.map(r.getString("variables")), referenceImageHints=Codec.listDecoded(r.getString("reference_image_hints")), explicitKeywords=Codec.set(r.getString("explicit_keywords")), importedKeywords=Codec.set(r.getString("imported_keywords")), regexKeywords=Codec.set(r.getString("regex_keywords")), semanticKeywords=Codec.set(r.getString("semantic_keywords")), visualKeywords=Codec.set(r.getString("visual_keywords")), favorite=r.getInt("favorite")!=0, readOnly=r.getInt("read_only")!=0, deletedAt=r.getString("deleted_at"), createdAt=r.getString("created_at"), updatedAt=r.getString("updated_at")
            )
        }}
        return out
    }

    fun savePromptRevision(r: PromptRevision) {
        conn.prepareStatement("INSERT OR REPLACE INTO prompt_revisions VALUES(?,?,?,?)").use { s -> s.setString(1,r.id);s.setString(2,r.promptId);s.setString(3,PromptSnapshotCodec.encode(r.snapshot));s.setString(4,r.createdAt);s.executeUpdate() }
    }
    fun loadPromptRevisions(): List<PromptRevision> {
        val out=mutableListOf<PromptRevision>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM prompt_revisions ORDER BY created_at").use { r -> while(r.next()) out += PromptRevision(r.getString("id"),r.getString("prompt_id"),PromptSnapshotCodec.decode(r.getString("snapshot")),r.getString("created_at")) } }
        return out
    }

    fun saveAsset(a: ImageAsset) { conn.prepareStatement("INSERT OR REPLACE INTO assets VALUES(?,?,?,?,?,?,?,?,?,?)").use { s -> s.setString(1,a.id);s.setString(2,a.kind.name);s.setString(3,a.path);s.setString(4,a.mime);s.setInt(5,a.width);s.setInt(6,a.height);s.setString(7,a.sha256);s.setString(8,a.sourceAssetId);s.setString(9,a.provenance);s.setString(10,a.createdAt);s.executeUpdate() } }
    fun loadAssets(): List<ImageAsset> {
        val out=mutableListOf<ImageAsset>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM assets ORDER BY created_at").use { r -> while(r.next()) out += ImageAsset(r.getString("id"),AssetKind.valueOf(r.getString("kind")),r.getString("path"),r.getString("mime"),r.getInt("width"),r.getInt("height"),r.getString("sha256"),r.getString("source_asset_id"),r.getString("provenance"),r.getString("created_at")) } }
        return out
    }

    fun saveMask(m: MaskRecord) { conn.prepareStatement("INSERT OR REPLACE INTO masks VALUES(?,?,?,?,?,?,?,?,?,?)").use { s -> s.setString(1,m.id);s.setString(2,m.sourceAssetId);s.setString(3,m.path);s.setInt(4,m.width);s.setInt(5,m.height);s.setInt(6,m.featherRadius);s.setInt(7,if(m.inverted)1 else 0);s.setString(8,m.method);s.setString(9,Codec.map(m.derivedMetadata));s.setString(10,m.createdAt);s.executeUpdate() } }
    fun loadMasks(): List<MaskRecord> {
        val out=mutableListOf<MaskRecord>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM masks ORDER BY created_at").use { r -> while(r.next()) out += MaskRecord(r.getString("id"),r.getString("source_asset_id"),r.getString("path"),r.getInt("width"),r.getInt("height"),r.getInt("feather_radius"),r.getInt("inverted")!=0,r.getString("method"),Codec.map(r.getString("metadata")),r.getString("created_at")) } }
        return out
    }

    fun saveVersion(v: VersionNode) { conn.prepareStatement("INSERT OR REPLACE INTO versions VALUES(?,?,?,?,?,?,?,?,?)").use { s -> s.setString(1,v.id);s.setString(2,v.parentId);s.setString(3,v.assetId);s.setString(4,v.operation.name);s.setString(5,v.name);s.setString(6,v.promptId);s.setString(7,v.generationId);s.setInt(8,if(v.favorite)1 else 0);s.setString(9,v.createdAt);s.executeUpdate() } }
    fun loadVersions(): List<VersionNode> {
        val out=mutableListOf<VersionNode>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM versions ORDER BY created_at").use { r -> while(r.next()) out += VersionNode(r.getString("id"),r.getString("parent_id"),r.getString("asset_id"),VersionOperation.valueOf(r.getString("operation")),r.getString("name"),r.getString("prompt_id"),r.getString("generation_id"),r.getInt("favorite")!=0,r.getString("created_at")) } }
        return out
    }

    fun saveGeneration(g: GenerationRecord) {
        val sql="""INSERT OR REPLACE INTO generations(id,parent_image_id,input_image_ids,mask_id,prompt_id,user_prompt,compiled_prompt,provider_id,model,endpoint,settings,output_image_ids,duration_ms,retry_count,worker_id,provider_revised_prompt,provider_context_id,error,created_at) VALUES(${List(19){"?"}.joinToString()})"""
        conn.prepareStatement(sql).use { s ->
            var i=1
            listOf<String?>(g.id,g.parentImageId,Codec.list(g.inputImageIds),g.maskId,g.promptId,g.userPrompt,g.compiledPrompt,g.providerId,g.model,g.endpoint,Codec.map(g.settings),Codec.list(g.outputImageIds)).forEach { s.setString(i++,it) }
            s.setLong(i++,g.durationMs);s.setInt(i++,g.retryCount);s.setString(i++,g.workerId);s.setString(i++,g.providerRevisedPrompt);s.setString(i++,g.providerContextId);s.setString(i++,g.error);s.setString(i,g.createdAt);s.executeUpdate()
        }
    }
    fun loadGenerations(): List<GenerationRecord> {
        val out=mutableListOf<GenerationRecord>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM generations ORDER BY created_at").use { r -> while(r.next()) out += GenerationRecord(id=r.getString("id"),parentImageId=r.getString("parent_image_id"),inputImageIds=Codec.listDecoded(r.getString("input_image_ids")),maskId=r.getString("mask_id"),promptId=r.getString("prompt_id"),userPrompt=r.getString("user_prompt"),compiledPrompt=r.getString("compiled_prompt"),providerId=r.getString("provider_id"),model=r.getString("model"),endpoint=r.getString("endpoint"),settings=Codec.map(r.getString("settings")),outputImageIds=Codec.listDecoded(r.getString("output_image_ids")),durationMs=r.getLong("duration_ms"),retryCount=r.getInt("retry_count"),workerId=r.getString("worker_id"),providerRevisedPrompt=r.getString("provider_revised_prompt"),providerContextId=r.getString("provider_context_id"),error=r.getString("error"),createdAt=r.getString("created_at")) } }
        return out
    }

    fun saveJob(j: WorkerJob) { conn.prepareStatement("INSERT OR REPLACE INTO jobs VALUES(?,?,?,?,?,?,?,?,?)").use { s -> s.setString(1,j.id);s.setString(2,j.type);s.setString(3,j.state.name);s.setString(4,Codec.map(j.payload));s.setInt(5,j.maxRetries);s.setInt(6,j.retryCount);s.setString(7,j.error);s.setString(8,j.createdAt);s.setString(9,j.updatedAt);s.executeUpdate() } }
    fun recoverInterruptedJobs(): Int = conn.prepareStatement("UPDATE jobs SET state='INTERRUPTED', updated_at=? WHERE state='RUNNING'").use { s -> s.setString(1,nowIso());s.executeUpdate() }
    fun loadJobs(): List<WorkerJob> {
        val out=mutableListOf<WorkerJob>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM jobs ORDER BY created_at").use { r -> while(r.next()) out += WorkerJob(r.getString("id"),r.getString("type"),JobState.valueOf(r.getString("state")),Codec.map(r.getString("payload")),r.getInt("max_retries"),r.getInt("retry_count"),r.getString("error"),r.getString("created_at"),r.getString("updated_at")) } }
        return out
    }

    private fun ensureColumn(table:String,column:String,definition:String) {
        val exists=conn.createStatement().use { s -> s.executeQuery("PRAGMA table_info($table)").use { r -> generateSequence { if(r.next()) r.getString("name") else null }.any { it==column } } }
        if(!exists) conn.createStatement().use { it.execute("ALTER TABLE $table ADD COLUMN $column $definition") }
    }

    fun saveDirective(d: AgentDirective) {
        conn.prepareStatement("INSERT OR REPLACE INTO directives(id,title,body,enabled,created_at,updated_at) VALUES(?,?,?,?,?,?)").use { s ->
            s.setString(1,d.id);s.setString(2,d.title);s.setString(3,d.body);s.setInt(4,if(d.enabled)1 else 0);s.setString(5,d.createdAt);s.setString(6,d.updatedAt);s.executeUpdate()
        }
    }
    fun loadDirectives(): List<AgentDirective> {
        val out=mutableListOf<AgentDirective>()
        conn.createStatement().use { s -> s.executeQuery("SELECT * FROM directives ORDER BY title").use { r ->
            while(r.next()) out += AgentDirective(r.getString("id"),r.getString("title"),r.getString("body"),r.getInt("enabled")!=0,r.getString("created_at"),r.getString("updated_at"))
        }}
        return out
    }
    fun deleteDirective(id:String) {
        conn.prepareStatement("DELETE FROM directives WHERE id=?").use { it.setString(1,id);it.executeUpdate() }
    }

    fun putSetting(key:String,value:String) { conn.prepareStatement("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)").use { it.setString(1,key);it.setString(2,value);it.executeUpdate() } }
    fun getSetting(key:String):String? = conn.prepareStatement("SELECT value FROM settings WHERE key=?").use { s -> s.setString(1,key);s.executeQuery().use { if(it.next()) it.getString(1) else null } }
    override fun close() = conn.close()
}

private object Codec {
    private const val SEP='\u001f'; private const val KV='\u001e'
    private fun enc(s:String)=s.replace("%","%25").replace(SEP.toString(),"%1F").replace(KV.toString(),"%1E")
    private fun dec(s:String)=s.replace("%1F",SEP.toString()).replace("%1E",KV.toString()).replace("%25","%")
    fun list(v:Collection<String>)=v.joinToString(SEP.toString()){enc(it)}
    fun listDecoded(s:String?):List<String> = if(s.isNullOrEmpty()) emptyList() else s.split(SEP).map(::dec)
    fun set(v:Set<String>)=list(v)
    fun set(s:String?):Set<String> = listDecoded(s).toCollection(linkedSetOf())
    fun map(v:Map<String,String>)=v.entries.joinToString(SEP.toString()){enc(it.key)+KV+enc(it.value)}
    fun map(s:String?):Map<String,String> = if(s.isNullOrEmpty()) emptyMap() else s.split(SEP).associate { part -> val i=part.indexOf(KV);if(i<0) dec(part) to "" else dec(part.substring(0,i)) to dec(part.substring(i+1)) }
}

private object PromptSnapshotCodec {
    private const val RS='\u001d';private const val FS='\u001c'
    private fun e(s:String?)=(s?:"").replace("%","%25").replace(RS.toString(),"%1D").replace(FS.toString(),"%1C")
    private fun d(s:String)=s.replace("%1D",RS.toString()).replace("%1C",FS.toString()).replace("%25","%")
    private fun list(v:Collection<String>)=v.joinToString(FS.toString()){e(it)}
    private fun list(s:String)=if(s.isEmpty()) emptyList() else s.split(FS).map(::d)
    private fun map(v:Map<String,String>)=v.entries.joinToString(FS.toString()){e(it.key)+"="+e(it.value).replace("=","%3D")}
    private fun map(s:String)=if(s.isEmpty()) emptyMap() else s.split(FS).associate{val i=it.indexOf('=');if(i<0)d(it) to "" else d(it.substring(0,i)) to d(it.substring(i+1).replace("%3D","="))}
    fun encode(p:PromptAsset)=listOf(p.id,p.title,p.summary,p.body,p.negativeConstraints,p.intent,p.workflow,p.treePath,p.origin.name,p.source,p.sourceUrl,p.sourceVersion,p.sourceLicense,p.parentId,p.derivedFrom,p.notes,p.explicitKeywords.joinToString(","),p.importedKeywords.joinToString(","),p.regexKeywords.joinToString(","),p.semanticKeywords.joinToString(","),p.visualKeywords.joinToString(","),p.favorite.toString(),p.readOnly.toString(),p.deletedAt,p.createdAt,p.updatedAt,list(p.providerHints),list(p.modelHints),list(p.aspectRatioHints),list(p.styleTags),list(p.subjectTags),list(p.compositionTags),list(p.lightingTags),list(p.cameraTags),list(p.materialTags),list(p.textRenderingTags),map(p.variables),list(p.referenceImageHints)).joinToString(RS.toString()){e(it)}
    fun decode(s:String):PromptAsset {
        val a=s.split(RS).map(::d).toMutableList(); while(a.size<38) a.add("")
        fun tags(i:Int)=a[i].split(',').map{it.trim()}.filter{it.isNotEmpty()}.toSet()
        return PromptAsset(id=a[0],title=a[1],summary=a[2],body=a[3],negativeConstraints=a[4],intent=a[5],workflow=a[6],treePath=a[7],origin=runCatching{PromptOrigin.valueOf(a[8])}.getOrDefault(PromptOrigin.LOCAL),source=a[9].ifEmpty{null},sourceUrl=a[10].ifEmpty{null},sourceVersion=a[11].ifEmpty{null},sourceLicense=a[12].ifEmpty{null},parentId=a[13].ifEmpty{null},derivedFrom=a[14].ifEmpty{null},notes=a[15],explicitKeywords=tags(16),importedKeywords=tags(17),regexKeywords=tags(18),semanticKeywords=tags(19),visualKeywords=tags(20),favorite=a[21].toBoolean(),readOnly=a[22].toBoolean(),deletedAt=a[23].ifEmpty{null},createdAt=a[24],updatedAt=a[25],providerHints=list(a[26]),modelHints=list(a[27]),aspectRatioHints=list(a[28]),styleTags=list(a[29]),subjectTags=list(a[30]),compositionTags=list(a[31]),lightingTags=list(a[32]),cameraTags=list(a[33]),materialTags=list(a[34]),textRenderingTags=list(a[35]),variables=map(a[36]),referenceImageHints=list(a[37]))
    }
}
