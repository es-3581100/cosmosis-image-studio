package studio.cosmosis.storage

import studio.cosmosis.*
import java.nio.file.*
import java.security.MessageDigest
import javax.imageio.ImageIO

object ProjectStore {
    fun paths(root:Path):ProjectPaths = ProjectPaths(root,root.resolve("project.db"),root.resolve("assets/originals"),root.resolve("assets/generated"),root.resolve("assets/masks"),root.resolve("assets/previews"),root.resolve("assets/exports"),root.resolve("metadata"),root.resolve("docs"))
    fun create(root:Path,name:String):Pair<ProjectRecord,ProjectPaths> {
        val p=paths(root); listOf(p.root,p.originals,p.generated,p.masks,p.previews,p.exports,p.metadata,p.docs).forEach(Files::createDirectories)
        val project=ProjectRecord(name=name,root=root.toAbsolutePath().toString())
        Files.writeString(root.resolve("project.json"),"""{\n  \"id\": \"${project.id}\",\n  \"name\": \"${escape(name)}\",\n  \"createdAt\": \"${project.createdAt}\",\n  \"schema\": 1\n}\n""")
        SqliteStore(p.db).use { it.migrate(); it.saveProject(project) }
        return project to p
    }
    fun importImage(paths:ProjectPaths,source:Path,provenance:String="local-import"):ImageAsset {
        require(Files.isRegularFile(source)){"Image does not exist: $source"}; val img=requireNotNull(ImageIO.read(source.toFile())){"Unsupported image: $source"}
        val mime=when(source.fileName.toString().substringAfterLast('.',"").lowercase()){"jpg","jpeg"->"image/jpeg";"webp"->"image/webp";else->"image/png"}
        val hash=sha256(source); val ext=when(mime){"image/jpeg"->"jpg";"image/webp"->"webp";else->"png"}; val dest=paths.originals.resolve("$hash.$ext")
        if(!Files.exists(dest)) Files.copy(source,dest)
        return ImageAsset(kind=AssetKind.ORIGINAL,path=paths.root.relativize(dest).toString(),mime=mime,width=img.width,height=img.height,sha256=hash,provenance=provenance)
    }
    fun sha256(path:Path):String { val md=MessageDigest.getInstance("SHA-256"); Files.newInputStream(path).use{input->val b=ByteArray(8192);while(true){val n=input.read(b);if(n<0)break;md.update(b,0,n)}};return md.digest().joinToString(""){"%02x".format(it)} }
    private fun escape(s:String)=s.replace("\\","\\\\").replace("\"","\\\"")
}
