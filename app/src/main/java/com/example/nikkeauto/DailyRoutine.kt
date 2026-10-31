package com.example.nikkeauto

import android.os.SystemClock

/** A conservative OCR-driven daily routine. Never purchases currency items. */
object DailyRoutine {
 data class Step(val id:String,val title:String,val detail:String)
 val steps=listOf(
  Step("outpost","前哨基地","領取累積資源"),
  Step("dispatch","派遣公告欄","領取派遣、重新派遣"),
  Step("gift","每日免費禮包","只允許明確標示免費"),
  Step("friends","好友愛心","贈送社交點數"),
  Step("rewards","每日任務","領取可用獎勵")
 )
 @Volatile var active=false
 @Volatile var paused=false
 @Volatile var index=0
 @Volatile var status="尚未開始"
 @Volatile var completed=0
 private var lastActionAt=0L
 private var stepStartedAt=0L
 private var repeatedAction=""
 private var repeatedCount=0
 private val states=mutableMapOf<String,String>()
 @Synchronized fun state(id:String):String=states[id]?:"待執行"
 @Synchronized fun isEnabled(id:String):Boolean=when(id){
  "outpost"->Settings.dailyOutpost
  "dispatch"->Settings.dailyDispatch
  "gift"->Settings.dailyGift
  "friends"->Settings.dailyFriends
  "rewards"->Settings.dailyRewards
  else->false
 }
 @Synchronized fun start(){
  active=true;paused=false;index=0;completed=0
  states.clear();lastActionAt=0;stepStartedAt=SystemClock.elapsedRealtime()
  repeatedAction="";repeatedCount=0
  advanceDisabled()
  if(active)status="請切換至遊戲主畫面，準備執行 ${steps[index].title}"
 }
 @Synchronized fun pause(){paused=true;status="每日流程已暫停"}
 @Synchronized fun resume(){if(active){paused=false;status="恢復：${steps[index].title}"}}
 @Synchronized fun stop(){active=false;paused=false;status="已停止"}
 @Synchronized fun skip(){
  if(!active)return
  states[steps[index].id]="略過"
  advance()
 }
 @Synchronized private fun advanceDisabled(){
  while(index<steps.size && !isEnabled(steps[index].id)){
   states[steps[index].id]="未啟用"
   index++
  }
  if(index>=steps.size){active=false;status="已完成所有勾選的模組"}
 }
 @Synchronized private fun advance(){
  completed++
  index++
  stepStartedAt=SystemClock.elapsedRealtime()
  repeatedAction="";repeatedCount=0
  advanceDisabled()
  if(active)status="下一項：${steps[index].title}"
 }
 data class Action(val name:String,val x:Float,val y:Float)
 /** Called on OCR completion. Coordinates must already be in screenshot space. */
 @Synchronized fun decide(lines:List<Triple<String,Float,Float>>,width:Int,height:Int):Action? {
  if(!active || paused || index>=steps.size)return null
  val now=SystemClock.elapsedRealtime()
  if(now-lastActionAt<2200L)return null
  val step=steps[index]
  states[step.id]="執行中"
  val all=lines.joinToString(" "){it.first}.replace(" ","").uppercase()
  fun find(vararg words:String):Triple<String,Float,Float>? =
   lines.firstOrNull{line->words.any{line.first.contains(it,true)}}
  fun action(name:String,line:Triple<String,Float,Float>?):Action? {
   if(line==null)return null
   val x=line.second;val y=line.third
   if(x<width*.04f || x>width*.97f || y<height*.05f || y>height*.96f)return null
   lastActionAt=now
   if(repeatedAction==name)repeatedCount++ else {repeatedAction=name;repeatedCount=1}
   if(repeatedCount>3){paused=true;status="已暫停：重複點擊 ${name}，請人工確認";return null}
   status="${step.title}：${name}"
   return Action(name,x,y)
  }
  // A common result confirmation; only if claim/completion content is visible.
  val confirm=find("確認","确定","OK")
  val claim=find("領取","领取","收取","CLAIM","全部領取","全部领取")
  when(step.id){
   "outpost"->{
    if(all.contains("前哨") || all.contains("OUTPOST") || all.contains("防禦") || all.contains("防御")){
     if(claim!=null)return action("領取基地資源",claim)
     if(all.contains("領取完成") || all.contains("领取完成")){states[step.id]="完成";advance();return null}
     status="前哨基地：找不到可領取項目，請確認後點「下一項」"
    }else return action("開啟前哨基地",find("前哨基地","OUTPOST"))
   }
   "dispatch"->{
    if(all.contains("派遣") || all.contains("DISPATCH") || all.contains("公告欄")){
     if(claim!=null)return action("領取派遣",claim)
     val send=find("全部派遣","一鍵派遣","派遣全部","AUTO DISPATCH")
     if(send!=null)return action("重新派遣",send)
     status="派遣：無可辨識操作，請確認後點「下一項」"
    }else return action("開啟派遣",find("派遣","公告欄","DISPATCH"))
   }
   "gift"->{
    val free=find("免費","免费","FREE")
    if(free!=null && !all.contains("NT$") && !all.contains("USD"))return action("免費禮包",free)
    status="商店：未確認免費商品，為避免花費資源已停止點擊"
   }
   "friends"->{
    val send=find("全部贈送","全部赠送","贈送","赠送","SEND")
    if(send!=null)return action("贈送好友愛心",send)
    status="好友：無可辨識贈送操作，請確認後點「下一項」"
   }
   "rewards"->{
    if(all.contains("DAILY") || all.contains("每日") || all.contains("MISSION")){
     if(claim!=null)return action("領取每日獎勵",claim)
     status="每日任務：目前沒有可辨識獎勵，請確認後點「下一項」"
    }else return action("開啟每日任務",find("每日任務","每日任务","DAILY MISSION"))
   }
  }
  return null
 }
}
