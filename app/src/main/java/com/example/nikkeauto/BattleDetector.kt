package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max

/**
 * Detects the two large battle action buttons by their colors and relative positions.
 * OCR is used to confirm the stage UI in CaptureService; this detector does not
 * click based on color alone on unrelated screens.
 */
object BattleDetector {
 data class Button(val x:Float,val y:Float,val confidence:Float)
 data class Result(val quick:Button?, val enter:Button?,val quickDisabled:Boolean)

 fun detect(bitmap:Bitmap):Result {
  val w=bitmap.width;val h=bitmap.height
  val scale=max(1,w/400)
  val cols=(w+scale-1)/scale
  val rows=(h+scale-1)/scale
  val orange=IntArray(rows)
  val blue=IntArray(rows)
  val rowOrangeX=Array(rows){IntArray(cols)}
  val rowBlueX=Array(rows){IntArray(cols)}
  val yStart=(h*.53f).toInt()/scale
  for(iy in yStart until rows) {
   val y=(iy*scale).coerceAtMost(h-1)
   for(ix in 0 until cols){
    val x=(ix*scale).coerceAtMost(w-1)
    val c=bitmap.getPixel(x,y)
    val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
    val isOrange=r>205 && g in 65..205 && b<125 && r>g+45
    val isBlue=b>125 && b>r*1.3 && g>r*1.2 && g>75
    if(isOrange){orange[iy]++;rowOrangeX[iy][ix]=1}
    if(isBlue){blue[iy]++;rowBlueX[iy][ix]=1}
   }
  }
  fun best(hits:IntArray,masks:Array<IntArray>):Button? {
   var bestScore=0f;var bestButton:Button?=null
   val minHits=max(8,(cols*.12f).toInt())
   for(y in yStart+2 until rows-2){
    val score=hits[y-2]+hits[y-1]+hits[y]+hits[y+1]+hits[y+2]
    if(hits[y]<minHits || score<bestScore)continue
    val mask=masks[y]
    var start=-1;var end=-1;var longest=0;var runStart=-1
    for(x in mask.indices){
     if(mask[x]==1){if(runStart<0)runStart=x}
     else if(runStart>=0){
      if(x-runStart>longest){longest=x-runStart;start=runStart;end=x}
      runStart=-1
     }
    }
    if(runStart>=0 && cols-runStart>longest){longest=cols-runStart;start=runStart;end=cols}
    if(longest<minHits/2 || start<0)continue
    val centerX=(start+end)*scale*.5f
    if(centerX<w*.30f || centerX>w*.92f)continue
    bestScore=score.toFloat()
    bestButton=Button(centerX,y*scale.toFloat(),(score/(cols*5f)).coerceIn(0f,1f))
   }
   return bestButton
  }
  val quick=best(orange,rowOrangeX)
  val enter=best(blue,rowBlueX)
  // A disabled gray quick button may still contain OCR; gray pixels alone
  // cannot justify a tap. Fall back to enter only when blue is present.
  return Result(quick,enter,quick==null && enter!=null)
 }
}
