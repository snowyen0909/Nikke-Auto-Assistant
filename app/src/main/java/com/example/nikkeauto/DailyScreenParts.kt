package com.example.nikkeauto

/**
 * Shared screen primitives for daily tasks, derived from the user's
 * mailbox / Rookie Arena / Outpost Defense walkthrough screenshots.
 *
 * OCR labels alone are NOT sufficient for unsafe actions. Check screen
 * context and coordinate regions before accepting a tap candidate.
 */
object DailyScreenParts {
 data class Label(val text:String,val x:Float,val y:Float)
 data class Tap(val name:String,val x:Float,val y:Float)
 class Frame(lines:List<Triple<String,Float,Float>>,val w:Int,val h:Int){
  val labels=lines.map{Label(it.first.replace(" ","").uppercase(),it.second,it.third)}
  val all=labels.joinToString(""){it.text}
  fun has(vararg words:String)=words.any{all.contains(it.replace(" ","").uppercase())}
  fun label(xMin:Float,xMax:Float,yMin:Float,yMax:Float,vararg words:String):Label?=
   labels.firstOrNull{it.x/w in xMin..xMax && it.y/h in yMin..yMax &&
    words.any{word->it.text.contains(word.replace(" ","").uppercase())}}
  fun tap(name:String,x:Float,y:Float)=Tap(name,x*w,y*h)
  fun tapLabel(name:String,xMin:Float,xMax:Float,yMin:Float,yMax:Float,vararg words:String):Tap? =
   label(xMin,xMax,yMin,yMax,*words)?.let{Tap(name,it.x,it.y)}
  fun home():Boolean=has("前哨基地","OUTPOST") && has("方舟","ARK","作戰出擊","作战出击")
  fun reward():Boolean=has("REWARD") && has("點擊領取獎勵","点击领取奖励","點擊進行下一步","点击进行下一步")
  fun rewardTap():Tap? {
   if(!reward())return null
   return tapLabel("確認獎勵",.25f,.75f,.48f,.84f,
    "點擊領取獎勵","点击领取奖励","點擊進行下一步","点击进行下一步")

  }
  fun topRightClose():Tap? {
   if(!has("郵箱","邮箱","防禦前哨基地","防御前哨基地",
     "好友目錄","好友目录","派遣公告欄","派遣公告栏"))return null
   return tapLabel("關閉彈窗",.72f,.98f,.04f,.24f,"X","×","✕","關閉","关闭")
  }
 }

 /**
  * Use the same state/labels for each task, and require an observed change
  * before moving to the next action. Never perform a paid wipe.
  */
 private var current=""
 private var phase=0
 private var arenaBattles=0
 private var freeWipeAttempted=false
 private var rewardSeen=false
 private var arenaResultHandled=false
 fun reset(){current="";phase=0;arenaBattles=0;freeWipeAttempted=false;rewardSeen=false;arenaResultHandled=false}
 private fun enter(id:String){if(current!=id){current=id;phase=0;rewardSeen=false}}
 fun decide(id:String,frame:Frame):Tap? {
  enter(id)
  return when(id){
   "mail"->mail(frame)
   "arena"->arena(frame)
   "idle"->idle(frame)
   else->null
  }
 }
 fun completed(id:String,frame:Frame):Boolean=when(id){
  "mail"->phase>=3 && frame.home()
  "arena"->phase>=3 && frame.home()
  "idle"->phase>=5 && frame.home()
  else->false
 }
 private fun mail(f:Frame):Tap? {
  if(f.reward()) {phase=2;rewardSeen=true;return f.rewardTap()}
  if(f.has("郵箱","邮箱","郵件","邮件")){
   // Mail bottom-right bulk claim; never click the trash can.
   val claim=f.tapLabel("信箱全部領取",.53f,.95f,.77f,.94f,"全部領取","全部领取")
   if(claim!=null && !f.has("已領取","已领取") && phase<2){phase=1;return claim}
   if(phase>=1 || f.has("已領取","已领取")){
    phase=3;return f.topRightClose()
   }
   return null
  }
  if(f.home()){
   if(phase>=2){phase=3;return null}
   return f.tap("開啟信箱",.87f,.035f)
  }
  return null
 }
 private fun arena(f:Frame):Tap? {
  if(f.has("DEFEAT","VICTORY") && f.has("ROOKIEARENA","ROOKIE","ARENA")){
   if(!arenaResultHandled){arenaBattles++;arenaResultHandled=true}
   phase=2
   return f.tapLabel("競技場結算下一步",.30f,.70f,.72f,.91f,
    "點擊進行下一步","点击进行下一步")
  }
  if(phase==2 && f.has("ROOKIEARENA","新人競技場") &&
     !f.has("DEFEAT","VICTORY")){
   arenaResultHandled=false
   if(arenaBattles>=2){
    phase=3
    // Prefer an explicitly recognized Home control; otherwise stop.
    return f.tapLabel("返回主大廳",.02f,.53f,.88f,.99f,"大廳","主頁","主页")
   }
   phase=0
   // Refresh the opponent directory before battle #2.
   return f.tapLabel("刷新競技場對手",.63f,.98f,.05f,.24f,
    "更新目錄","更新目录")
  }
  if(f.has("新人競技場","ROOKIEARENA") && f.has("快速戰鬥","快速战斗")
     && f.has("進入戰鬥","进入战斗")){
   // The PDF requires ON before combat. Avoid false positive 'ON' from
   // other OCR text by using image switch + geometry in future.
   // Never accept a generic 'ON' substring anywhere on the screen.
   // Switch state without a localized OCR label needs image confirmation.
   val switchOn=f.label(.10f,.45f,.78f,.94f,"ON")
   if(switchOn==null || f.label(.10f,.45f,.78f,.94f,"OFF")!=null)return null
   phase=1
   return f.tapLabel("開始快速戰鬥",.46f,.94f,.73f,.94f,
    "進入戰鬥","进入战斗")
  }
  if(f.has("ROOKIEARENA","新人競技場") && f.has("進入戰鬥","进入战斗")){
   // Prefer the third opponent free-entry button, restricted to lower row.
   return f.tapLabel("選擇第三位對手",.64f,.95f,.68f,.93f,
    "進入戰鬥","进入战斗")
  }
  if(f.has("ARENA") && f.has("ROOKIE")){
   return f.tapLabel("進入新人競技場",.04f,.52f,.31f,.66f,"ROOKIE")
  }
  if(f.has("競技場","竞技场") && !f.has("ROOKIE")){
   return f.tapLabel("進入競技場",.45f,.95f,.34f,.77f,"競技場","竞技场")
  }
  if(f.has("方舟") && !f.home())return f.tapLabel("進入方舟",.36f,.80f,.25f,.85f,"方舟")
  if(f.home()){
   if(phase>=2){phase=3;return null}
   return f.tapLabel("開啟方舟",.47f,.93f,.52f,.83f,"方舟")
  }
  return null
 }
 private fun idle(f:Frame):Tap? {
  // Prioritize reward overlay across both claim and free wipe.
  if(f.reward()){phase=if(freeWipeAttempted)4 else 2;rewardSeen=true;return f.rewardTap()}
  if(f.has("今日剩餘次數","今日剩余次数") &&
     f.has("50") && f.has("進行殲滅","进行歼灭")){
   // Subsequent wipe requires gems; explicitly cancel, never buy.
   phase=4
   return f.tapLabel("取消付費殲滅",.05f,.43f,.74f,.89f,"取消")
  }
  if(f.has("進行殲滅","进行歼灭") && f.has("取消") &&
      !freeWipeAttempted){
   // Only use the FREE daily wipe. If a gem price appears, the guard above
   // handles it first; unknown prices require manual review.
   // Positive confirmation of first-free status is mandatory.
   if(!f.has("每日首次免費","每日首次免费","首次免費","首次免费") ||
      f.has("50","鑽石","钻石"))return null
   freeWipeAttempted=true;phase=3
   return f.tapLabel("執行免費殲滅",.42f,.96f,.75f,.91f,
    "進行殲滅","进行歼灭")
  }
  if(f.has("防禦前哨基地","防御前哨基地")){
   if(phase>=4){phase=5;return f.topRightClose()}
   if(phase==2 || phase==3){
    return f.tapLabel("開啟一舉殲滅",.05f,.49f,.78f,.95f,"一舉殲滅","一举歼灭")
   }
   val claim=f.tapLabel("領取掛機獎勵",.42f,.96f,.78f,.95f,
    "獲得獎勵","获得奖励")
   if(claim!=null){phase=1;return claim}
   return null
  }
  if(f.home()){
   if(phase>=4){phase=5;return null}
   return f.tapLabel("開啟前哨基地防禦",.02f,.46f,.66f,.94f,
    "前哨基地","OUTPOST","DEFENSE")
  }
  return null
 }
}
