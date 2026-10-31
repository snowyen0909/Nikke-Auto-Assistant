package com.example.nikkeauto
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
class TapService:AccessibilityService(){
 override fun onServiceConnected(){Settings.tap=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){ }
 override fun onInterrupt(){}
 fun swipe(x1:Float,y1:Float,x2:Float,y2:Float,duration:Long=450L){
 val path=Path().apply{moveTo(x1,y1);lineTo(x2,y2)}
 dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,duration)).build(),null,null)
 }
 fun zoomOut(w:Float,h:Float){
 val a=Path().apply{moveTo(w*.40f,h*.50f);lineTo(w*.18f,h*.50f)}
 val b=Path().apply{moveTo(w*.60f,h*.50f);lineTo(w*.82f,h*.50f)}
 dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(a,0,550)).addStroke(GestureDescription.StrokeDescription(b,0,550)).build(),null,null)
 }
 override fun onDestroy(){if(Settings.tap===this) Settings.tap=null;super.onDestroy()}
 fun click(x:Float,y:Float){ val path=Path().apply{moveTo(x,y)}; val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build();dispatchGesture(gesture,null,null) }
}