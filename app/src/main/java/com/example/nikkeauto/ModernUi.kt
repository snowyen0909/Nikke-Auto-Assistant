package com.example.nikkeauto

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Offline native Android adaptation of the supplied tactical HTML mockup.
 * Its controls call the SAME callbacks used by the classic interface.
 * No fake FPS / tap counts, CDN, or simulated authorization statuses.
 */
object ModernUi {
 private val BG=0xFF0C0E14.toInt()
 private val SURFACE=0xFF151922.toInt()
 private val PANEL=0xFF1B202C.toInt()
 private val EDGE=0xFF2A3245.toInt()
 private val CYAN=0xFF00F0FF.toInt()
 private val MUTED=0xFF9BA9C0.toInt()
 private val AMBER=0xFFFFAA00.toInt()
 private val RED=0xFFFF3366.toInt()

 private fun dp(a:Activity,x:Int)=(x*a.resources.displayMetrics.density+.5f).toInt()
 private fun bg(color:Int,stroke:Int=EDGE,radius:Float=14f):GradientDrawable =
  GradientDrawable().apply{setColor(color);cornerRadius=radius*2.5f;setStroke(1,stroke)}
 private fun label(a:Activity,s:String,size:Float=13f,color:Int=Color.WHITE,bold:Boolean=false)=
  TextView(a).apply{text=s;textSize=size;setTextColor(color);if(bold)typeface=Typeface.DEFAULT_BOLD}
 private fun vertical(a:Activity):LinearLayout=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}
 private fun row(a:Activity):LinearLayout=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
 private fun add(parent:LinearLayout,child:View,margin:Int=0){
  parent.addView(child,LinearLayout.LayoutParams(-1,-2).apply{if(margin>0)topMargin=margin})
 }
 private fun card(a:Activity,title:String):LinearLayout{
  val col=vertical(a).apply{background=bg(SURFACE);setPadding(dp(a,14),dp(a,14),dp(a,14),dp(a,14))}
  add(col,label(a,title,12f,CYAN,true))
  return col
 }
 private fun button(a:Activity,text:String,color:Int,ink:Int=Color.WHITE,clicked:()->Unit):TextView =
  label(a,text,13f,ink,true).apply{
   gravity=Gravity.CENTER;minHeight=dp(a,46);background=bg(color,color,11f)
   isClickable=true;isFocusable=true;setOnClickListener{clicked()}
  }
 private fun module(a:Activity,name:String,desc:String,initial:Boolean,change:(Boolean)->Unit):LinearLayout{
  val line=row(a).apply{background=bg(PANEL);setPadding(dp(a,10),dp(a,10),dp(a,8),dp(a,10))}
  val words=vertical(a)
  add(words,label(a,name,13f,Color.WHITE,true))
  add(words,label(a,desc,10f,MUTED),dp(a,3))
  line.addView(words,LinearLayout.LayoutParams(0,-2,1f))
  val sw=Switch(a).apply{isChecked=initial;setOnCheckedChangeListener{_,v->change(v)}}
  line.addView(sw)
  return line
 }
 data class ViewRefs(val status:TextView,val details:TextView)
 fun create(a:Activity,
  permissions:()->Unit,capture:()->Unit,test:()->Unit,
  pause:()->Unit,resume:()->Unit,stop:()->Unit):ViewRefs{
  a.window.statusBarColor=BG
  a.window.navigationBarColor=BG
  val root=vertical(a).apply{setBackgroundColor(BG)}
  val head=vertical(a).apply{setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,14))}
  add(head,label(a,"NIKKE  ASSISTANT",21f,CYAN,true))
  add(head,label(a,"勝利女神 · 自動化戰術巡航終端",11f,MUTED))
  root.addView(head)
  val scroll=ScrollView(a).apply{fillViewport=true}
  val body=vertical(a).apply{setPadding(dp(a,14),dp(a,4),dp(a,14),dp(a,16))}
  scroll.addView(body)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val access=card(a,"系統授權與服務連線")
  val perms=row(a)
  val p1=button(a,"STEP 01\n無障礙服務",PANEL){permissions()}
  val p2=button(a,"STEP 02\n螢幕投影授權",PANEL){capture()}
  perms.addView(p1,LinearLayout.LayoutParams(0,dp(a,58),1f).apply{rightMargin=dp(a,5)})
  perms.addView(p2,LinearLayout.LayoutParams(0,dp(a,58),1f).apply{leftMargin=dp(a,5)})
  add(access,perms,dp(a,12))
  add(access,button(a,"測試無障礙點擊",PANEL){test()},dp(a,8))
  add(body,access)
  val modules=card(a,"自動化巡航模組配備")
  add(modules,module(a,"自動跳過劇情 / 結算 / 升級彈窗","SKIP、結算確認與升級獎勵",Settings.skip){Settings.skip=it},dp(a,10))
  add(modules,module(a,"自動戰鬥確認","橘色快速戰鬥優先，否則藍色進入戰鬥",Settings.battle){Settings.battle=it},dp(a,8))
  add(modules,module(a,"地圖尋敵","紅圈怪物、導航驚嘆號、Boss",Settings.map){Settings.map=it},dp(a,8))
  add(modules,module(a,"自動巡弋 / 視角縮放","實驗性功能，預設關閉",Settings.patrol){Settings.patrol=it},dp(a,8))
  add(body,modules,dp(a,12))
  val monitor=card(a,"LIVE STATUS / 系統狀態")
  val status=label(a,"已停止",16f,CYAN,true)
  val details=label(a,"讀取真實擷取及點擊數據中",12f,MUTED)
  add(monitor,status,dp(a,10))
  add(monitor,details,dp(a,8))
  add(body,monitor,dp(a,12))
  val notes=label(a,"授權時選擇整個螢幕。暫停會保留擷取授權；完全停止後需再次授權。",11f,MUTED)
  add(body,notes,dp(a,12))
  val footer=row(a).apply{setPadding(dp(a,10),dp(a,10),dp(a,10),dp(a,12));setBackgroundColor(SURFACE)}
  val btns=listOf(
   Triple("暫停",PANEL,pause),
   Triple("繼續 / 開始",CYAN,resume),
   Triple("停止",PANEL,stop)
  )
  btns.forEachIndexed { i,v->
   val b=button(a,v.first,v.second,if(i==1)Color.BLACK else if(i==2)RED else AMBER){v.third()}
   footer.addView(b,LinearLayout.LayoutParams(0,dp(a,51),1f).apply{
    leftMargin=dp(a,3);rightMargin=dp(a,3)
   })
  }
  root.addView(footer)
  a.setContentView(root)
  return ViewRefs(status,details)
 }
}
