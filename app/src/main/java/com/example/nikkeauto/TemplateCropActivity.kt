package com.example.nikkeauto

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Crop icon templates from a screenshot taken on this phone, so template
 * matching uses the device's own pixels instead of the tutorial crops.
 * Plain framework views only.
 */
class TemplateCropActivity:Activity(){
 private val slots=listOf(
  "close" to "關閉 X（彈窗右上角）",
  "home" to "返回主頁（底部房子圖示）",
  "mailbox" to "信箱圖示（大廳右上角）",
  "fast_on" to "快速戰鬥 ON（框住標題＋開關）",
  "fast_off" to "快速戰鬥 OFF（框住標題＋開關）")

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val shot=File(filesDir,"template_snapshot.png")
  val bmp=if(shot.exists())BitmapFactory.decodeFile(shot.path) else null
  if(bmp==null){
   Toast.makeText(this,"還沒有擷取畫面：請先按「5 秒後擷取遊戲畫面」",Toast.LENGTH_LONG).show()
   finish();return
  }
  val pad=(12*resources.displayMetrics.density).toInt()
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL;setBackgroundColor(0xFF0B1522.toInt());setPadding(pad,pad,pad,pad)}
  root.addView(TextView(this).apply{
   text="用手指拖出方框框住按鈕（盡量貼齊），選擇種類後按儲存。可重拖。"
   setTextColor(Color.WHITE);textSize=13f})
  val crop=CropView(this,bmp)
  root.addView(crop,LinearLayout.LayoutParams(-1,0,1f).apply{topMargin=pad/2})
  val group=RadioGroup(this).apply{orientation=RadioGroup.VERTICAL}
  slots.forEachIndexed{i,(name,label)->
   val custom=DailyTemplates.userTemplate(filesDir,name).exists()
   group.addView(RadioButton(this).apply{
    id=i+1;text=label+if(custom)"　✓已校正" else "";setTextColor(Color.WHITE);textSize=13f})
  }
  group.check(1)
  root.addView(group)
  val buttons=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  fun selected()=slots[(group.checkedRadioButtonId-1).coerceIn(0,slots.size-1)]
  buttons.addView(Button(this).apply{text="儲存";setOnClickListener{
   val r=crop.selection()
   if(r==null || r.width()<12 || r.height()<12){
    Toast.makeText(this@TemplateCropActivity,"方框太小，請重新框選",Toast.LENGTH_SHORT).show()
    return@setOnClickListener
   }
   val (name,label)=selected()
   try{
    val dir=File(filesDir,"templates").apply{mkdirs()}
    val part=Bitmap.createBitmap(bmp,r.left,r.top,r.width(),r.height())
    DailyTemplates.userTemplate(filesDir,name).outputStream().use{part.compress(Bitmap.CompressFormat.PNG,100,it)}
    part.recycle()
    DailyTemplates.userTemplateWidth(filesDir,name).writeText(bmp.width.toString())
    DailyTemplates.invalidate(name)
    (group.findViewById<RadioButton>(group.checkedRadioButtonId)).text="$label　✓已校正"
    Toast.makeText(this@TemplateCropActivity,"已儲存：$label（${r.width()}×${r.height()}）",Toast.LENGTH_SHORT).show()
    if(!dir.exists())throw IllegalStateException()
   }catch(e:Exception){
    Toast.makeText(this@TemplateCropActivity,"儲存失敗：${e.javaClass.simpleName}",Toast.LENGTH_LONG).show()
   }
  }},LinearLayout.LayoutParams(0,-2,1f))
  buttons.addView(Button(this).apply{text="恢復預設";setOnClickListener{
   val (name,label)=selected()
   DailyTemplates.userTemplate(filesDir,name).delete()
   DailyTemplates.userTemplateWidth(filesDir,name).delete()
   DailyTemplates.invalidate(name)
   (group.findViewById<RadioButton>(group.checkedRadioButtonId)).text=label
   Toast.makeText(this@TemplateCropActivity,"已恢復預設：$label",Toast.LENGTH_SHORT).show()
  }},LinearLayout.LayoutParams(0,-2,1f))
  buttons.addView(Button(this).apply{text="完成";setOnClickListener{finish()}},
   LinearLayout.LayoutParams(0,-2,1f))
  root.addView(buttons)
  setContentView(root)
 }

 /** Shows the screenshot fit-center and lets the user drag a selection box. */
 private class CropView(ctx:Context,private val bmp:Bitmap):View(ctx){
  private val toView=Matrix();private val toBitmap=Matrix()
  private var sx=0f;private var sy=0f;private var ex=0f;private var ey=0f
  private var hasBox=false
  private val stroke=Paint().apply{style=Paint.Style.STROKE;color=0xFFFF4D6D.toInt()
   strokeWidth=2.5f*ctx.resources.displayMetrics.density}
  private val fill=Paint().apply{style=Paint.Style.FILL;color=0x33FF4D6D}
  override fun onSizeChanged(w:Int,h:Int,ow:Int,oh:Int){
   val s=min(w/bmp.width.toFloat(),h/bmp.height.toFloat())
   toView.setScale(s,s);toView.postTranslate((w-bmp.width*s)/2f,(h-bmp.height*s)/2f)
   toView.invert(toBitmap)
  }
  override fun onDraw(c:Canvas){
   c.drawBitmap(bmp,toView,null)
   if(hasBox){
    val r=RectF(min(sx,ex),min(sy,ey),max(sx,ex),max(sy,ey))
    c.drawRect(r,fill);c.drawRect(r,stroke)
   }
  }
  override fun onTouchEvent(e:MotionEvent):Boolean{
   when(e.actionMasked){
    MotionEvent.ACTION_DOWN->{sx=e.x;sy=e.y;ex=e.x;ey=e.y;hasBox=true}
    MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP->{ex=e.x;ey=e.y}
   }
   invalidate();return true
  }
  /** Selection in screenshot pixel coordinates, clamped to the image. */
  fun selection():Rect?{
   if(!hasBox)return null
   val p=floatArrayOf(min(sx,ex),min(sy,ey),max(sx,ex),max(sy,ey))
   toBitmap.mapPoints(p)
   val l=p[0].toInt().coerceIn(0,bmp.width-1);val t=p[1].toInt().coerceIn(0,bmp.height-1)
   val r=p[2].toInt().coerceIn(l+1,bmp.width);val b=p[3].toInt().coerceIn(t+1,bmp.height)
   return Rect(l,t,r,b)
  }
 }
}
