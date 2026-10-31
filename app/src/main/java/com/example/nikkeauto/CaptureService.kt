package com.example.nikkeauto
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjectionManager
import android.os.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
class CaptureService:Service(){
 private val handler=Handler(Looper.getMainLooper())
 private val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
 private var reader:ImageReader?=null
 private var projection:android.media.projection.MediaProjection?=null
 private var busy=false
 private var lastTap=0L
 private var lastTarget=""
 private var stable=0
 override fun onBind(intent:Intent?)=null
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
 val channel="capture";val manager=getSystemService(NOTIFICATION_SERVICE) as NotificationManager
 manager.createNotificationChannel(NotificationChannel(channel,"畫面辨識",NotificationManager.IMPORTANCE_LOW))
 val notification=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("NIKKE 自動化執行中").setContentText("點選 App 停止；螢幕畫面僅在裝置上分析").build()
 startForeground(1,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
 val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra("resultData",Intent::class.java) else @Suppress("DEPRECATION") intent?.getParcelableExtra<Intent>("resultData")
 if(data==null){stopSelf();return START_NOT_STICKY}
 val dm=resources.displayMetrics;val w=dm.widthPixels;val h=dm.heightPixels
 val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
 projection=mgr.getMediaProjection(intent.getIntExtra("resultCode",Activity.RESULT_CANCELED),data)
 projection?.registerCallback(object:android.media.projection.MediaProjection.Callback(){override fun onStop(){stopSelf()}},handler)
 reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
 projection?.createVirtualDisplay("nikke-capture",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader!!.surface,null,handler)
 handler.post(object:Runnable{override fun run(){if(Settings.running) {capture(w,h);handler.postDelayed(this,650)}else stopSelf()}})
 return START_NOT_STICKY
 }
 private fun capture(w:Int,h:Int){if(busy)return;val img=reader?.acquireLatestImage()?:return
 try{val plane=img.planes[0];val row=plane.rowStride;val px=plane.pixelStride;val bitmap=Bitmap.createBitmap(w+(row-w*px)/px,h,Bitmap.Config.ARGB_8888);bitmap.copyPixelsFromBuffer(plane.buffer);val cropped=Bitmap.createBitmap(bitmap,0,0,w,h);bitmap.recycle();busy=true
 recognizer.process(InputImage.fromBitmap(cropped,0)).addOnSuccessListener{result->
 val lines=result.textBlocks.flatMap{it.lines}.mapNotNull{line->line.boundingBox?.let{Triple(line.text,it.centerX().toFloat(),it.centerY().toFloat())}}
 val skip=lines.firstOrNull{it.first.contains("SKIP",true)}
 val next=lines.firstOrNull{it.first.contains("點擊任意處")||it.first.contains("进行下一步")}
 val quick=lines.firstOrNull{it.first.contains("快速戰鬥")||it.first.contains("快速战斗")}
 val enter=lines.firstOrNull{it.first.contains("進入戰鬥")||it.first.contains("进入战斗")}
 val stage=lines.any{it.first.contains("STAGE",true)}
 val target=when{
 Settings.skip && skip!=null -> Triple("skip",skip.second,skip.third)
 Settings.skip && next!=null -> Triple("next",w*0.50f,h*0.80f)
 Settings.battle && stage && quick!=null && isOrange(cropped,quick.second.toInt(),quick.third.toInt()) -> Triple("quick",quick.second,quick.third)
 Settings.battle && stage && enter!=null -> Triple("enter",enter.second,enter.third)
 else -> null
 }
 if(target!=null){val key=target.first;if(key==lastTarget)stable++ else {lastTarget=key;stable=1};val now=SystemClock.elapsedRealtime();if(stable>=2 && now-lastTap>=1800){Settings.tap?.click(target.second,target.third);lastTap=now;stable=0}}
 else {stable=0;lastTarget=""}
 }.addOnCompleteListener{busy=false;cropped.recycle()}
 }finally{img.close()}}
 private fun isOrange(bitmap:Bitmap,x:Int,y:Int):Boolean{val c=bitmap.getPixel(x.coerceIn(0,bitmap.width-1),y.coerceIn(0,bitmap.height-1));val r=android.graphics.Color.red(c);val g=android.graphics.Color.green(c);val b=android.graphics.Color.blue(c);return r>175 && g in 65..210 && b<110}
 override fun onDestroy(){Settings.running=false;reader?.close();projection?.stop();recognizer.close();super.onDestroy()}
}