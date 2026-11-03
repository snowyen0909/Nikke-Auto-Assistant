package com.example.nikkeauto

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Icon-only controls (close X, home, mailbox, quick-battle switch) have no text
 * for OCR, so they are located with grayscale template matching inside a limited
 * region of interest. Templates are cropped from 750px wide screenshots and
 * searched at several scales relative to the current frame width.
 * Original implementation; the bundled templates come from the user's own screenshots.
 */
object DailyTemplates {
 data class Hit(val x:Float,val y:Float,val w:Float,val h:Float,val score:Double)
 /** Set by CaptureService for the duration of one DailyRoutine.decide() call. */
 @Volatile var frame:Bitmap?=null
 @Volatile var assets:AssetManager?=null
 private const val REF_WIDTH=750.0
 private val scales=doubleArrayOf(.75,.85,.95,1.05,1.2,1.35)
 private val ready by lazy {runCatching{OpenCVLoader.initLocal()}.getOrDefault(false)}
 private val cache=HashMap<String,Mat>()

 private fun template(name:String):Mat? {
  cache[name]?.let{return it}
  val am=assets?:return null
  val bmp=runCatching{am.open("templates/daily/$name.jpg").use{BitmapFactory.decodeStream(it)}}
   .getOrNull()?:return null
  val rgba=Mat();val gray=Mat()
  try{
   Utils.bitmapToMat(bmp,rgba)
   Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY)
   cache[name]=gray
   return gray
  }catch(_:Exception){gray.release();return null}
  finally{rgba.release();bmp.recycle()}
 }

 /** Best match of [name] inside the fractional region; null below [threshold]. */
 @Synchronized fun find(name:String,xMin:Float,xMax:Float,yMin:Float,yMax:Float,
                        threshold:Double=.80):Hit? {
  val b=frame?:return null
  if(!ready || b.isRecycled)return null
  val tpl=template(name)?:return null
  val x0=(b.width*xMin).toInt().coerceIn(0,b.width-2)
  val y0=(b.height*yMin).toInt().coerceIn(0,b.height-2)
  val x1=(b.width*xMax).toInt().coerceIn(x0+1,b.width)
  val y1=(b.height*yMax).toInt().coerceIn(y0+1,b.height)
  val rgba=Mat();val gray=Mat()
  var best:Hit?=null
  try{
   val roi=Bitmap.createBitmap(b,x0,y0,x1-x0,y1-y0)
   Utils.bitmapToMat(roi,rgba)
   if(roi!==b)roi.recycle()
   Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY)
   val base=b.width/REF_WIDTH
   for(k in scales){
    val tw=(tpl.cols()*base*k).toInt();val th=(tpl.rows()*base*k).toInt()
    if(tw<10 || th<10 || tw>=gray.cols() || th>=gray.rows())continue
    val t=Mat();val result=Mat()
    try{
     Imgproc.resize(tpl,t,Size(tw.toDouble(),th.toDouble()))
     Imgproc.matchTemplate(gray,t,result,Imgproc.TM_CCOEFF_NORMED)
     val mm=Core.minMaxLoc(result)
     if(mm.maxVal>=threshold && (best==null || mm.maxVal>best.score)){
      best=Hit((x0+mm.maxLoc.x+tw/2.0).toFloat(),(y0+mm.maxLoc.y+th/2.0).toFloat(),
       tw.toFloat(),th.toFloat(),mm.maxVal)
     }
    }finally{t.release();result.release()}
   }
   return best
  }catch(_:Exception){return null}
  finally{rgba.release();gray.release()}
 }

 /** Close X in the top-right area of dialogs. */
 fun closeTop():Hit?=find("close",.66f,1f,.03f,.34f,.85)
 /** Home icon of the bottom navigation bar. */
 fun homeBottom():Hit?=find("home",0f,1f,.80f,1f,.85)
 /** Mailbox icon on the lobby top bar. */
 fun mailbox():Hit?=find("mailbox",.50f,1f,0f,.24f,.80)

 /**
  * Quick-battle switch state from the two reference crops.
  * Returns (isOn, hit) where hit points at the switch body, or null if unclear.
  */
 fun fastBattle():Pair<Boolean,Hit>? {
  val on=find("fast_on",.02f,.65f,.55f,.98f,.72)
  val off=find("fast_off",.02f,.65f,.55f,.98f,.72)
  if(on==null && off==null)return null
  val isOn=when{
   on==null->false
   off==null->true
   else->on.score>off.score+.02
  }
  if(on!=null && off!=null && kotlin.math.abs(on.score-off.score)<=.02)return null
  val hit=(if(isOn)on else off)!!
  // The crop includes the title above the switch; aim at the lower half.
  return Pair(isOn,hit.copy(y=hit.y+hit.h*.2f))
 }
}
