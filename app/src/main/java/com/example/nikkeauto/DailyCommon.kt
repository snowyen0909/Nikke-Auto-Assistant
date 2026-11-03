package com.example.nikkeauto

/**
 * Shared daily login and temporary popup handling.
 * OCR coordinates are screenshot coordinates, not assumed control positions.
 * Close icons are only tapped when an X is localized in the overlay header.
 */
object DailyCommon {
 data class Tap(val name:String,val x:Float,val y:Float)
 class Frame(lines:List<Triple<String,Float,Float>>,val w:Int,val h:Int) {
  private val items=lines.map { Triple(it.first.replace(" ","").uppercase(),it.second,it.third) }
  val text=items.joinToString(""){it.first}
  fun has(vararg words:String)=words.any{text.contains(it.replace(" ","").uppercase())}
  fun match(x0:Float,x1:Float,y0:Float,y1:Float,vararg words:String):Tap? {
   val keys=words.map{it.replace(" ","").uppercase()}
   val item=items.filter{it.second/w in x0..x1 && it.third/h in y0..y1}
    // Single-character keys (X, x-like glyphs) must match exactly; otherwise
    // words such as MAX / NEXT / EXP would be mistaken for a close icon.
    .filter{label->keys.any {if(it.length==1)label.first==it else label.first==it || label.first.contains(it)}}
    .sortedWith(compareBy<Triple<String,Float,Float>> {
     if(keys.contains(it.first))0 else 1
    }.thenBy{it.first.length}).firstOrNull() ?:return null
   return Tap(item.first,item.second,item.third)
  }
  fun home():Boolean =
   has("前哨基地","OUTPOST") && has("方舟","ARK","作戰出擊","作战出击") &&
   has("妮姬","NIKKE","好友","付費商店","付费商店")
  fun closeIcon():Tap? {
   // Icon-only X: template match first, then an exact single-glyph OCR element.
   DailyTemplates.closeTop()?.let{return Tap("X",it.x,it.y)}
   return match(.72f,.98f,.045f,.26f,"X","×","✕","關閉","关闭")
  }
  fun rewardPrompt():Tap?{
   if(!has("REWARD","獲得獎勵","获得奖励"))return null
   return match(.16f,.84f,.48f,.92f,
    "點擊領取獎勵","点击领取奖励","領取獎勵","领取奖励",
    "點擊進行下一步","点击进行下一步")
  }
 }
 private var lastPopupSignature=""
 private var repeated=0
 fun reset(){lastPopupSignature="";repeated=0}
 /** Return a global popup action, or null if the current task should proceed. */
 fun popup(frame:Frame):Tap? {
  val isEvent=frame.has("第三次地面收復戰","活動公告","活动公告",
   "系統公告","系统公告","回歸簽到","回归签到","回歸任務","回归任务",
   "簽到獎勵","签到奖励","版本更新公告","NEWCHAPTER","TRAILMARKER")
  val volt=frame.has("伏特的每日探索")
  if(volt) {
   // Claim first, then close on the next frame.
   val claim=frame.match(.18f,.90f,.60f,.95f,"領取獎勵","领取奖励")
   if(claim!=null)return Tap("伏特探索領獎",claim.x,claim.y)
   frame.closeIcon()?.let{return Tap("關閉伏特探索",it.x,it.y)}
  }
  if(!isEvent)return null
  return frame.closeIcon()?.let{Tap("關閉活動或簽到公告",it.x,it.y)}
 }
 fun enter(frame:Frame):Tap? {
  frame.match(.14f,.86f,.50f,.96f,"TOUCHTOCONTINUE","點擊進入遊戲","点击进入游戏")
   ?.let{return Tap("登入進入遊戲",it.x,it.y)}
  return popup(frame)
 }
}
