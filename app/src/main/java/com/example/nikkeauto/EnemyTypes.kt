package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/**
 * Supplementary enemy taxonomy. Independent of the proven legacy detectors.
 * Classifications are candidates, not instructions to click blindly.
 */
object EnemyTypes {
 enum class Kind {
  RED_RING, ORANGE_MARKER, CYAN_MARKED_BLUE_CORE, LARGE_RED_BEAM
 }
 data class Candidate(val kind:Kind,val x:Float,val y:Float,val confidence:Int)

 private fun red(c:Int):Boolean {
  val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
  return r>112 && r>g*1.13f && r>b*1.15f && g>25
 }
 private fun blue(c:Int):Boolean {
  val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
  return b>90 && b>r*1.08f && b>g*.90f
 }
 /** A visible cyan marker over a blue-center/red-outer-ring enemy.
  * Unlike off-screen marker navigation, this target is already in-frame.
  */
 fun cyanMarkedEnemy(bitmap:Bitmap,marker:MapDetector.Target?):Candidate? {
  if(marker==null)return null
  val w=bitmap.width;val h=bitmap.height
  val mx=marker.x.toInt();val my=marker.y.toInt()
  val step=max(3,w/150)
  var best:Candidate?=null
  // Screen-relative gap between icon and selected enemy base.
  for(y in my+(w*.12f).toInt()..min(h-1,my+(w*.34f).toInt()) step step*2)
   for(x in max(0,mx-(w*.09f).toInt())..min(w-1,mx+(w*.09f).toInt()) step step*2){
    if(!blue(bitmap.getPixel(x,y)))continue
    var inner=0
    for(dy in -2..2)for(dx in -2..2){
     val xx=x+dx*step;val yy=y+dy*step
     if(xx in 0 until w && yy in 0 until h &&
      blue(bitmap.getPixel(xx,yy)))inner++
    }
    if(inner<6)continue
    var ringBest=0
    for(rx in intArrayOf((w*.056f).toInt(),(w*.078f).toInt(),(w*.10f).toInt())){
     val ry=max(5,(rx*.60f).toInt())
     var ring=0
     for(k in 0 until 36){
      val theta=k*2.0*Math.PI/36
      val px=x+(rx*cos(theta)).toInt()
      val py=y+(ry*sin(theta)).toInt()
      if(px in 0 until w && py in 0 until h && red(bitmap.getPixel(px,py)))ring++
     }
     ringBest=max(ringBest,ring)
    }
    if(ringBest<8)continue
    val conf=ringBest*4+inner*2-abs(mx-x)/step
    // Center of monster body directly above its ground disc.
    val candidate=Candidate(Kind.CYAN_MARKED_BLUE_CORE,x.toFloat(),
     (y-w*.10f).coerceIn(h*.14f,h*.85f),conf)
    if(best==null || candidate.confidence>best.confidence)best=candidate
   }
  return best
 }
 /** Orange punctuation marker above a warm orange ground ring. */
 fun orangeMarkedEnemy(bitmap:Bitmap):Candidate? {
  val w=bitmap.width;val h=bitmap.height
  val step=max(4,w/125)
  var best:Candidate?=null
  for(y in (h*.15f).toInt() until (h*.68f).toInt() step step*2)
   for(x in (w*.08f).toInt() until (w*.90f).toInt() step step*2){
    val p=bitmap.getPixel(x,y)
    val r=Color.red(p);val g=Color.green(p);val b=Color.blue(p)
    if(r<165 || g<72 || g>r*.84f || b>g*.83f)continue
    var orange=0
    for(dy in -step*2..step*2 step step)
     for(dx in -step*2..step*2 step step){
      val xx=x+dx;val yy=y+dy
      if(xx !in 0 until w || yy !in 0 until h)continue
      val c=bitmap.getPixel(xx,yy)
      val rr=Color.red(c);val gg=Color.green(c);val bb=Color.blue(c)
      if(rr>150 && gg>65 && gg<rr*.85f && bb<gg*.95f)orange++
     }
    if(orange<5)continue
    var redBelow=0
    for(dy in (w*.11f).toInt()..(w*.27f).toInt() step step*2)
     for(dx in -(w*.10f).toInt()..(w*.10f).toInt() step step*2){
      val xx=x+dx;val yy=y+dy
      if(xx in 0 until w && yy in 0 until h && red(bitmap.getPixel(xx,yy)))redBelow++
     }
    if(redBelow<8)continue
    val conf=orange*2+redBelow
    val candidate=Candidate(Kind.ORANGE_MARKER,x.toFloat(),
     (y+w*.13f).coerceAtMost(h*.84f),conf)
    if(best==null || candidate.confidence>best.confidence)best=candidate
   }
  return best
 }
}
