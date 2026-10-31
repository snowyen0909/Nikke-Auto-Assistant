package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Battle buttons are detected within the lower part of the screen. All results
 * are in full screenshot pixel coordinates; no hard-coded phone resolution.
 */
object BattleDetector {
 enum class ColorState { ORANGE, BLUE, GRAY, UNKNOWN }
 data class Button(val x:Float,val y:Float,val confidence:Float)

 fun stateNear(b:Bitmap,x:Float,y:Float):ColorState {
  val w=b.width;val h=b.height
  val rx=max(12,(w*.035f).toInt());val ry=max(8,(h*.012f).toInt())
  var orange=0;var blue=0;var gray=0;var total=0
  for(dy in -ry..ry step 4)for(dx in -rx..rx step 5) {
   val xx=x.toInt()+dx;val yy=y.toInt()+dy
   if(xx !in 0 until w || yy !in 0 until h)continue
   val c=b.getPixel(xx,yy)
   val r=Color.red(c);val g=Color.green(c);val v=Color.blue(c)
   if(r>195 && g in 65..210 && v<145 && r>g+40)orange++
   if(v>142 && v>r+58 && g>83 && g>r+34)blue++
   if(abs(r-g)<25 && abs(g-v)<25 && r in 55..185)gray++
   total++
  }
  if(total<10)return ColorState.UNKNOWN
  if(orange.toFloat()/total>.16f)return ColorState.ORANGE
  if(blue.toFloat()/total>.16f)return ColorState.BLUE
  if(gray.toFloat()/total>.30f)return ColorState.GRAY
  return ColorState.UNKNOWN
 }

 /** Find a wide, solid blue button in the lower 42% of the battle screen.
  * Called ONLY after independent stage UI confirmation.
  * Supports both portrait and landscape; ignores thin blue tabs/character cards.
  */
 fun findBlueEnterButton(b:Bitmap):Button? {
  val w=b.width;val h=b.height
  val step=max(2,min(w,h)/280)
  val cols=(w+step-1)/step
  val rows=(h+step-1)/step
  val y0=(h*.58f/step).toInt()
  val x0=(w*.28f/step).toInt()
  val active=BooleanArray(cols*rows)
  for(y in y0 until rows)for(x in x0 until cols){
   val c=b.getPixel(min(w-1,x*step),min(h-1,y*step))
   val r=Color.red(c);val g=Color.green(c);val v=Color.blue(c)
   active[y*cols+x]=(v>140 && v>r+55 && g>77 && g>r+30 && r<125)
  }
  val visited=BooleanArray(active.size)
  val queue=IntArray(active.size)
  var best:Button?=null
  var bestScore=-1f
  for(start in active.indices){
   if(!active[start] || visited[start])continue
   var head=0;var tail=1;queue[0]=start;visited[start]=true
   var minX=cols;var maxX=0;var minY=rows;var maxY=0;var count=0
   while(head<tail) {
    val i=queue[head++];val x=i%cols;val y=i/cols
    minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y);count++
    for(dy in -1..1)for(dx in -1..1) {
     val nx=x+dx;val ny=y+dy
     if(nx in 0 until cols && ny in y0 until rows) {
      val ni=ny*cols+nx
      if(active[ni] && !visited[ni]){visited[ni]=true;queue[tail++]=ni}
     }
    }
   }
   val boxW=(maxX-minX+1)*step;val boxH=(maxY-minY+1)*step
   val density=count.toFloat()/((maxX-minX+1)*(maxY-minY+1))
   if(boxW<w*.15f || boxW>w*.66f || boxH<h*.035f || boxH>h*.16f || density<.40f)continue
   val cx=(minX+maxX+1)*step*.5f
   val cy=(minY+maxY+1)*step*.5f
   if(cx<w*.38f || cy<h*.67f)continue
   val score=density*boxW*boxH
   if(score>bestScore) {bestScore=score;best=Button(cx.coerceAtMost(w-1f),cy.coerceAtMost(h-1f),density)}
  }
  return best
 }
}
