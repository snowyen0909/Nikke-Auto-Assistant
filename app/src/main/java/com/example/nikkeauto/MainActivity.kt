package com.example.nikkeauto
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.widget.*

class MainActivity:Activity(){
 private val captureRequest=100
 private val handler=Handler(Looper.getMainLooper())
 private lateinit var liveStatus:TextView
 private var modernStatus:ModernUi.ViewRefs?=null
 private val refresh=object:Runnable {
  override fun run(){
   if(::liveStatus.isInitialized){
    liveStatus.text="""控制：${if(!Settings.running)"已停止" else if(Settings.paused)"已暫停" else "執行中"}
運行狀態：${Settings.status}
無障礙服務：${if(Settings.tap!=null)"已連接" else "未連接"}
已分析畫面：${Settings.frames}
已送出點擊：${Settings.taps}
辨識文字：${Settings.lastRecognized.take(90)}""".trimIndent()
   }
   modernStatus?.let {
    it.status.text=if(!Settings.running)"● 已停止" else if(Settings.paused)"● 已暫停" else "● 巡航中"
    it.details.text="""服務：${if(Settings.tap!=null)"已連線" else "未連線"} | 分析：${Settings.frames} 張 | 點擊：${Settings.taps} 次

${Settings.status}

OCR: ${Settings.lastRecognized.take(95)}""".trimIndent()
   }
   handler.postDelayed(this,900)
  }
 }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  DailyDebug.load(this)
  if(BuildConfig.FLAVOR=="modern"){
   modernStatus=ModernUi.create(this,
    permissions={startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))},
    capture={askForCapture()},
    test={testClick()},
    pause={setPause(true)},
    resume={if(Settings.running)setPause(false) else askForCapture()},
    stop={Settings.running=false;Settings.paused=false;DailyRoutine.stop();Settings.dailyMode=false;stopService(Intent(this,CaptureService::class.java))},
    daily={startActivity(Intent(this,DailyRoutineActivity::class.java))}
   )
   handler.post(refresh)
   return
  }
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,42,32,28)}
  fun toggle(title:String, initial:Boolean, change:(Boolean)->Unit){
   val sw=Switch(this).apply{text=title;isChecked=initial;setOnCheckedChangeListener{_,checked->change(checked)}}
   root.addView(sw)
  }
  root.addView(TextView(this).apply{text="NIKKE Auto Assistant";textSize=24f})
  toggle("自動跳過劇情 / 結算 / 升級彈窗",Settings.skip){Settings.skip=it}
  toggle("自動戰鬥確認",Settings.battle){Settings.battle=it}
  toggle("地圖尋敵（紅圈 / 驚嘆號 / Boss）",Settings.map){Settings.map=it}
  toggle("自動巡弋 / 縮放（實驗性，預設關閉）",Settings.patrol){Settings.patrol=it}
  root.addView(Button(this).apply{text="1. 開啟無障礙服務";setOnClickListener{startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))}})
  root.addView(Button(this).apply{text="2. 授權完整螢幕並啟動";setOnClickListener{
   if(Settings.tap==null){Toast.makeText(this@MainActivity,"請先開啟無障礙服務",Toast.LENGTH_LONG).show();return@setOnClickListener}
   val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   startActivityForResult(mgr.createScreenCaptureIntent(),captureRequest)
  }})
  root.addView(Button(this).apply{text="測試無障礙點擊（在本畫面測試）";setOnClickListener{
   val tap=Settings.tap
   if(tap==null) Toast.makeText(this@MainActivity,"無障礙服務沒有連接",Toast.LENGTH_LONG).show()
   else {
    val dm=resources.displayMetrics
    tap.click(dm.widthPixels*.50f,dm.heightPixels*.50f)
    Settings.status="已要求測試點擊，請查看是否回報執行成功"
   }
  }})
  root.addView(Button(this).apply{text="暫停自動化";setOnClickListener{
   if(Settings.running){
    Settings.paused=true
    Settings.status="已暫停（不用重新授權截圖）"
    startService(Intent(this@MainActivity,CaptureService::class.java).setAction("com.example.nikkeauto.PAUSE"))
   }else Toast.makeText(this@MainActivity,"請先授權完整螢幕並啟動",Toast.LENGTH_SHORT).show()
  }})
  root.addView(Button(this).apply{text="繼續 / 開始";setOnClickListener{
   if(Settings.running){
    Settings.paused=false
    startService(Intent(this@MainActivity,CaptureService::class.java).setAction("com.example.nikkeauto.RESUME"))
   }else Toast.makeText(this@MainActivity,"請按「授權完整螢幕並啟動」",Toast.LENGTH_LONG).show()
  }})
  root.addView(Button(this).apply{text="停止自動化";setOnClickListener{
   Settings.running=false;Settings.paused=false;stopService(Intent(this@MainActivity,CaptureService::class.java))
  }})
  liveStatus=TextView(this).apply{textSize=15f;setPadding(0,20,0,0)}
  root.addView(liveStatus)
  root.addView(TextView(this).apply{text="授權時請選擇「整個螢幕」，然後切換到遊戲。地圖尋敵與巡弋為實驗性功能。可透過通知列暫停／繼續；停止後須重新授權截圖。"})
  setContentView(ScrollView(this).apply{addView(root)})
  handler.post(refresh)
 }
 private fun askForCapture(){
  if(Settings.tap==null){Toast.makeText(this,"請先啟用無障礙服務",Toast.LENGTH_LONG).show();return}
  val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startActivityForResult(manager.createScreenCaptureIntent(),captureRequest)
 }
 private fun testClick(){
  val tap=Settings.tap
  if(tap==null){Toast.makeText(this,"無障礙服務未連接",Toast.LENGTH_SHORT).show();return}
  tap.click(resources.displayMetrics.widthPixels*.50f,resources.displayMetrics.heightPixels*.50f)
 }
 private fun setPause(paused:Boolean){
  if(!Settings.running){Toast.makeText(this,"請先授權螢幕並啟動",Toast.LENGTH_SHORT).show();return}
  Settings.paused=paused
  startService(Intent(this,CaptureService::class.java).setAction(if(paused)"com.example.nikkeauto.PAUSE" else "com.example.nikkeauto.RESUME"))
 }
 override fun onResume(){
  super.onResume()
  if(OverlayControls.permissionPending && android.provider.Settings.canDrawOverlays(this)){
   OverlayControls.permissionPending=false
   if(!OverlayControls.visible){
    try{startService(Intent(this,OverlayService::class.java))}
    catch(e:Exception){Toast.makeText(this,"無法啟動浮窗：${e.javaClass.simpleName}",Toast.LENGTH_LONG).show()}
   }
  }
 }
 override fun onDestroy(){handler.removeCallbacks(refresh);super.onDestroy()}
 @Deprecated("Legacy activity result")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==captureRequest && resultCode==RESULT_OK && data!=null){
   Settings.frames=0;Settings.taps=0;Settings.paused=false;Settings.running=true;Settings.status="初始化中"
   val serviceIntent=Intent(this,CaptureService::class.java)
    .putExtra("resultCode",resultCode).putExtra("resultData",data)
   startForegroundService(serviceIntent)
  }
 }
}