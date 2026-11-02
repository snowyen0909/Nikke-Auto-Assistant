package com.example.nikkeauto

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/** Entry point used by the overlay to edit each daily module. */
class RuleEditorActivity:Activity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setPadding(22,35,22,20)
  }
  root.addView(TextView(this).apply{
   text="日常規則編輯器";textSize=24f
  })
  root.addView(TextView(this).apply{
   text="每個模組可獨立設定 OCR、圖片條件、成功與失敗動作。未驗證的自訂規則不會自動點擊。"
  })
  root.addView(MaterialButton(this).apply{
   text="匯入圖片模板（先裁切出按鈕）";isAllCaps=false
   setOnClickListener{
    startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
     addCategory(Intent.CATEGORY_OPENABLE);type="image/*"
    },81)
   }
  })
  root.addView(TextView(this).apply{
   text="已匯入模板："+UserTemplateMatcher.names(this@RuleEditorActivity).joinToString("、")
  })
  DailyRoutine.steps.forEach{step->
   root.addView(MaterialButton(this).apply{
    text="編輯 · ${step.title}";isAllCaps=false
    setOnClickListener{RuleEditorUi.open(this@RuleEditorActivity,step.id,step.title)}
   })
  }
  setContentView(ScrollView(this).apply{addView(root)})
 }
 @Deprecated("Legacy result callback")
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode==81 && resultCode==RESULT_OK && data?.data!=null){
   val name=UserTemplateMatcher.import(this,data.data!!)
   Toast.makeText(this,if(name!=null)"圖片模板已存入：$name（請填入辨識目標）" else
    "圖片匯入失敗，請選擇正常的裁切圖片",Toast.LENGTH_LONG).show()
   recreate()
  }
 }
}
