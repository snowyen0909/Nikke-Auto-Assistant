package com.example.nikkeauto

import android.os.SystemClock
import kotlin.math.abs

/** Screenshot/OCR-guided routine based on the eight supplied walkthrough PDFs.
 * No payment or gem purchases. Never loops indefinitely on an uncertain screen.
 */
object DailyRoutine {
 data class Step(val id:String,val title:String,val detail:String)
 val steps=listOf(
  Step("login","登入與公告","TOUCH TO CONTINUE；關閉登入公告"),
  Step("mail","信箱獎勵","開信箱、全部領取、確認獎勵"),
  Step("idle","掛機獎勵","防禦前哨基地 → 獲得獎勵 → 免費殲滅"),
  Step("dispatch","派遣公告欄","前哨基地 → 全部領取 → 全部派遣"),
  Step("shop","商店每日免費商品","普通商店信用點，僅允許鑽石消耗為 0"),
  Step("gift","每日免費禮包","付費商店 → 普通禮包 → 每日 → 免費"),
  Step("friends","好友愛心","好友 → 贈送 → 確認"),
  Step("upgrade","每日升級","低等級角色升級一次（消耗遊戲資源）"),
  Step("arena","新人競技場","2 次免費對戰／確認快速戰鬥 ON")
 )
 @Volatile var active=false
 @Volatile var paused=false
 @Volatile var index=0
 @Volatile var status="尚未啟動"
 @Volatile var completed=0
 private val states=mutableMapOf<String,String>()
 private var stage=0
 private var lastTap=0L
 private var sameAction=""
 private var sameCount=0
 private var lastX=-1f
 private var lastY=-1f
 private var stepStart=0L
 // Per-step bookkeeping for modules without DailyScreenParts.
 private var doneFlag=false
 private val budgets=mutableMapOf<String,Int>()
 private val stableCounts=mutableMapOf<String,Int>()
 private fun resetStep(){doneFlag=false;budgets.clear();stableCounts.clear()}

 @Synchronized fun isEnabled(id:String):Boolean=when(id){
  "login"->Settings.dailyLogin
  "mail"->Settings.dailyMail
  "idle"->Settings.dailyIdle
  "dispatch"->Settings.dailyDispatch
  "shop"->Settings.dailyShop
  "gift"->Settings.dailyGift
  "friends"->Settings.dailyFriends
  "upgrade"->Settings.dailyUpgrade
  "arena"->Settings.dailyArena
  else->false
 }
 @Synchronized fun setEnabled(id:String,on:Boolean) {
  when(id){
   "login"->Settings.dailyLogin=on
   "mail"->Settings.dailyMail=on
   "idle"->Settings.dailyIdle=on
   "dispatch"->Settings.dailyDispatch=on
   "shop"->Settings.dailyShop=on
   "gift"->Settings.dailyGift=on
   "friends"->Settings.dailyFriends=on
   "upgrade"->Settings.dailyUpgrade=on
   "arena"->Settings.dailyArena=on
  }
 }
 @Synchronized fun state(id:String):String=states[id]?:"待執行"
 @Synchronized fun start(){
  active=true;paused=false;index=0;completed=0;stage=0
  states.clear();sameAction="";sameCount=0;lastTap=0L;resetStep()
  DailyScreenParts.reset()
  DailyCommon.reset()
  lastX=-1f;lastY=-1f;stepStart=SystemClock.elapsedRealtime()
  advanceDisabled()
  if(active)status="請切換至遊戲，執行：${steps[index].title}"
 }
 @Synchronized fun pause(){paused=true;status="日常流程已暫停"}
 @Synchronized fun resume(){if(active){paused=false;status="繼續：${steps[index].title}"}}
 @Synchronized fun stop(){active=false;paused=false;status="已停止"}
 @Synchronized fun skip(){
  if(!active)return
  states[steps[index].id]="人工確認／略過"
  advance()
 }
 @Synchronized private fun advanceDisabled(){
  while(index<steps.size && !isEnabled(steps[index].id)){
   states[steps[index].id]="未啟用";index++
  }
  if(index>=steps.size){active=false;status="日常流程已結束"}
 }
 @Synchronized private fun advance(){
  completed++;index++;stage=0;lastTap=0L;resetStep()
  sameCount=0;sameAction="";lastX=-1f;lastY=-1f
  stepStart=SystemClock.elapsedRealtime()
  advanceDisabled()
  if(active)status="下一項：${steps[index].title}；請返回對應首頁"
 }

 data class Action(val name:String,val x:Float,val y:Float)
 @Synchronized fun decide(lines:List<Triple<String,Float,Float>>,width:Int,height:Int):Action? {
  if(!active || paused || index>=steps.size)return null
  val now=SystemClock.elapsedRealtime()
  if(now-lastTap<1700L)return null
  val limit=if(steps[index].id=="arena" || steps[index].id=="upgrade")300000L else 150000L
  if(now-stepStart>limit){paused=true;status="步驟逾時，已暫停：${steps[index].title}";return null}
  val s=steps[index]
  states[s.id]="執行中 · 階段 ${stage+1}"
  val all=lines.joinToString(" "){it.first}.replace(" ","").uppercase()
  fun has(vararg v:String)=v.any{all.contains(it.uppercase())}
  fun find(vararg words:String):Triple<String,Float,Float>? {
   val query=words.map{it.replace(" ","").uppercase()}
   // Prefer exact localized OCR elements, then shortest matching label.
   // Long OCR lines commonly contain unrelated UI buttons.
   return lines.filter{line->
    val text=line.first.replace(" ","").uppercase()
    query.any{text.contains(it)}
   }.sortedWith(compareBy<Triple<String,Float,Float>> {
    if(query.contains(it.first.replace(" ","").uppercase()))0 else 1
   }.thenBy {it.first.length}).firstOrNull()
  }
  fun at(name:String,x:Float,y:Float):Action? {
   if(x<width*.03f || x>width*.97f || y<height*.035f || y>height*.985f)return null
   val moved=abs(x-lastX)>width*.035f || abs(y-lastY)>height*.04f
   sameCount=if(name==sameAction && !moved)sameCount+1 else 1
   sameAction=name;lastX=x;lastY=y
   if(sameCount>2){paused=true;status="防重複點擊暫停：${name}；請確認畫面";return null}
   lastTap=now
   status="${s.title}：${name}"
   return Action(name,x,y)
  }
  fun tap(name:String,x:Float,y:Float)=at(name,x*width,y*height)
  fun label(name:String,vararg words:String):Action? {
   val match=find(*words)?:return null
   return at(name,match.second,match.third)
  }
  // Prefer OCR button bounds when a readable label is available.
  // REWARD by itself is not a reason to tap an arbitrary position.
  fun reward():Action? {
   if(!has("REWARD"))return null
   val button=lines.firstOrNull{line->
    val text=line.first.replace(" ","")
    (text.contains("點擊領取獎勵") || text.contains("点击领取奖励") ||
     text.contains("點擊進行下一步") || text.contains("点击进行下一步")) &&
     line.second in (width*.20f)..(width*.80f) &&
     line.third in (height*.45f)..(height*.90f)
   }?:return null
   return at("確認 REWARD 獎勵",button.second,button.third)
  }
  // Login and transient event notices use the same OCR-anchored component.
  val shared=DailyCommon.Frame(lines,width,height)
  // Strict lobby test (outpost + ark + a lobby-only entry); the old loose test
  // also matched the paid-shop page itself.
  fun home()=shared.home()
  /** Stable for [n] consecutive frames; resets as soon as the condition fails. */
  fun stable(key:String,cond:Boolean,n:Int=2):Boolean {
   if(!cond){stableCounts[key]=0;return false}
   val c=(stableCounts[key]?:0)+1
   stableCounts[key]=c
   return c>=n
  }
  /** Run [act] at most [max] times per step; only successful taps use the budget. */
  fun limited(key:String,max:Int,act:()->Action?):Action? {
   if((budgets[key]?:0)>=max)return null
   val a=act()?:return null
   budgets[key]=(budgets[key]?:0)+1
   return a
  }
  /**
   * Way back to the lobby, text first: bottom-bar "大廳", a "返回" label, a
   * "關閉" label. Each text is tried at most twice (a popup may cover it);
   * then the icon templates (dialog X, home icon) take over.
   */
  fun goBack():Action? {
   fun textAt(name:String,xMax:Float,yMin:Float,vararg words:String):Action? {
    val hit=lines.firstOrNull{line->
     val t=line.first.replace(" ","")
     words.any{t==it || t.contains(it)} && line.second<=width*xMax && line.third>=height*yMin}
     ?:return null
    return limited("back_$name",2){at(name,hit.second,hit.third)}
   }
   textAt("返回大廳",1f,.85f,"大廳","大厅")?.let{return it}
   textAt("返回",.35f,.85f,"返回")?.let{return it}
   val close=lines.firstOrNull{line->
    val t=line.first.replace(" ","")
    (t=="關閉" || t=="关闭") && line.second>=width*.60f && line.third<=height*.30f}
   if(close!=null)limited("back_close",2){at("關閉頁面",close.second,close.third)}?.let{return it}
   DailyTemplates.closeTop()?.let{return at("關閉頁面",it.x,it.y)}
   DailyTemplates.homeBottom()?.let{return at("返回大廳",it.x,it.y)}
   return null
  }
  if(s.id=="login"){
   // Event popups that cover the lobby are closed by the shared handler below;
   // they must not block login completion (the lobby text is still readable).
   if(shared.home() && DailyCommon.popup(shared)==null){
    states[s.id]="已完成";advance();return null
   }
   val entering=DailyCommon.enter(shared)
   if(entering!=null)return at(entering.name,entering.x,entering.y)
   status="登入：等待登入文字、彈窗關閉圖示或主大廳"
   return null
  }
  // Event windows can appear while doing any daily task.
  DailyCommon.popup(shared)?.let {return at(it.name,it.x,it.y)}
  // Shared screen components from annotated mail, idle reward and arena PDFs.
  // They require page-specific OCR cues and region checks before any tap.
  if(s.id=="mail" || s.id=="arena" || s.id=="idle"){
   val frame=DailyScreenParts.Frame(lines,width,height)
   if(DailyScreenParts.completed(s.id,frame)){
    states[s.id]="已完成";advance();return null
   }
   val action=DailyScreenParts.decide(s.id,frame)
   if(action!=null)return at(action.name,action.x,action.y)
   status="${s.title}：等待已知畫面或人工確認"
   return null
  }
  // Remaining modules: finish once the work is done AND the lobby is visible again.
  if(doneFlag){
   if(home()){states[s.id]="已完成";advance();return null}
   goBack()?.let{return it}
   status="${s.title}：已完成，找不到返回大廳按鈕，請手動返回大廳"
   return null
  }
  when(s.id){
   "dispatch"->{
    if(has("REWARD"))return reward()
    val board=has("派遣公告欄","派遣公告栏")
    // After "全部派遣" a confirm dialog has a blue "派遣" button.
    if((budgets["d_send"]?:0)>0 && !board){
     val confirm=lines.firstOrNull{line->
      line.first.replace(" ","")=="派遣" &&
       line.second in (width*.25f)..(width*.80f) &&
       line.third in (height*.50f)..(height*.90f)}
     if(confirm!=null)return limited("d_confirm",1){at("確認派遣",confirm.second,confirm.third)}
    }
    if(board){
     // The buttons stay on screen (greyed) after use, so each is tried at most twice.
     if(has("全部領取","全部领取")){
      limited("d_claim",2){label("派遣全部領取","全部領取","全部领取")}?.let{return it}
     }
     if(has("全部派遣")){
      limited("d_send",1){label("全部派遣","全部派遣")}?.let{return it}
     }
     if(stable("d_done",true,3)){doneFlag=true}
     status="派遣：確認完成中"
     return null
    }
    if(has("前哨基地","OUTPOST") && !home())
     return label("開啟派遣公告欄","派遣公告欄","派遣公告栏")
    if(home())return label("進入前哨基地","前哨基地")
    status="派遣：等待前哨基地或派遣公告欄"
   }
   "shop"->{
    if(has("REWARD"))return reward()
    if(has("購買道具","购买道具") && has("信用點","信用点")){
     // PDFs show FREE/0 gems. Never accept 0 if other payment may be charged.
     val explicitZero=lines.any{line->
      val t=line.first.replace(" ","").trim()
      (t=="0" || t=="鑽石0" || t=="消耗資金0" || t=="消耗资金0") &&
       line.second in (width*.48f)..(width*.78f) &&
       line.third in (height*.67f)..(height*.79f)
     }
     if(!has("消耗資金","消耗资金") || !explicitZero || has("NT$","$33","$170","$490")){
      paused=true;status="商店：無法確定消耗為 0，已暫停";return null
     }
     val buy=label("確認 0 鑽石信用點購買","購買","购买")
     if(buy!=null)budgets["shop_bought"]=1
     return buy
    }
    if(has("普通商店","百貨商店") && has("信用點","信用点")){
     val freeItem=has("100%SALE","FREE")
     val bought=(budgets["shop_bought"]?:0)>0
     val soldOut=has("SOLDOUT","售罄")
     // Done when the purchase went through, or nothing free is left to buy.
     if(stable("shop_done",bought || soldOut || !freeItem,3)){doneFlag=true;return null}
     if(freeItem && !bought)
      return limited("shop_pick",2){label("選擇免費信用點","信用點","信用点")}
     status="商店：確認完成中"
     return null
    }
    if(home())return label("進入一般商店","商店")
    status="商店：未確認免費 0 鑽石商品，禁止購買"
   }
   "gift"->{
    if(has("REWARD"))return reward()
    val giftPage=has("普通禮包","普通礼包","每日免費禮包","每日免费礼包")
    if(giftPage){
     val claimed=(budgets["g_claim"]?:0)>0
     // The title "每日免費禮包" itself contains 免費, so SOLD OUT is checked first.
     if(has("SOLDOUT","售罄") || stable("g_done",claimed,2)){doneFlag=true;return null}
     if(has("每日免費禮包","每日免费礼包") && has("免費","免费"))
      limited("g_claim",2){label("領取每日免費禮包","免費","免费")}?.let{return it}
     if(has("普通禮包","普通礼包") && has("每日"))
      limited("g_tab",2){label("切換每日禮包","每日")}?.let{return it}
    }
    if(has("STEPUPPACKAGE","限時禮包","STEPUP禮包"))
      return limited("g_normal",2){tap("切換普通禮包",.18f,.20f)}
    if(home())return label("開啟付費商店","付費商店","付费商店")
    status="免費禮包：找不到明確免費標記，不點擊任何付費項目"
   }
   "friends"->{
    if(has("贈送結果","赠送结果")){
     val ok=limited("f_confirm",2){label("確認贈送結果","確認","确认")}
     if(ok!=null){budgets["f_done"]=1;return ok}
     return null
    }
    if(has("好友目錄","好友目录")){
     if(has("贈送","赠送"))
      limited("f_send",2){label("贈送好友點數","贈送","赠送")}?.let{return it}
     // Confirmed, or the gift button is already used up: close the list.
     if(stable("f_done",(budgets["f_done"]?:0)>0 || (budgets["f_send"]?:0)>=2,3)){doneFlag=true}
     return null
    }
    if(home())return label("開啟好友","好友")
    status="好友：等待好友目錄／贈送結果"
   }
   "upgrade"->{
    if(has("升級","升级") && has("升級材料","升级材料") && has("LV.2")){
     // One level only; no maxing out; optionally user can disable module.
     if(stage==0){stage=1;return tap("升級一級",.50f,.79f)}
     // The level reset that refunds materials is not implemented yet.
     status="升級：已升一級；等級重置尚未實作，請手動重置"
     doneFlag=true
     return null
    }
    if(has("詳細資訊","详细信息") && has("LV") && has("戰力","战力"))
      return label("開啟升級視窗","升級","升级")
    if(has("妮姬","NIKKE") && has("戰力","战力")){
     if(stage==0)return tap("選擇最低等級角色（PDF 第一格）",.11f,.33f)
     status="升級：等待升級畫面"
     return null
    }
    if(home())return label("進入妮姬", "妮姬")
    status="升級：畫面不確定，未消耗資源"
   }
  }
  return null
 }
}
