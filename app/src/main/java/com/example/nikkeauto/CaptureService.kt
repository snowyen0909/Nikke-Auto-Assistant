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
 private var lastLoggedTarget = ""
 private var lastLoggedAt = 0L
 private var failureHandled = false
 private var lastMinimapClose = 0L
 private var lastPadTap = 0L
 private var lastTargetX = -1f
 private var lastTargetY = -1f
 private val mapState=MapNavigationState()
 private val notificationChannel = "capture"
 private val ACTION_PAUSE="com.example.nikkeauto.PAUSE"
 private val ACTION_RESUME="com.example.nikkeauto.RESUME"

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
 private fun notification(s: String): Notification {
  val action=if(Settings.paused)ACTION_RESUME else ACTION_PAUSE
  val label=if(Settings.paused)"繼續" else "暫停"
  val control=Intent(this,CaptureService::class.java).setAction(action)
  val pending=PendingIntent.getService(this,if(Settings.paused)2 else 1,control,
   PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this, notificationChannel)
   .setSmallIcon(android.R.drawable.ic_menu_view)
   .setContentTitle("NIKKE Auto Assistant")
   .setContentText(s)
   .setOngoing(true)
   .addAction(android.R.drawable.ic_media_pause,label,pending)
   .build()
 }
 private fun setPaused(paused:Boolean) {
  Settings.paused=paused
  lastTarget=""
  stable=0
  Settings.status=if(paused)"已暫停（螢幕擷取保持啟用）" else "已繼續自動化"
  val mgr=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  mgr.notify(1,notification(Settings.status))
 }

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if(intent?.action==ACTION_PAUSE){setPaused(true);return START_NOT_STICKY}
  if(intent?.action==ACTION_RESUME){setPaused(false);return START_NOT_STICKY}
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
    if(!Settings.paused)capture(w,h)
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
     // Daily routine has exclusive priority over ordinary map/story automation.
     if(Settings.dailyMode && DailyRoutine.active){
      val action=DailyRoutine.decide(lines,w,h)
      if(action!=null && Settings.running && !Settings.paused){
       val sent=Settings.tap?.click(action.x,action.y) ?: false
       if(sent)Settings.taps++
      }
      Settings.status=DailyRoutine.status
      return@addOnSuccessListener
     }
     if(Settings.dailyMode && !DailyRoutine.active)Settings.dailyMode=false
     // OCR often merges "HIDE AUTO LOG SKIP" into ONE line. Its center is
     // near AUTO, not SKIP. Never tap the center of a merged toolbar line.
     val skip=lines.firstOrNull {
      val normalized=it.first.replace(Regex("[^A-Za-z]"),"").uppercase()
      normalized=="SKIP" && it.second>w*.60f && it.third<h*.12f
     }
     val skipToolbarLine=lines.firstOrNull {
      it.first.contains("SKIP",true) && it.third<h*.14f
     }
     // A merged OCR line (HIDE AUTO LOG SKIP) has its center over AUTO.
     // Aim at the rightmost SKIP label instead of the merged-line center.
     val visibleSkip=skip ?: skipToolbarLine
     val skipTapX=if(skip!=null)skip.second else w*.89f
     val skipTapY=(visibleSkip?.third ?: h*.023f).coerceIn(h*.010f,h*.095f)
     // The fallback is only allowed when the story HUD is positively detected.
     val storyHud=(all.contains("HIDE") && all.contains("AUTO") &&
      (all.contains("LOG") || all.contains("SKIP"))) ||
      (skipToolbarLine!=null && all.contains("AUTO"))
     val skipIcon=if(Settings.skip) SkipDetector.find(bitmap) else null
     val skipFallback=false // Never tap assumed toolbar coordinates: previously hit AUTO
     val skipX=w*.875f
     // Reuse the actual toolbar row if OCR grouped the labels; fixed fallback
     // must stay near the TOP edge, never at the center of the AUTO label.
     val skipY=(skipToolbarLine?.third ?: h*.022f).coerceIn(h*.012f,h*.065f)
     val next=lines.firstOrNull {
      val t=it.first.replace(" ","")
      t.contains("點擊任意處") || t.contains("点击任意处") || t.contains("進行下一步") || t.contains("进行下一步")
     }
     val failed=all.contains("OPERATIONFAILED") ||
      (all.contains("OPERATION") && all.contains("FAILED")) ||
      (all.contains("逾時") && (all.contains("重新挑戰") || all.contains("編隊") || all.contains("编队")))
     if(!failed)failureHandled=false
     val resultSignature=(all.contains("STAGECLEARCOUNT") ||
       (all.contains("REWARD") && (all.contains("STAGE") || all.contains("OPERATION"))) ||
       (all.contains("OPERATIONCOMPLETE") && all.contains("STAGE")))
     val hasResult=next!=null || resultSignature
     val popup=(all.contains("OUTPOSTDEFENSE") || all.contains("基地防禦") || all.contains("基地防御") || all.contains("升級獎勵") || all.contains("升级奖励")) && hasResult
     val quick=lines.firstOrNull { it.first.replace(" ","").contains("快速戰鬥") || it.first.replace(" ","").contains("快速战斗") }
     val enter=lines.firstOrNull { it.first.replace(" ","").contains("進入戰鬥") || it.first.replace(" ","").contains("进入战斗") }
     // Require stage heading and at least one battle action label.
     // Do not require the old white-panel pixel threshold: it rejects
     // legitimate 14:9 portrait and landscape layouts.
     val battleText=all.contains("STAGE") || all.contains("BOSS") || all.contains("戰力") || all.contains("战力")
     val blueEnter=if(Settings.battle && battleText) BattleDetector.findBlueEnterButton(bitmap) else null
     val orangeQuick=if(Settings.battle && battleText) BattleDetector.findOrangeQuickButton(bitmap) else null
     val battleScreen=battleText && (quick!=null || enter!=null || blueEnter!=null || orangeQuick!=null)
     val quickState=if(quick!=null)BattleDetector.stateNear(bitmap,quick.second,quick.third) else BattleDetector.ColorState.UNKNOWN
     val enterState=if(enter!=null)BattleDetector.stateNear(bitmap,enter.second,enter.third) else BattleDetector.ColorState.UNKNOWN
     val storyScreen=skip!=null || storyHud || skipIcon!=null
     val resultScreen=hasResult || popup
     // OCR sometimes fails on the small 野外 text; combine scene context with
     // minimap recognition, while still excluding battles, story and results.
     val detectedMinimap=if(Settings.map && !battleScreen && !storyScreen && !resultScreen)
      MapDetector.expandedMinimap(bitmap) else null
     val outdoorMap=!battleScreen && !storyScreen && !resultScreen &&
      (all.contains("野外") || all.contains("CURRENTAREA") ||
       all.contains("NORMAL") || detectedMinimap!=null)
     val enlargedMap=detectedMinimap
     val blueCore=if(Settings.map && outdoorMap && enlargedMap==null)
      MapDetector.blueCoreRedRing(bitmap) else null
     val monster=if(Settings.map && outdoorMap && enlargedMap==null)
      blueCore ?: MapDetector.redRingMonster(bitmap) else null
     val marker=if(Settings.map && outdoorMap && enlargedMap==null)
      MapDetector.navigationDiamond(bitmap) else null
     val overhead=if(Settings.map && outdoorMap && monster==null)
      MapDetector.overheadEnemyMarker(bitmap,marker) else null
     val offscreen=if(overhead==null)marker else null
     val boss=if(Settings.map && outdoorMap && monster==null && marker==null && enlargedMap==null)
      MapDetector.bossPortal(bitmap) else null
     val mini=if(Settings.map && outdoorMap)MapDetector.minimapEnemyDirection(bitmap) else 0
     val pad=if(Settings.map && outdoorMap && monster==null && marker==null &&
       enlargedMap==null)MapDetector.inactiveGroundPad(bitmap) else null
     val nowMap=SystemClock.elapsedRealtime()
     // Map state machine has exclusive priority, including the overlay close.
     val mapChoice=if(Settings.map && outdoorMap && !Settings.paused)
      mapState.next(w,h,enlargedMap,monster,overhead,offscreen,nowMap) else null
     val mapBusy=Settings.map && outdoorMap && (enlargedMap!=null ||
      mapState.isCameraMoving || mapState.phase==MapNavigationState.Phase.CLOSING_MAP ||
      mapState.phase==MapNavigationState.Phase.APPROACHING_MONSTER)
     val idleMap=Settings.map && outdoorMap && monster==null && marker==null && boss==null
     val target:Triple<String,Float,Float>?=when {
      failed && !failureHandled -> Triple("failed-return",w*.16f,h*.943f)
      Settings.skip && popup -> Triple("upgrade-popup",w*.50f,h*.76f)
      Settings.skip && visibleSkip!=null -> Triple("skip-visible",skipTapX,skipTapY)
      Settings.skip && skipIcon!=null -> Triple("skip-image",skipIcon.x,skipIcon.y)
      Settings.skip && hasResult && !failed -> Triple("result-next",w*.53f,h*.87f)
      Settings.battle && battleScreen && orangeQuick!=null ->
       Triple("quick",orangeQuick.x,orangeQuick.y)
      Settings.battle && battleScreen && quick!=null && quickState==BattleDetector.ColorState.ORANGE ->
       Triple("quick",quick.second,quick.third)
      Settings.battle && battleScreen && blueEnter!=null ->
       Triple("enter",blueEnter.x,blueEnter.y)
      Settings.battle && battleScreen && enter!=null && enterState==BattleDetector.ColorState.BLUE ->
       Triple("enter",enter.second,enter.third)
      mapChoice!=null ->
       Triple(mapChoice.kind,mapChoice.x,mapChoice.y)
      mapBusy -> null
      Settings.map && outdoorMap && mapState.canPatrol(nowMap) &&
       pad!=null && nowMap-lastPadTap>20000L ->
       Triple("map-pad",pad.x,pad.y)
      Settings.map && outdoorMap && mapState.canPatrol(nowMap) && boss!=null ->
       Triple("map-boss",boss.x,boss.y)
      else -> null
     }
      if(target!=null) {
       noTargetSince=0L
       if(lastTarget==target.first) stable++ else {lastTarget=target.first;stable=1}
       val now=SystemClock.elapsedRealtime()
       status("目標 ${target.first} @(${target.second.toInt()},${target.third.toInt()}) 確認 ${stable}/2")
       val cooldown=when(target.first) {"map-monster","map-nav","map-boss","map-pad"->6500L;"skip-visible"->1200L;else->1500L}
       val requiredFrames=if(target.first=="skip-visible" ||
        target.first=="close-minimap" || target.first=="map-nav" ||
        target.first=="map-monster")1 else 2
       if(!Settings.paused && Settings.running && stable>=requiredFrames && now-lastTap>=cooldown) {
        val tap=Settings.tap
        if(tap!=null && tap.click(target.second,target.third)) {
         Settings.taps++
         if(target.first=="close-minimap")lastMinimapClose=now
         if(target.first=="map-pad")lastPadTap=now
         status("已送出 ${target.first} 點擊，共 ${Settings.taps} 次")
         if(target.first=="failed-return") {
          failureHandled=true
          Settings.map=false
          Settings.patrol=false
          Settings.paused=true
         }
        }
        lastTap=now
        stable=0
       }
      } else {
       stable=0;lastTarget=""
       status("分析中：${Settings.frames} 張；劇情=${storyHud} SKIP文字=${visibleSkip!=null} SKIP圖=${skipIcon?.score ?: 0f} 地圖=${outdoorMap} 紅圈=${monster?.score ?: 0} 導航=${nav!=null} Boss=${boss!=null} 戰鬥=${battleScreen} 橘鍵=${orangeQuick!=null} 藍鍵=${blueEnter!=null} OCR: ${all.take(25)}")
       if(idleMap && pad==null && enlargedMap==null && mapState.canPatrol(nowMap) && Settings.patrol && !Settings.paused && Settings.running){
        if(noTargetSince==0L)noTargetSince=nowMap
        if(nowMap-noTargetSince>9000L && nowMap-lastTap>6500L){
         val tap=Settings.tap
         if(tap!=null){
          when(patrolStep++%5){
           0->tap.zoomOut(w.toFloat(),h.toFloat())
           1->if(mini<0)tap.swipe(w*.28f,h*.50f,w*.75f,h*.50f) else tap.swipe(w*.75f,h*.50f,w*.28f,h*.50f)
           2->if(mini>0)tap.swipe(w*.75f,h*.50f,w*.28f,h*.50f) else tap.swipe(w*.28f,h*.50f,w*.75f,h*.50f)
           3->tap.swipe(w*.50f,h*.72f,w*.50f,h*.30f)
           else->tap.swipe(w*.50f,h*.30f,w*.50f,h*.72f)
          }
          lastTap=nowMap;noTargetSince=nowMap
          status("巡弋視角 ${patrolStep%5}/5，小地圖方向=${mini}")
         }
        }
       }else noTargetSince=0L
      }
     } catch(e:Exception) {
      status("辨識處理失敗: ${e.javaClass.simpleName}")
     }
    }
    .addOnFailureListener {status("OCR 失敗: ${it.javaClass.simpleName}")}

   .addOnCompleteListener {busy=false;bitmap.recycle()}
 }

 private fun isBattlePanel(b:Bitmap):Boolean {
  // White stage-detail panel at the bottom, plus blue enter button.
  // Do not use blue-button detection alone on the exploration map.
  val w=b.width;val h=b.height
  var bright=0;var count=0
  for(iy in 72..90 step 3) for(ix in 8..45 step 4) {
   val x=(w*ix/100).coerceIn(0,w-1)
   val y=(h*iy/100).coerceIn(0,h-1)
   val c=b.getPixel(x,y)
   if(Color.red(c)>215 && Color.green(c)>215 && Color.blue(c)>215)bright++
   count++
  }
  return count>0 && bright.toFloat()/count>.72f
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
  Settings.running=false;Settings.paused=false;Settings.status="已停止"
  display?.release();reader?.close();projection?.stop();recognizer.close()
  super.onDestroy()
 }
}
