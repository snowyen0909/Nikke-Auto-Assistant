package com.example.nikkeauto

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

/** Material 3 UI: dashboard / exploration / daily routine as true in-app tabs. */
object ModernUi {
 private val BG=0xFF0B1522.toInt()
 private val PANEL=0xFF1A2B3A.toInt()
 private val TILE=0xFF26394A.toInt()
 private val MINT=0xFF62E6CF.toInt()
 private val MUTED=0xFFB0C1CF.toInt()
 private fun dp(a:Activity,v:Int)=(a.resources.displayMetrics.density*v+.5f).toInt()
 private fun shape(a:Activity,color:Int)=GradientDrawable().apply {
  setColor(color);cornerRadius=dp(a,19).toFloat()
 }
 private fun text(a:Activity,t:String,size:Float=14f,bold:Boolean=false,color:Int=Color.WHITE)=
  TextView(a).apply{
   text=t;textSize=size;setTextColor(color)
   if(bold)typeface=Typeface.DEFAULT_BOLD
  }
 private fun col(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}
 private fun row(a:Activity)=LinearLayout(a).apply{
  orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
 }
 private fun add(parent:LinearLayout,view:View,top:Int=0) {
  parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dpFrom(parent,top)})
 }
 private fun dpFrom(parent:View,n:Int)=(n*parent.resources.displayMetrics.density+.5f).toInt()
 private fun card(a:Activity,title:String)=col(a).apply {
  background=shape(a,PANEL);setPadding(dp(a,16),dp(a,17),dp(a,16),dp(a,17))
  add(this,text(a,title,14f,true,MINT))
 }
 private fun button(a:Activity,title:String,color:Int,click:()->Unit)=MaterialButton(a).apply {
  text=title;textSize=13f;isAllCaps=false
  cornerRadius=dp(a,17);minHeight=dp(a,52)
  backgroundTintList=ColorStateList.valueOf(color)
  setTextColor(if(color==MINT)BG else Color.WHITE)
  setOnClickListener{click()}
 }
 private fun option(a:Activity,title:String,description:String,enabled:Boolean,change:(Boolean)->Unit)=row(a).apply {
  setPadding(0,dp(a,10),0,dp(a,6))
  val label=col(a)
  add(label,text(a,title,15f,true))
  add(label,text(a,description,12f,false,MUTED),4)
  addView(label,LinearLayout.LayoutParams(0,-2,1f))
  addView(MaterialSwitch(a).apply{
   isChecked=enabled;setOnCheckedChangeListener{_,v->change(v)}
  })
 }
 private fun page(a:Activity):Pair<ScrollView,LinearLayout>{
  val s=ScrollView(a).apply{isFillViewport=true}
  val body=col(a).apply{setPadding(dp(a,18),dp(a,20),dp(a,18),dp(a,26))}
  s.addView(body)
  return s to body
 }
 private fun heading(a:Activity,body:LinearLayout,kicker:String,title:String,desc:String){
  add(body,text(a,kicker,12f,true,MINT))
  add(body,text(a,title,27f,true),5)
  add(body,text(a,desc,12f,false,MUTED),6)
 }
 data class ViewRefs(val status:TextView,val details:TextView)
 fun create(a:Activity,
  permissions:()->Unit,capture:()->Unit,test:()->Unit,
  pause:()->Unit,resume:()->Unit,stop:()->Unit,daily:()->Unit):ViewRefs{
  a.window.statusBarColor=BG;a.window.navigationBarColor=BG
  val root=col(a).apply{setBackgroundColor(BG)}
  val pages=col(a)
  root.addView(pages,LinearLayout.LayoutParams(-1,0,1f))
  val (dash,dashBody)=page(a)
  val (map,mapBody)=page(a)
  val (routine,routineBody)=page(a)
  pages.addView(dash,LinearLayout.LayoutParams(-1,-1))
  pages.addView(map,LinearLayout.LayoutParams(-1,-1))
  pages.addView(routine,LinearLayout.LayoutParams(-1,-1))

  // Tab 1: system dashboard from reference page 1.
  heading(a,dashBody,"NIKKE  •  AUTOMATION","自動化總控台","狀態、權限與自動化控制")
  val monitor=card(a,"● 系統狀態")
  val status=text(a,"已停止",24f,true)
  val details=text(a,"等待螢幕擷取和無障礙服務",12f,false,MUTED)
  add(monitor,status,12);add(monitor,details,8)
  add(dashBody,monitor,20)
  val actions=row(a)
  val controls=listOf(Triple("開始",MINT,resume),Triple("暫停",TILE,pause),
   Triple("停止",0xFF813B4F.toInt(),stop))
  for((i,b) in controls.withIndex()){
   actions.addView(button(a,b.first,b.second,b.third),
    LinearLayout.LayoutParams(0,dp(a,58),if(i==0)1.2f else 1f).apply{rightMargin=dp(a,5)})
  }
  add(dashBody,actions,12)
  val auth=card(a,"權限與測試")
  add(auth,button(a,"01 · 開啟無障礙服務",TILE,permissions),12)
  add(auth,button(a,"02 · 授權整個螢幕",TILE,capture),7)
  add(auth,button(a,"測試點擊",TILE,test),7)
  add(dashBody,auth,18)
  add(dashBody,text(a,"請先授權整個螢幕及無障礙服務，再切換到遊戲。",12f,false,MUTED),17)

  // Tab 2: map/battle controls from reference page 2.
  heading(a,mapBody,"MAP  •  AUTOMATION","推圖與戰鬥","進入遊戲與公告關閉為預設功能")
  val mapCard=card(a,"◎ 大地圖與戰鬥")
  add(mapCard,text(a,"✓ 預設：進入遊戲／關閉活動與簽到彈窗",13f,false,MINT),10)
  add(mapCard,option(a,"劇情與結算","SKIP / 結算推進",Settings.skip){Settings.skip=it},8)
  add(mapCard,option(a,"戰鬥準備","橘色快速戰鬥優先",Settings.battle){Settings.battle=it})
  add(mapCard,option(a,"地圖自動尋敵","藍色菱形、紅圈與 Boss",Settings.map){Settings.map=it})
  add(mapCard,option(a,"地圖巡弋","實驗性功能，預設關閉",Settings.patrol){Settings.patrol=it})
  add(mapBody,mapCard,20)
  add(mapBody,text(a,"地圖展開時優先關閉；導航後優先確認怪物，避免鏡頭來回跳轉。",12f,false,MUTED),18)

  // Tab 3: daily controls from reference page 3, embedded rather than separate Activity.
  heading(a,routineBody,"DAILY ROUTINE  •  8 MODULES","日常一條龍","八項日常任務・登入與公告已整合推圖")
  val dailyPanel=card(a,"✦ 日常執行狀態")
  val dailyState=text(a,"已停止",19f,true)
  val dailyProgress=text(a,"0 / 8",12f,false,MUTED)
  add(dailyPanel,dailyState,12);add(dailyPanel,dailyProgress,8)
  add(dailyPanel,text(a,"未確認的畫面會停止點擊；升級及競技場預設關閉。",12f,false,MUTED),10)
  add(routineBody,dailyPanel,18)
  val list=card(a,"日常功能")
  for(step in DailyRoutine.steps){
   val item=col(a)
   add(item,option(a,step.title,step.detail,DailyRoutine.isEnabled(step.id)){
    DailyRoutine.setEnabled(step.id,it)
   },5)
   add(item,button(a,"調試設定 · ${step.title}",TILE){
    DailyDebugUi.show(a,step.id,step.title)
   },2)
   add(list,item,10)
  }
  add(routineBody,list,14)
  add(routineBody,button(a,"開始日常一條龍",MINT){
   if(!Settings.running || Settings.tap==null){
    Toast.makeText(a,"請先到總控台授權整個螢幕和無障礙服務",Toast.LENGTH_LONG).show()
   }else{
    DailyRoutine.start();Settings.dailyMode=true
    dailyState.text=DailyRoutine.status
   }
  },16)
  add(routineBody,button(a,"暫停／繼續",TILE){
   if(DailyRoutine.paused)DailyRoutine.resume() else DailyRoutine.pause()
   dailyState.text=DailyRoutine.status
  },7)
  add(routineBody,button(a,"確認／略過目前步驟 → 下一項",TILE){
   if(DailyRoutine.active)DailyRoutine.skip()
   dailyState.text=DailyRoutine.status
  },7)
  add(routineBody,button(a,"停止日常任務",0xFF813B4F.toInt()){
   DailyRoutine.stop();Settings.dailyMode=false;dailyState.text=DailyRoutine.status
  },7)

  val nav=BottomNavigationView(a).apply{
   setBackgroundColor(PANEL)
   itemIconTintList=ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked),intArrayOf()),
    intArrayOf(MINT,MUTED))
   itemTextColor=itemIconTintList
   menu.add(0,1,0,"總控台").setIcon(android.R.drawable.ic_menu_manage)
   menu.add(0,2,1,"推圖設定").setIcon(android.R.drawable.ic_menu_compass)
   menu.add(0,3,2,"日常一條龍").setIcon(android.R.drawable.ic_menu_today)
   labelVisibilityMode=0
  }
  fun show(id:Int){
   dash.visibility=if(id==1)View.VISIBLE else View.GONE
   map.visibility=if(id==2)View.VISIBLE else View.GONE
   routine.visibility=if(id==3)View.VISIBLE else View.GONE
   if(id==3){
    dailyState.text=DailyRoutine.status
    dailyProgress.text="${DailyRoutine.completed} / ${DailyRoutine.steps.size}"
   }
  }
  nav.setOnItemSelectedListener{item->show(item.itemId);true}
  show(1);nav.selectedItemId=1
  add(root,nav)
  a.setContentView(root)
  // The main activity still updates dashboard status without recreating tabs.
  return ViewRefs(status,details)
 }
}
