package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Lightweight pixel-based detector for red rings under outdoor monsters.
 * Coordinates are in the original MediaProjection bitmap space.
 * Requires an outdoor-map UI confirmation from the caller.
 */
object MapDetector {
 data class Target(val x:Float, val y:Float, val score:Int)
 fun redRingMonster(bitmap:Bitmap):Target? {
  val w=bitmap.width;val h=bitmap.height
  // Scan in a small grid and cluster red/orange pixels around elliptical ground rings.
  // Exclude HUD, minimap, and bottom navigation panel.
  val step=max(2,min(w,h)/250)
  val cw=(w+step-1)/step;val ch=(h+step-1)/step
  val mask=BooleanArray(cw*ch)
  val minY=(h*.13f).toInt()/step
  val maxY=(h*.88f).toInt()/step
  for(gy in minY until min(maxY,ch)) for(gx in 0 until cw) {
   val x=min(w-1,gx*step);val y=min(h-1,gy*step)
   if(x<w*.21f && y<h*.28f) continue
   if(x>w*.77f && y<h*.13f) continue
   val c=bitmap.getPixel(x,y)
   val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
   // Orange-red encounter circle: high red, some green, weak blue.
   mask[gy*cw+gx]=(r>145 && r>g*1.25f && r>b*1.45f && g>35 && g<165)
  }
  val seen=BooleanArray(mask.size)
  val queue=IntArray(mask.size)
  var best:Target?=null
  for(start in mask.indices){
   if(!mask[start] || seen[start])continue
   var head=0;var tail=1;queue[0]=start;seen[start]=true
   var minX=cw;var maxX=0;var minRow=ch;var maxRow=0;var count=0
   while(head<tail){
    val idx=queue[head++];val x=idx%cw;val y=idx/cw;count++
    minX=min(minX,x);maxX=max(maxX,x);minRow=min(minRow,y);maxRow=max(maxRow,y)
    for(dy in -1..1)for(dx in -1..1){
     val nx=x+dx;val ny=y+dy
     if(nx in 0 until cw && ny in 0 until ch){
      val ni=ny*cw+nx
      if(mask[ni] && !seen[ni]){seen[ni]=true;queue[tail++]=ni}
     }
    }
   }
   val ringW=(maxX-minX+1)*step;val ringH=(maxRow-minRow+1)*step
   if(ringW !in (w*.035f).toInt()..(w*.26f).toInt())continue
   if(ringH !in (h*.008f).toInt()..(h*.095f).toInt())continue
   if(count<9)continue
   val ratio=ringW.toFloat()/ringH
   if(ratio<1.0f || ratio>6.5f)continue
   // Click ABOVE ground circle center, on the enemy body.
   val cx=(minX+maxX)*step/2f
   val ringCenterY=(minRow+maxRow)*step/2f
   val clickY=(ringCenterY-ringH*.9f).coerceIn(h*.12f,h*.86f)
   val score=count
   if(best==null || score>best.score)best=Target(cx,clickY,score)
  }
  return best
 }
 /** Candidate blue/cyan exclamation markers inside the map, excluding HUD/minimap.
  * Verify a bright white center surrounded by cyan-blue pixels.
  */
 fun navigationDiamond(b:Bitmap):Target? {
  val w=b.width;val h=b.height;val s=max(3,min(w,h)/190)
  var best:Target?=null
  for(y in (h*.15f).toInt() until (h*.87f).toInt() step s)
   for(x in (w*.14f).toInt() until (w*.92f).toInt() step s){
    if(x<w*.24f && y<h*.32f)continue
    var cyan=0;var white=0;var samples=0
    val r=max(10,min(w,h)/55)
    for(dy in -r..r step max(3,r/4))for(dx in -r..r step max(3,r/4)){
     val xx=x+dx;val yy=y+dy
     if(xx !in 0 until w || yy !in 0 until h)continue
     val color=b.getPixel(xx,yy)
     val red=Color.red(color);val green=Color.green(color);val blue=Color.blue(color)
     if(blue>155 && green>120 && blue>red*1.30f && green>red*1.20f)cyan++
     if(red>205 && green>205 && blue>205)white++
     samples++
    }
    if(samples==0 || cyan<6 || white<3)continue
    // Prefer a sufficiently strong local cyan ring with a white interior.
    val score=cyan*2+white
    if(best==null || score>best.score)best=Target(x.toFloat(),y.toFloat(),score)
   }
  return best?.takeIf{it.score>=18}
 }
 /** Boss portal: broad saturated red/orange glowing ground zone, not a tiny enemy ring. */
 fun bossPortal(b:Bitmap):Target? {
  val w=b.width;val h=b.height
  val s=max(4,min(w,h)/130)
  var best:Target?=null
  for(y in (h*.19f).toInt() until (h*.79f).toInt() step s*3)
   for(x in (w*.2f).toInt() until (w*.93f).toInt() step s*3){
    var reds=0;var total=0
    val radius=max(20,min(w,h)/13)
    for(dy in -radius..radius step s*2)for(dx in -radius..radius step s*2){
     val xx=x+dx;val yy=y+dy
     if(xx !in 0 until w || yy !in 0 until h)continue
     val c=b.getPixel(xx,yy)
     val r=Color.red(c);val g=Color.green(c);val blue=Color.blue(c)
     if(r>175 && r>g*1.5f && r>blue*1.6f)reds++
     total++
    }
    if(total<10)continue
    val fraction=reds.toFloat()/total
    if(fraction>.43f && (best==null || reds>best.score))
     best=Target(x.toFloat(),y.toFloat(),reds)
   }
  return best
 }
 /** Approximate enemy direction from pink markers in the top-left minimap.
  * Returns -1 (left), 0 (unknown), +1 (right). Never treated as tap coordinates.
  */
 fun minimapEnemyDirection(b:Bitmap):Int {
  val w=b.width;val h=b.height
  var sx=0L;var count=0
  for(y in (h*.065f).toInt() until (h*.24f).toInt() step 3)
   for(x in (w*.025f).toInt() until (w*.20f).toInt() step 3){
    val c=b.getPixel(x,y)
    val r=Color.red(c);val g=Color.green(c);val blue=Color.blue(c)
    if(r>125 && blue>95 && r>g*1.35f && blue>g*1.25f) {sx+=x;count++}
   }
  if(count<5)return 0
  return if(sx/count < w*.105f)-1 else 1
 }

}
