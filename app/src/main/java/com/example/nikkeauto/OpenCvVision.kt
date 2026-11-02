package com.example.nikkeauto

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Image-location approach informed by Klick'r, AutomatorX and recordAutoTestScript.
 * This is original Kotlin code; no third-party project source is copied.
 * Native OpenCV HSV and contour processing is resilient to some theme/background changes.
 */
object OpenCvVision {
 private val available:Boolean by lazy {runCatching{OpenCVLoader.initLocal()}.getOrDefault(false)}

 /** Find cyan frame + light diamond face + dark exclamation; return pixel center. */
 fun blueDiamond(bitmap:Bitmap,overlay:Boolean=false):MapDetector.Target? {
  if(!available)return null
  val w=bitmap.width;val h=bitmap.height
  if(w<240 || h<400)return null
  val rgba=Mat();val rgb=Mat();val hsv=Mat();val binary=Mat();val hierarchy=Mat()
  val contours=ArrayList<MatOfPoint>()
  try{
   Utils.bitmapToMat(bitmap,rgba)
   Imgproc.cvtColor(rgba,rgb,Imgproc.COLOR_RGBA2RGB)
   Imgproc.cvtColor(rgb,hsv,Imgproc.COLOR_RGB2HSV)
   // Cyan/turquoise edge pixels; saturation threshold filters bright white UI.
   Core.inRange(hsv,Scalar(80.0,75.0,90.0),Scalar(110.0,255.0,255.0),binary)
   val kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,org.opencv.core.Size(3.0,3.0))
   Imgproc.morphologyEx(binary,binary,Imgproc.MORPH_CLOSE,kernel)
   kernel.release()
   Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE)
   var best:MapDetector.Target?=null
   for(contour in contours){
    val r=Imgproc.boundingRect(contour)
    if(r.width !in (w*.045f).toInt()..(w*.19f).toInt())continue
    if(r.height !in (w*.045f).toInt()..(w*.23f).toInt())continue
    if(r.width.toFloat()/r.height !in .64f..1.45f)continue
    val cx=r.x+r.width/2;val cy=r.y+r.height/2
    if(cx<w*.035f || cx>w*.965f || cy<h*.12f || cy>h*.935f)continue
    if(overlay && cx<w*.45f && cy<h*.30f)continue
    if(cx>w*.49f && cy<h*.17f)continue // Event header
    val area=Imgproc.contourArea(contour)
    if(area<r.width*r.height*.08 || area>r.width*r.height*.83)continue
    // Evaluate the center of the icon: bright face and dark exclamation.
    var light=0;var dark=0;var total=0
    val insetX=max(2,r.width/5);val insetY=max(2,r.height/5)
    val stride=max(2,min(r.width,r.height)/12)
    for(y in (r.y+insetY) until (r.y+r.height-insetY) step stride)
     for(x in (r.x+insetX) until (r.x+r.width-insetX) step stride){
      val pixel=rgb.get(y,x)?:continue
      val rr=pixel[0];val gg=pixel[1];val bb=pixel[2]
      if(rr>168 && gg>168 && bb>168)light++
      if(rr<118 && gg<125 && bb<140)dark++
      total++
     }
    if(total<8 || light<total*.18 || dark<1)continue
    val score=((light.toFloat()/total)*45+min(20.0,area/(r.width*r.height)*40).toFloat()+dark*2).toInt()
    if(best==null || score>best.score)best=MapDetector.Target(cx.toFloat(),cy.toFloat(),score)
   }
   return best
  }catch(_:Exception){return null}
  finally{
   contours.forEach{it.release()}
   rgba.release();rgb.release();hsv.release();binary.release();hierarchy.release()
  }
 }
}
