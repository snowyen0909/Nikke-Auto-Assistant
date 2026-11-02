package com.example.nikkeauto

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** User-enabled, movable in-game overlay, not a full-screen touch blocker. */
class OverlayService:Service(){
 private lateinit var wm:WindowManager
 private var root:LinearLayout?=null
 private var params:WindowManager.LayoutParams?=null
 private val handler=Handler(Looper.getMainLooper())
 private var expanded=false
 private var readout:TextView?=null
 private val tick=object:Runnable{
  override fun run(){
   readout?.text="狀態：${if(!Settings.running)"已停止" else if(Settings.paused)"已暫停" else "執行中"}\n日常：${DailyRoutine.status.take(75)}\n辨識：${Settings.lastRecognized.take(55)}"
   handler.postDelayed(this,1300)
  }
 }
 private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
 override fun onBind(intent:Intent?):IBinder?=null
 override fun onCreate(){
  super.onCreate()
  wm=getSystemService(WINDOW_SERVICE) as WindowManager
  render()
  handler.post(tick)
 }
 private fun card(color:Int)=GradientDrawable().apply{
  setColor(color);cornerRadius=dp(16).toFloat()
 }
 private fun render(){
  root?.let{runCatching{wm.removeView(it)}}
  val view=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   background=card(0xF21C2B3C.toInt())
   setPadding(dp(9),dp(7),dp(9),dp(7))
  }
  val title=TextView(this).apply{
   text="⠿ NIKKE  ${if(expanded)"▴" else "▾"}"
   textSize=15f;setTextColor(Color.WHITE)
   setPadding(dp(8),dp(5),dp(8),dp(5))
  }
  view.addView(title)
  title.setOnClickListener{expanded=!expanded;render()}
  // Drag by the header. Avoid intercepting touches outside the actual overlay.
  title.setOnTouchListener(object:android.view.View.OnTouchListener{
   var px=0f;var py=0f;var x=0;var y=0;var moved=false
   override fun onTouch(v:android.view.View,event:MotionEvent):Boolean{
    when(event.actionMasked){
     MotionEvent.ACTION_DOWN->{
      px=event.rawX;py=event.rawY;x=params?.x?:0;y=params?.y?:0;moved=false;return true
     }
     MotionEvent.ACTION_MOVE->{
      if(kotlin.math.abs(event.rawX-px)>dp(5)||kotlin.math.abs(event.rawY-py)>dp(5))moved=true
      params?.let{it.x=x+(event.rawX-px).toInt();it.y=y+(event.rawY-py).toInt()
       wm.updateViewLayout(view,it)}
      return true
     }
     MotionEvent.ACTION_UP->{if(!moved){expanded=!expanded;render()};return true}
    }
    return false
   }
  })
  if(expanded){
   readout=TextView(this).apply{setTextColor(Color.WHITE);textSize=12f}
   view.addView(readout)
   val row=LinearLayout(this)
   fun action(label:String,callback:()->Unit){
    row.addView(MaterialButton(this).apply{
     text=label;isAllCaps=false;minWidth=0;setPadding(1,0,1,0)
     textSize=11f;setOnClickListener{callback()}
    },LinearLayout.LayoutParams(0,dp(42),1f))
   }
   action(if(Settings.paused)"繼續" else "暫停"){
    if(Settings.running){
     Settings.paused=!Settings.paused
     startService(Intent(this,CaptureService::class.java).setAction(
      if(Settings.paused)"com.example.nikkeauto.PAUSE" else "com.example.nikkeauto.RESUME"))
     render()
    }
   }
   action("停止"){
    Settings.running=false;DailyRoutine.stop();Settings.dailyMode=false
    stopService(Intent(this,CaptureService::class.java));render()
   }
   action("下一步"){if(DailyRoutine.active)DailyRoutine.skip();render()}
   view.addView(row)
   val open=MaterialButton(this).apply{
    text="開啟完整規則編輯器";isAllCaps=false
    setOnClickListener{
     val intent=Intent(this@OverlayService,RuleEditorActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
     startActivity(intent)
    }
   }
   view.addView(open)
  }else readout=null
  val lp=WindowManager.LayoutParams(
   if(expanded)dp(295) else WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.WRAP_CONTENT,
   WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
   WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
   PixelFormat.TRANSLUCENT).apply{
    gravity=Gravity.TOP or Gravity.START
    x=params?.x?:dp(15);y=params?.y?:dp(150)
   }
  params=lp;root=view
  wm.addView(view,lp)
 }
 override fun onDestroy(){
  handler.removeCallbacks(tick)
  root?.let{runCatching{wm.removeView(it)}}
  super.onDestroy()
 }
}
