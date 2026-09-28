package studio.cosmosis

import kotlin.test.*
import studio.cosmosis.storage.SqliteStore
import java.nio.file.Files

class SqliteRecoveryTest {
    @Test fun runningJobsBecomeInterruptedAndPromptRevisionsPersist(){
        val dir=Files.createTempDirectory("offworld-db")
        SqliteStore(dir.resolve("project.db")).use{db->
            db.migrate()
            val j=WorkerJob(type="image-generate",state=JobState.RUNNING)
            db.saveJob(j)
            assertEquals(1,db.recoverInterruptedJobs())
            assertEquals(JobState.INTERRUPTED,db.loadJobs().single().state)
            val lib=studio.cosmosis.prompt.PromptLibrary()
            val p=lib.create(PromptAsset(title="A",body="one"))
            db.savePrompt(p)
            lib.update(p.id){it.body="two"}
            db.savePrompt(p)
            db.savePromptRevision(lib.revisions(p.id).first())
            assertEquals("one",db.loadPromptRevisions().single().snapshot.body)
        }
    }
}
