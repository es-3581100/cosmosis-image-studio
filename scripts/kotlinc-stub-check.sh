#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/kotlinx/coroutines/channels" "$TMP/kotlinx/coroutines" "$TMP/org/openrndr/color" "$TMP/org/openrndr/draw" "$TMP/org/openrndr/math" "$TMP/org/openrndr"
cat > "$TMP/kotlinx/coroutines/Core.kt" <<'KT'
package kotlinx.coroutines
class Ctx { operator fun plus(other:Ctx)=this }
fun SupervisorJob()=Ctx()
object Dispatchers { val IO=Ctx() }
class CoroutineScope(val ctx:Ctx) { fun cancel(){} }
class CancellationException(message:String):RuntimeException(message)
fun CoroutineScope.launch(block:suspend ()->Unit) {}
suspend fun <T> withTimeout(ms:Long,block:suspend ()->T):T=block()
suspend fun <T> withContext(ctx:Ctx,block:suspend ()->T):T=block()
KT
cat > "$TMP/kotlinx/coroutines/channels/Channel.kt" <<'KT'
package kotlinx.coroutines.channels
class ChannelResult<T>(private val value:T?=null,private val error:Throwable?=null) { fun getOrElse(block:(Throwable)->T):T=value?:block(error?:IllegalStateException("stub")) }
class Channel<T>(capacity:Int):Iterable<T> { fun trySend(value:T)=ChannelResult(Unit);fun close(){};override fun iterator():Iterator<T> = emptyList<T>().iterator() }
KT
cat > "$TMP/org/openrndr/math/Vector2.kt" <<'KT'
package org.openrndr.math
data class Vector2(val x:Double,val y:Double){operator fun plus(o:Vector2)=Vector2(x+o.x,y+o.y);operator fun minus(o:Vector2)=Vector2(x-o.x,y-o.y);companion object{val ZERO=Vector2(0.0,0.0)}}
KT
cat > "$TMP/org/openrndr/color/ColorRGBa.kt" <<'KT'
package org.openrndr.color
data class ColorRGBa(val r:Double,val g:Double,val b:Double,val alpha:Double=1.0)
KT
cat > "$TMP/org/openrndr/draw/Draw.kt" <<'KT'
package org.openrndr.draw
class ColorBuffer(val width:Int=100,val height:Int=100){fun destroy(){}}
class FontMap
fun loadFont(path:String,size:Double)=FontMap()
fun loadImage(path:String)=ColorBuffer()
KT
cat > "$TMP/org/openrndr/Core.kt" <<'KT'
package org.openrndr
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.ColorBuffer
import org.openrndr.draw.FontMap
import org.openrndr.math.Vector2
class Event<T>{fun listen(block:(T)->Unit){}}
class DropEvent(val files:List<String> = emptyList())
class ScrollEvent(val rotation:Vector2 = Vector2.ZERO)
class MouseEvent(val position:Vector2 = Vector2.ZERO)
class KeyModifier(val name:String)
class KeyEvent(val name:String="",val modifiers:List<KeyModifier> = emptyList())
class Window{val drop=Event<DropEvent>()}
class Mouse{val scrolled=Event<ScrollEvent>();val buttonDown=Event<MouseEvent>();val buttonUp=Event<MouseEvent>();val dragged=Event<MouseEvent>()}
class Keyboard{val keyDown=Event<KeyEvent>()}
class Drawer{var stroke:ColorRGBa?=null;var strokeWeight:Double=1.0;var fill:ColorRGBa?=null;var fontMap:FontMap?=null;fun clear(c:ColorRGBa){};fun lineSegment(x1:Double,y1:Double,x2:Double,y2:Double){};fun rectangle(x:Double,y:Double,w:Double,h:Double){};fun text(s:String,x:Double,y:Double){};fun image(i:ColorBuffer,x:Double,y:Double,w:Double,h:Double){}}
class Program{val window=Window();val mouse=Mouse();val keyboard=Keyboard();val drawer=Drawer();val width:Int=1480;val height:Int=900;fun extend(block:Program.()->Unit){}}
class Configuration{var width:Int=0;var height:Int=0;var title:String=""}
class ApplicationBuilder{fun configure(block:Configuration.()->Unit){Configuration().block()};fun program(block:Program.()->Unit){Program().block()}}
fun application(block:ApplicationBuilder.()->Unit){ApplicationBuilder().block()}
KT
FILES=$(find src/main/kotlin/studio/cosmosis -name '*.kt' ! -name 'LiveSmoke.kt' | tr '\n' ' ')
kotlinc $FILES "$TMP"/kotlinx/coroutines/Core.kt "$TMP"/kotlinx/coroutines/channels/Channel.kt "$TMP"/org/openrndr/math/Vector2.kt "$TMP"/org/openrndr/color/ColorRGBa.kt "$TMP"/org/openrndr/draw/Draw.kt "$TMP"/org/openrndr/Core.kt -d "$TMP/check.jar"
echo "KOTLINC_STUB_CHECK_PASS"
