package com.example.nikkeauto
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
class TapService:AccessibilityService(){
 override fun onServiceConnected(){Settings.tap=this}
 override fun onAccessibilityEvent(event:AccessibilityEvent?){ }
 override fun onInterrupt(){}
 override fun onDestroy(){if(Settings.tap===this) Settings.tap=null;super.onDestroy()}
 fun click(x:Float,y:Float){ val path=Path().apply{moveTo(x,y)}; val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,80)).build();dispatchGesture(gesture,null,null) }
}