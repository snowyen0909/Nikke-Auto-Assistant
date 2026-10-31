package com.example.nikkeauto

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.WindowManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class CaptureService : Service() {
 private val handler = Handler(Looper.getMainLooper())
 private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
 private var reader: ImageReader? = null
 private var projection: MediaProjection? = null
 private var display: android.hardware.display.VirtualDisplay? = null
 private var busy = false
 private var lastTap = 0L
 private var lastTarget = ""
 private var stable = 0
 private var noTargetSince = 0L
 private var patrolStep = 0
 private var lastNotify = 0L
 private val notificationChannel = "capture"

 override fun onBind(intent: Intent?): IBinder? = null

 private fun status(s: String) {
  Settings.status = s
  val now = SystemClock.elapsedRealtime()
  if (now - lastNotify >= 2500) {
   lastNotify = now
   val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
   mgr.notify(1, notification(s))
  }
 }
 private fun notification(s: String): Notification =
  Notification.Builder(this, notificationChannel)
   .setSmallIcon(android.R.drawable.ic_menu_view)
   .setContentTitle("NIKKE Auto Assistant")
   .setContentText(s)
   .setOngoing(true).build()

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  mgr.createNotificationChannel(NotificationChannel(notificationChannel, "螢幕辨識狀態", NotificationManager.IMPORTANCE_LOW))
  startForeground(1, notification("正在初始化擷取"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
  val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33)
   intent?.getParcelableExtra("resultData", Intent::class.java)
  else @Suppress("DEPRECATION") intent?.getParcelableExtra("resultData")
  if (resultData == null) { status("螢幕擷取授權資料遺失"); stopSelf(); return START_NOT_STICKY }
  val wm = getSystemService(WINDOW_SERVICE) as WindowManager
  val bounds = wm.currentWindowMetrics.bounds
  val w = bounds.width()
  val h = bounds.height()
  val dpi = resources.displayMetrics.densityDpi
  if (w <= 0 || h <= 0) {status("螢幕尺寸無效");stopSelf();return START_NOT_STICKY}
  val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  try {
   projection = manager.getMediaProjection(intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED, resultData)
   projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){Settings.running=false;stopSelf()}},handler)
   reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
   display = projection?.createVirtualDisplay("NikkeCapture",w,h,dpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
   status("螢幕擷取已啟動，等待畫面")
  } catch(e:Exception) {status("擷取啟動失敗: ${e.javaClass.simpleName}");stopSelf();return START_NOT_STICKY}
  handler.post(object:Runnable {
   override fun run() {
    if (!Settings.running) {stopSelf();return}
    capture(w,h)
    handler.postDelayed(this,700)
   }
  })
  return START_NOT_STICKY
 }

 private fun capture(w:Int,h:Int) {
  if (busy) return
  val image = reader?.acquireLatestImage()
  if (image == null) {status("等待螢幕畫面（若持續顯示，請重新授權整個螢幕）");return}
  val bitmap: Bitmap
  try {
   val p = image.planes[0]
   val strideWidth = p.rowStride / p.pixelStride
   val raw = Bitmap.createBitmap(strideWidth,h,Bitmap.Config.ARGB_8888)
   raw.copyPixelsFromBuffer(p.buffer)
   bitmap = Bitmap.createBitmap(raw,0,0,w,h)
   raw.recycle()
  } catch(e:Exception) {status("擷取影像失敗: ${e.javaClass.simpleName}");return}
  finally {image.close()}
  busy = true
  recognizer.process(InputImage.fromBitmap(bitmap,0))
   .addOnSuccessListener { result ->
    try {
     Settings.frames++
     val lines=result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
      line.boundingBox?.let { Triple(line.text, it.centerX().toFloat(), it.centerY().toFloat()) }
     }
     val all=lines.joinToString(" ") { it.first }.replace(" ","").uppercase()
     Settings.lastRecognized=all.take(110)
     val skip=lines.firstOrNull { it.first.contains("SKIP",true) && it.second>w*.48f && it.third<h*.22f }
     val next=lines.firstOrNull {
      val t=it.first.replace(" ","")
      t.contains("點擊任意處") || t.contains("点击任意处") || t.contains("進行下一步") || t.contains("进行下一步")
     }
     val popup=all.contains("OUTPOST") || all.contains("DEFENSE") ||
      all.contains("基地防禦") || all.contains("基地防御") || all.contains("升級獎勵") || all.contains("升级奖励")
     val quick=lines.firstOrNull { it.first.replace(" ","").contains("快速戰鬥") || it.first.replace(" ","").contains("快速战斗") }
     val enter=lines.firstOrNull { it.first.replace(" ","").contains("進入戰鬥") || it.first.replace(" ","").contains("进入战斗") }
     val detected=if(Settings.battle) BattleDetector.detect(bitmap) else null
     val battleScreen=(quick!=null || enter!=null || detected?.enter!=null) && (all.contains("STAGE") || all.contains("戰力") || all.contains("战力") || all.contains("WEAKPOINT") || all.contains("EFFECTIVERANGE"))
     val target:Triple<String,Float,Float>?=when {
      Settings.skip && popup && next!=null -> Triple("upgrade-popup",w*.50f,h*.74f)
      Settings.skip && popup -> Triple("upgrade-popup",w*.50f,h*.76f)
      Settings.skip && skip!=null -> Triple("skip",skip.second,skip.third)
      Settings.skip && next!=null -> Triple("result-next",w*.50f,h*.77f)
      Settings.battle && battleScreen && detected?.quick!=null && (quick!=null || detected.quick.confidence>.17f) ->
       Triple("quick",detected.quick.x,detected.quick.y)
      Settings.battle && battleScreen && detected?.enter!=null -> Triple("enter",detected.enter.x,detected.enter.y)
      Settings.battle && battleScreen && enter!=null -> Triple("enter",enter.second,enter.third)
      else -> null
     }
     if(target!=null) {
      noTargetSince=0L
      if(lastTarget==target.first)stable++ else {lastTarget=target.first;stable=1}
      val now=SystemClock.elapsedRealtime()
      status("辨識 ${target.first}，確認 ${stable}/2；畫面 ${Settings.frames} 張")
      if(stable>=2 && now-lastTap>=1500L) {
       val tap=Settings.tap
       if(tap!=null) {
        tap.click(target.second,target.third)
        Settings.taps++
        status("已送出 ${target.first} 點擊，共 ${Settings.taps} 次")
       } else status("無障礙服務未連接：請檢查系統設定")
       lastTap=now
       stable=0
      }
     } else {
      stable=0;lastTarget=""
      status("分析中：${Settings.frames} 張；OCR: ${all.take(45)}")
      // Map monster detection is not implemented yet. Do not patrol without a reliable map classifier.
      noTargetSince=0L
     }
    } catch(e:Exception) {status("辨識處理失敗: ${e.javaClass.simpleName}")}
   }
   .addOnFailureListener {status("OCR 失敗: ${it.javaClass.simpleName}")}
   .addOnCompleteListener {busy=false;bitmap.recycle()}
 }

 private fun isOrangeNear(bitmap:Bitmap,x:Int,y:Int):Boolean {
  var hits=0;var total=0
  val rx=(bitmap.width*.10f).toInt().coerceAtLeast(20)
  val ry=(bitmap.height*.018f).toInt().coerceAtLeast(10)
  for(dy in -ry..ry step 8) for(dx in -rx..rx step 8) {
   val xx=x+dx;val yy=y+dy
   if(xx in 0 until bitmap.width && yy in 0 until bitmap.height) {
    val c=bitmap.getPixel(xx,yy)
    val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
    total++;if(r>190 && g in 65..205 && b<125 && r>g+35)hits++
   }
  }
  return total>0 && hits.toFloat()/total>.08f
 }

 override fun onDestroy(){
  Settings.running=false;Settings.status="已停止"
  display?.release();reader?.close();projection?.stop();recognizer.close()
  super.onDestroy()
 }
}
