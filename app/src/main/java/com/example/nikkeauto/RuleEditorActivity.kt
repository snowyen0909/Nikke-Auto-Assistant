package com.example.nikkeauto

import android.app.Activity
import android.os.Bundle
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
  DailyRoutine.steps.forEach{step->
   root.addView(MaterialButton(this).apply{
    text="編輯 · ${step.title}";isAllCaps=false
    setOnClickListener{RuleEditorUi.open(this@RuleEditorActivity,step.id,step.title)}
   })
  }
  setContentView(ScrollView(this).apply{addView(root)})
 }
}
