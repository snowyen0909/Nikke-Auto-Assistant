package com.example.nikkeauto
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.widget.*
class MainActivity:Activity(){
 private val captureRequest=100
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(35,45,35,25)}
 fun toggle(title:String, initial:Boolean, change:(Boolean)->Unit){val sw=Switch(this).apply{text=title;isChecked=initial;setOnCheckedChangeListener{_,checked->change(checked)}};root.addView(sw)}
 root.addView(TextView(this).apply{text="NIKKE Auto Assistant";textSize=24f})
 toggle("自動跳過劇情 / 結算",Settings.skip){Settings.skip=it}
 toggle("自動戰鬥確認",Settings.battle){Settings.battle=it}
 toggle("地圖自動尋敵與導航（實驗性）",Settings.map){Settings.map=it}
 toggle("地圖巡弋 / 縮放（實驗性）",Settings.patrol){Settings.patrol=it}
 root.addView(Button(this).apply{text="1. 開啟無障礙服務";setOnClickListener{startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))}})
 root.addView(Button(this).apply{text="2. 授權截圖並啟動";setOnClickListener{val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;startActivityForResult(mgr.createScreenCaptureIntent(),captureRequest)}})
 root.addView(Button(this).apply{text="停止";setOnClickListener{Settings.running=false;stopService(Intent(this@MainActivity,CaptureService::class.java))}})
 root.addView(TextView(this).apply{text="需要手動授權螢幕錄製及無障礙。執行時請保持遊戲在前景。"})
 setContentView(root)
 }
 @Deprecated("Legacy activity result") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==captureRequest && resultCode==RESULT_OK && data!=null){Settings.running=true;val i=Intent(this,CaptureService::class.java).putExtra("resultCode",resultCode).putExtra("resultData",data);startForegroundService(i)}}
}