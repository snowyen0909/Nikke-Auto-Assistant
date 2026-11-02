package com.example.nikkeauto

import android.app.Activity
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Material 3 per-task diagnosis editor. */
object DailyDebugUi {
 fun show(activity:Activity,id:String,title:String){
  val d=activity.resources.displayMetrics.density
  fun px(v:Int)=(v*d+.5f).toInt()
  val cfg=DailyDebug.get(id)
  val view=LinearLayout(activity).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(px(22),px(8),px(22),px(8))
  }
  fun field(label:String,value:Long):EditText{
   view.addView(TextView(activity).apply{text=label;textSize=14f})
   val input=EditText(activity).apply{
    inputType=InputType.TYPE_CLASS_NUMBER
    setSingleLine(true)
    setText(value.toString())
   }
   view.addView(input)
   return input
  }
  val wait=field("成功點擊後等待（毫秒，1000–12000）",cfg.postTapMs)
  val delay=field("辨識失敗後重試間隔（毫秒，1500–12000）",cfg.retryDelayMs)
  val tries=field("辨識失敗重試次數（0–5）",cfg.retries.toLong())
  view.addView(TextView(activity).apply{text="超過重試次數後";textSize=14f})
  val fail=Spinner(activity)
  fail.adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,
   listOf("暫停並等待人工處理","略過目前任務"))
  fail.setSelection(if(cfg.failure==DailyDebug.Failure.PAUSE)0 else 1)
  view.addView(fail)
  MaterialAlertDialogBuilder(activity)
   .setTitle("${title} · 調試設定")
   .setMessage("已辨識的文字按鈕會點選實際 OCR 座標；找不到時不盲點。設定僅影響此模組。")
   .setView(view)
   .setNegativeButton("取消",null)
   .setPositiveButton("保存"){_,_->
    fun num(input:EditText,default:Long)=input.text.toString().toLongOrNull()?:default
    DailyDebug.update(activity,id,DailyDebug.Config(
     postTapMs=num(wait,cfg.postTapMs),
     retryDelayMs=num(delay,cfg.retryDelayMs),
     retries=num(tries,cfg.retries.toLong()).toInt(),
     failure=if(fail.selectedItemPosition==0)DailyDebug.Failure.PAUSE else DailyDebug.Failure.SKIP
    ))
   }.show()
 }
}
