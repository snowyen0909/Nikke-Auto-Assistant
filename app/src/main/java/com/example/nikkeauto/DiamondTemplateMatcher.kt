package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.AssetManager
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-scale edge template search for the exact ! diamond supplied by user.
 * Use only when existing color/shape detectors fail, to avoid regressions.
 * The template omits colored edges so the actual icon can have a cyan rim
 * independent of hue/background. Cyan rim is independently validated.
 */
object DiamondTemplateMatcher {
 private val ready by lazy {runCatching {OpenCVLoader.initLocal()}.getOrDefault(false)}
 private var template:Mat?=null
 private fun edgeTemplate(assets:AssetManager):Mat? {
  template?.let{return it}
  val bmp=assets.open("templates/blue_diamond_core.png").use {
   BitmapFactory.decodeStream(it)
  } ?:return null
  val rgba=Mat();val gray=Mat();val edges=Mat()
  try {
   Utils.bitmapToMat(bmp,rgba)
   Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY)
   Imgproc.Canny(gray,edges,70.0,160.0)
   template=edges
   return edges
  } finally {rgba.release();gray.release();bmp.recycle()}
 }
 fun find(b:Bitmap,assets:AssetManager,expanded:Boolean=false):MapDetector.Target? {
  if(!ready)return null
  val full=Mat();val rgb=Mat();val gray=Mat();val edge=Mat()
  try {
   val scale=min(1.0,650.0/b.width)
   Utils.bitmapToMat(b,full)
   if(scale<1)Imgproc.resize(full,rgb,Size(b.width*scale,b.height*scale))
   else full.copyTo(rgb)
   Imgproc.cvtColor(rgb,gray,Imgproc.COLOR_RGBA2GRAY)
   Imgproc.Canny(gray,edge,70.0,160.0)
   val base=edgeTemplate(assets)?:return null
   val w=rgb.cols();val h=rgb.rows()
   // Reject weak edge correlations; they selected snow textures as targets.
   var bestScore=.67
   var bestX=-1;var bestY=-1
   for(size in intArrayOf((w*.087).toInt(),(w*.102).toInt(),
                         (w*.116).toInt(),(w*.135).toInt())){
    if(size<22 || size>=w || size>=h)continue
    val t=Mat();val result=Mat()
    try {
     Imgproc.resize(base,t,Size(size.toDouble(),size.toDouble()))
     Imgproc.matchTemplate(edge,t,result,Imgproc.TM_CCOEFF_NORMED)
     // Consider a few peaks, not just one, because false peaks may be HUD.
     for(peak in 0 until 4){
      val mm=Core.minMaxLoc(result)
      if(mm.maxVal<bestScore)break
      val cx=mm.maxLoc.x.toInt()+size/2
      val cy=mm.maxLoc.y.toInt()+size/2
      val inMap=expanded && cx<w*.45 && cy<h*.30
      val hud=(cy<h*.145 || cy>h*.928 || (cx>w*.48 && cy<h*.17))
      if(!hud && !inMap && cyanRim(rgb,cx,cy,size)){
       bestScore=mm.maxVal;bestX=cx;bestY=cy
      }
      // Remove this peak locally and test alternatives.
      val px=mm.maxLoc.x.toInt();val py=mm.maxLoc.y.toInt()
      val left=max(0,px-size/3);val top=max(0,py-size/3)
      val right=min(result.cols(),px+size/3+1)
      val bottom=min(result.rows(),py+size/3+1)
      if(right>left && bottom>top) {
       val block=result.submat(top,bottom,left,right)
       block.setTo(org.opencv.core.Scalar(-1.0));block.release()
      }
     }
    } finally {t.release();result.release()}
   }
   return if(bestX>=0)MapDetector.Target(
    (bestX/scale).toFloat(),(bestY/scale).toFloat(),(bestScore*100).toInt())
    else null
  }catch(_:Exception){return null}
  finally {full.release();rgb.release();gray.release();edge.release()}
 }
 private fun cyanRim(rgb:Mat,cx:Int,cy:Int,size:Int):Boolean {
  var cyan=0
  for(k in 0 until 32){
   val theta=k*2*Math.PI/32
   val radius=size*.47
   val x=cx+(radius*kotlin.math.cos(theta)).toInt()
   val y=cy+(radius*kotlin.math.sin(theta)).toInt()
   if(x !in 0 until rgb.cols() || y !in 0 until rgb.rows())continue
   val p=rgb.get(y,x)?:continue // RGBA image, RGB ordering
   if(p[2]>118 && p[1]>96 && p[2]>p[0]*1.20 && p[1]>p[0]*1.09)cyan++
  }
  return cyan>=7
 }
}
