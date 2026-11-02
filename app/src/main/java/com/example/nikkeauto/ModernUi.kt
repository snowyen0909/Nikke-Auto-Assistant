package com.example.nikkeauto

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch

/** NOVA native dashboard, intentionally independent of the historical UI. */
object ModernUi {
 private val BG=0xFF0B1522.toInt()
 private val PANEL=0xFF1A2B3A.toInt()
 private val TILE=0xFF26394A.toInt()
 private val MINT=0xFF62E6CF.toInt()
 private val BLUE=0xFF78B8FF.toInt()
 private val MUTED=0xFFB0C1CF.toInt()
 private fun dp(a:Activity,v:Int)=(a.resources.displayMetrics.density*v+.5f).toInt()
 private fun shape(a:Activity,color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(a,18).toFloat()}
 private fun txt(a:Activity,t:String,size:Float=14f,bold:Boolean=false,color:Int=Color.WHITE)=
  TextView(a).apply{text=t;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
 private fun col(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}
 private fun row(a:Activity)=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 private fun add(parent:LinearLayout,view:View,top:Int=0){
  parent.addView(view,LinearLayout.LayoutParams(-1,-2).apply{topMargin=top})
 }
 private fun block(a:Activity,title:String):LinearLayout=col(a).apply{
  background=shape(a,PANEL);setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15))
  add(this,txt(a,title,13f,true,MINT))
 }
 private fun action(a:Activity,title:String,color:Int,listener:()->Unit)=
  MaterialButton(a).apply{
   text=title;textSize=13f;isAllCaps=false
   cornerRadius=dp(a,18)
   setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
   setTextColor(if(color==MINT)BG else Color.WHITE)
   minHeight=dp(a,54)
   setOnClickListener{listener()}
  }
 private fun switchItem(a:Activity,title:String,subtitle:String,checked:Boolean,on:(Boolean)->Unit):LinearLayout{
  val r=row(a).apply{setPadding(0,dp(a,11),0,dp(a,5))}
  val l=col(a)
  add(l,txt(a,title,14f,true))
  add(l,txt(a,subtitle,11f,false,MUTED),3)
  r.addView(l,LinearLayout.LayoutParams(0,-2,1f))
  r.addView(MaterialSwitch(a).apply{isChecked=checked;setOnCheckedChangeListener{_,value->on(value)}})
  return r
 }
 data class ViewRefs(val status:TextView,val details:TextView)
 fun create(a:Activity,
  permissions:()->Unit,capture:()->Unit,test:()->Unit,
  pause:()->Unit,resume:()->Unit,stop:()->Unit,daily:()->Unit):ViewRefs{
  a.window.statusBarColor=BG
  a.window.navigationBarColor=BG
  val root=col(a).apply{setBackgroundColor(BG)}
  val scroll=ScrollView(a).apply{isFillViewport=true}
  val body=col(a).apply{setPadding(dp(a,18),dp(a,22),dp(a,18),dp(a,20))}
  scroll.addView(body)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  add(body,txt(a,"NIKKE  •  AUTOMATION",12f,true,MINT))
  add(body,txt(a,"自動化總控台",27f,true),5)
  add(body,txt(a,"清楚、簡單、可隨時停止的遊戲輔助工具",12f,false,MUTED),5)
  val monitor=block(a,"● 系統狀態")
  val status=txt(a,"待機",23f,true)
  val details=txt(a,"等待無障礙服務和螢幕擷取",12f,false,MUTED)
  add(monitor,status,13);add(monitor,details,8)
  add(body,monitor,22)
  val controls=row(a)
  val buttons=listOf(Triple("開始 / 繼續",MINT,resume),Triple("暫停",TILE,pause),Triple("停止",0xFF7A3443.toInt(),stop))
  for((index,item) in buttons.withIndex()){
   controls.addView(action(a,item.first,item.second,item.third),LinearLayout.LayoutParams(0,dp(a,56),if(index==0)1.45f else 1f).apply{rightMargin=dp(a,5)})
  }
  add(body,controls,12)
  val permission=block(a,"權限與測試")
  add(permission,action(a,"01 · 開啟無障礙服務",TILE,permissions),11)
  add(permission,action(a,"02 · 授權整個螢幕",TILE,capture),8)
  add(permission,action(a,"測試點擊",TILE,test),8)
  add(body,permission,20)
  val dailyBlock=block(a,"✦ 日常自動化")
  add(dailyBlock,txt(a,"日常一條龍",21f,true),10)
  add(dailyBlock,txt(a,"登入 · 信箱 · 派遣 · 免費商品 · 好友 · 升級 · 競技場",12f,false,MUTED),6)
  add(dailyBlock,action(a,"開啟日常專屬頁面",MINT,daily),12)
  add(body,dailyBlock,16)
  val modules=block(a,"◎ 大地圖與戰鬥")
  add(modules,switchItem(a,"劇情與結算","SKIP / 結算推進",Settings.skip){Settings.skip=it},7)
  add(modules,switchItem(a,"戰鬥準備","橘色快速戰鬥優先",Settings.battle){Settings.battle=it})
  add(modules,switchItem(a,"地圖自動尋敵","紅圈、驚嘆號與 Boss",Settings.map){Settings.map=it})
  add(modules,switchItem(a,"地圖巡弋","實驗性功能",Settings.patrol){Settings.patrol=it})
  add(body,modules,16)
  add(body,txt(a,"安全提示：進入遊戲前請確認無障礙和螢幕擷取已啟用。日常模式與一般推圖模式不會同時點擊。",11f,false,MUTED),16)
  a.setContentView(root)
  return ViewRefs(status,details)
 }
}
