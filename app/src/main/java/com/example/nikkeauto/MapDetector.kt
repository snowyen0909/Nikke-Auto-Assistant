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
}
