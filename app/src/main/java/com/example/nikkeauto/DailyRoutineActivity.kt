package com.example.nikkeauto

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.*

/** Standalone native screen; no WebView or log collection. */
class DailyRoutineActivity:Activity(){
 private val bg=0xFF101827.toInt()
 private val surface=0xFF1C2939.toInt()
 private val teal=0xFF4EE0C4.toInt()
 private val secondary=0xFFAFC2D1.toInt()
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var status:TextView
 private lateinit var progress:TextView
 private val views=mutableMapOf<String,TextView>()
 private fun dp(i:Int)=(i*resources.displayMetrics.density+.5f).toInt()
 private fun shape(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(18).toFloat()}
 private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
 private fun text(value:String,size:Float=14f,bold:Boolean=false):TextView=
  TextView(this).apply{text=value;textSize=size;setTextColor(Color.WHITE);if(bold)typeface=Typeface.DEFAULT_BOLD}
 private fun add(parent:LinearLayout,child:android.view.View,gap:Int=0){
  parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(gap)})
 }
 private fun button(title:String,onClick:()->Unit)=Button(this).apply{
  text=title;isAllCaps=false;setOnClickListener{onClick()}
 }
 private val refresh=object:Runnable{
  override fun run(){
   if(::status.isInitialized){
    status.text=DailyRoutine.status
    progress.text="${DailyRoutine.completed}/5 · ${if(DailyRoutine.active)"進行中" else "待機"}"
    for(step in DailyRoutine.steps)views[step.id]?.text=DailyRoutine.state(step.id)
   }
   handler.postDelayed(this,1000)
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  window.statusBarColor=bg;window.navigationBarColor=bg
  val root=column().apply{setBackgroundColor(bg)}
  val scroll=ScrollView(this)
  val body=column().apply{setPadding(dp(18),dp(20),dp(18),dp(20))}
  scroll.addView(body)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  add(body,text("日常一條龍",28f,true))
  add(body,text("DAILY ROUTINE · 五個非戰鬥任務",12f).apply{setTextColor(teal)},4)
  val panel=column().apply{background=shape(surface);setPadding(dp(16),dp(14),dp(16),dp(14))}
  status=text("等待啟動",16f,true)
  progress=text("0/5",13f)
  add(panel,status)
  add(panel,progress,8)
  add(panel,text("需先由總控台授權整個螢幕。偵測不明時停止點擊，不自動購買付費物品。",12f).apply{setTextColor(secondary)},10)
  add(body,panel,20)
  for(step in DailyRoutine.steps){
   val card=LinearLayout(this).apply{
    orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
    setPadding(dp(14),dp(12),dp(14),dp(12));background=shape(surface)
   }
   val left=column()
   add(left,text(step.title,16f,true))
   add(left,text(step.detail,12f).apply{setTextColor(secondary)},4)
   val state=text("待執行",11f).apply{setTextColor(teal)}
   views[step.id]=state
   add(left,state,7)
   card.addView(left,LinearLayout.LayoutParams(0,-2,1f))
   card.addView(Switch(this).apply{
    isChecked=DailyRoutine.isEnabled(step.id)
    setOnCheckedChangeListener{_,on->
     when(step.id){
      "outpost"->Settings.dailyOutpost=on
      "dispatch"->Settings.dailyDispatch=on
      "gift"->Settings.dailyGift=on
      "friends"->Settings.dailyFriends=on
      "rewards"->Settings.dailyRewards=on
     }
    }
   })
   add(body,card,10)
  }
  add(body,button("開始日常一條龍"){
   if(!Settings.running || Settings.tap==null){
    Toast.makeText(this,"請先返回總控台，授權螢幕擷取與無障礙服務",Toast.LENGTH_LONG).show()
   }else {
    DailyRoutine.start()
    Settings.dailyMode=true
    Settings.status="日常模式已啟動"
   }
  },22)
  add(body,button("暫停／繼續"){
   if(DailyRoutine.paused)DailyRoutine.resume() else DailyRoutine.pause()
  },6)
  add(body,button("確認目前步驟完成 → 下一項"){
   if(DailyRoutine.active)DailyRoutine.skip()
  },6)
  add(body,button("停止日常任務"){
   DailyRoutine.stop();Settings.dailyMode=false
  },6)
  add(body,button("返回總控台"){finish()},8)
  setContentView(root)
  handler.post(refresh)
 }
 override fun onDestroy(){handler.removeCallbacks(refresh);super.onDestroy()}
}
