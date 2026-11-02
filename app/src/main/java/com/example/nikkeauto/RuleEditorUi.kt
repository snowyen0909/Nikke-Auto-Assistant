package com.example.nikkeauto

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputType
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton

/** Full native Material 3 visual rule editor. */
object RuleEditorUi {
 private fun spinner(a:Activity,values:List<String>,selected:String):Spinner =
  Spinner(a).apply{
   adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,values)
   setSelection(values.indexOf(selected).coerceAtLeast(0))
  }
 fun open(a:Activity,id:String,title:String){
  val main=LinearLayout(a).apply{
   orientation=LinearLayout.VERTICAL;setPadding(20,12,20,12)
  }
  val list=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL}
  val scroll=ScrollView(a).apply{addView(list)}
  main.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  val buttons=LinearLayout(a).apply{orientation=LinearLayout.HORIZONTAL}
  fun button(text:String,cb:()->Unit){
   buttons.addView(MaterialButton(a).apply{
    this.text=text;isAllCaps=false;setOnClickListener{cb()}
   },LinearLayout.LayoutParams(0,-2,1f))
  }
  fun edit(index:Int?,refresh:()->Unit){
   val old=index?.let{RuleStore.rules(a,id).getOrNull(it)}?:RuleStore.Rule("新步驟")
   val form=LinearLayout(a).apply{
    orientation=LinearLayout.VERTICAL;setPadding(24,12,24,12)
   }
   fun field(hint:String,value:String):EditText{
    val input=EditText(a).apply{this.hint=hint;setSingleLine(true);setText(value)}
    form.addView(input);return input
   }
   val name=field("步驟名稱",old.name)
   val target=field("文字關鍵字／模板名稱",old.target)
   fun picker(label:String,choices:List<String>,value:String):Spinner{
    form.addView(TextView(a).apply{text=label})
    return spinner(a,choices,value).also{form.addView(it)}
   }
   val method=picker("辨識方式",listOf("OCR","IMAGE","BOTH"),old.method)
   val region=picker("搜尋區域",listOf("FULL","TOP","BOTTOM","LEFT","RIGHT","CENTER"),old.region)
   val success=picker("成功動作",listOf("TAP","NEXT","WAIT","PAUSE"),old.success)
   val failure=picker("失敗動作",listOf("RETRY","SKIP","PAUSE","BACK"),old.failure)
   val delay=field("操作後等待 ms",old.delayMs.toString()).apply{inputType=InputType.TYPE_CLASS_NUMBER}
   val retries=field("重試次數",old.attempts.toString()).apply{inputType=InputType.TYPE_CLASS_NUMBER}
   val enabled=CheckBox(a).apply{text="啟用此步驟";isChecked=old.enabled}
   form.addView(enabled)
   MaterialAlertDialogBuilder(a).setTitle("編輯規則 · ${title}")
    .setView(ScrollView(a).apply{addView(form)})
    .setNegativeButton("取消",null)
    .setPositiveButton("儲存"){_,_->
     val rule=RuleStore.Rule(name.text.toString().ifBlank{"步驟"},method.selectedItem.toString(),
      target.text.toString(),region.selectedItem.toString(),success.selectedItem.toString(),
      failure.selectedItem.toString(),
      (delay.text.toString().toLongOrNull()?:3000L).coerceIn(500,15000),
      (retries.text.toString().toIntOrNull()?:3).coerceIn(0,10),enabled.isChecked)
     val items=RuleStore.rules(a,id).toMutableList()
     if(index==null)items.add(rule) else if(index in items.indices)items[index]=rule
     RuleStore.save(a,id,items);refresh()
    }.show()
  }
  fun render(){
   list.removeAllViews()
   RuleStore.rules(a,id).toList().forEachIndexed{index,rule->
    val row=LinearLayout(a).apply{orientation=LinearLayout.VERTICAL;setPadding(4,6,4,8)}
    row.addView(TextView(a).apply{
     text="${index+1}. ${rule.name}  [${rule.method}]  ${if(rule.enabled)"●" else "○"}\n${rule.target}  •  ${rule.success} / ${rule.failure}"
     textSize=15f
    })
    val actions=LinearLayout(a)
    fun act(s:String,fn:()->Unit){
     actions.addView(MaterialButton(a).apply{
      text=s;isAllCaps=false;setOnClickListener{fn()}
     },LinearLayout.LayoutParams(0,-2,1f))
    }
    act("編輯"){edit(index,::render)}
    act("↑"){
     val items=RuleStore.rules(a,id).toMutableList()
     if(index>0){val x=items.removeAt(index);items.add(index-1,x);RuleStore.save(a,id,items);render()}
    }
    act("↓"){
     val items=RuleStore.rules(a,id).toMutableList()
     if(index<items.lastIndex){val x=items.removeAt(index);items.add(index+1,x);RuleStore.save(a,id,items);render()}
    }
    act("刪除"){
     MaterialAlertDialogBuilder(a).setMessage("刪除「${rule.name}」？")
      .setNegativeButton("取消",null).setPositiveButton("刪除"){_,_->
       val items=RuleStore.rules(a,id).toMutableList()
       if(index in items.indices){items.removeAt(index);RuleStore.save(a,id,items);render()}
      }.show()
    }
    row.addView(actions);list.addView(row)
   }
  }
  button("新增"){edit(null,::render)}
  button("複製 JSON"){
   val clipboard=a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
   clipboard.setPrimaryClip(ClipData.newPlainText("rules",RuleStore.export(a,id)))
   Toast.makeText(a,"規則 JSON 已複製",Toast.LENGTH_SHORT).show()
  }
  button("匯入"){
   val input=EditText(a).apply{hint="貼上 JSON 規則";minLines=4}
   MaterialAlertDialogBuilder(a).setTitle("匯入規則 JSON").setView(input)
    .setNegativeButton("取消",null).setPositiveButton("匯入"){_,_->
     if(RuleStore.import(a,id,input.text.toString()))render()
     else Toast.makeText(a,"JSON 格式無效",Toast.LENGTH_LONG).show()
    }.show()
  }
  main.addView(buttons)
  render()
  MaterialAlertDialogBuilder(a).setTitle("${title} · 步驟規則")
   .setView(main).setPositiveButton("關閉",null).show().window?.setLayout(
    android.view.WindowManager.LayoutParams.MATCH_PARENT,
    (a.resources.displayMetrics.heightPixels*.84f).toInt())
 }
}
