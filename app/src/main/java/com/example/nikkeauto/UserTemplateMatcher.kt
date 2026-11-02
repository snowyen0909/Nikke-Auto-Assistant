package com.example.nikkeauto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File

/** User-controlled local image templates, never downloadable executable code. */
object UserTemplateMatcher {
 private val ready by lazy{runCatching{OpenCVLoader.initLocal()}.getOrDefault(false)}
 private fun directory(ctx:Context)=File(ctx.filesDir,"rule_templates").apply{mkdirs()}
 fun names(ctx:Context)=directory(ctx).listFiles()?.filter{it.isFile && it.extension=="png"}
  ?.map{it.nameWithoutExtension}?.sorted()?:emptyList()
 fun import(ctx:Context,uri:Uri):String?=runCatching{
  val source=ctx.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}
   ?:return null
  require(source.width in 16..4096 && source.height in 16..4096)
  val name="template_"+System.currentTimeMillis()
  File(directory(ctx),"$name.png").outputStream().use{source.compress(Bitmap.CompressFormat.PNG,100,it)}
  source.recycle()
  name
 }.getOrNull()
 fun find(ctx:Context,frame:Bitmap,name:String):Pair<Float,Float>? {
  if(!ready || !name.matches(Regex("[a-zA-Z0-9_]{1,80}")))return null
  val file=File(directory(ctx),"$name.png")
  if(!file.isFile)return null
  val bmp=BitmapFactory.decodeFile(file.absolutePath)?:return null
  val source=Mat();val gray=Mat();val templColor=Mat();val templ=Mat()
  try {
   Utils.bitmapToMat(frame,source)
   Imgproc.cvtColor(source,gray,Imgproc.COLOR_RGBA2GRAY)
   Utils.bitmapToMat(bmp,templColor)
   Imgproc.cvtColor(templColor,templ,Imgproc.COLOR_RGBA2GRAY)
   var best=0.84
   var xy:Pair<Float,Float>?=null
   for(factor in doubleArrayOf(0.75,0.90,1.0,1.15,1.3)){
    val tw=(templ.cols()*factor).toInt();val th=(templ.rows()*factor).toInt()
    if(tw<12 || th<12 || tw>=gray.cols() || th>=gray.rows())continue
    val resized=Mat();val result=Mat()
    try {
     Imgproc.resize(templ,resized,Size(tw.toDouble(),th.toDouble()))
     Imgproc.matchTemplate(gray,resized,result,Imgproc.TM_CCOEFF_NORMED)
     val peak=Core.minMaxLoc(result)
     if(peak.maxVal>best){
      best=peak.maxVal
      xy=(peak.maxLoc.x+tw/2.0).toFloat() to (peak.maxLoc.y+th/2.0).toFloat()
     }
    }finally{resized.release();result.release()}
   }
   return xy
  }catch(_:Exception){return null}
  finally{bmp.recycle();source.release();gray.release();templColor.release();templ.release()}
 }
}
