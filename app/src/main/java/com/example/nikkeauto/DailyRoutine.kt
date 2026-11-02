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
  Step("arena","新人競技場","Rookie Arena 第三位對手／快速戰鬥")
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
  states.clear();sameAction="";sameCount=0;lastTap=0L
  DailyScreenParts.reset()
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
  completed++;index++;stage=0;lastTap=0L
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
  if(now-stepStart>120000L){paused=true;status="步驟逾時，已暫停：${steps[index].title}";return null}
  val s=steps[index]
  states[s.id]="執行中 · 階段 ${stage+1}"
  val all=lines.joinToString(" "){it.first}.replace(" ","").uppercase()
  fun has(vararg v:String)=v.any{all.contains(it.uppercase())}
  fun find(vararg words:String)=lines.firstOrNull{line->
   val t=line.first.replace(" ","").uppercase()
   words.any{t.contains(it.uppercase())}
  }
  fun at(name:String,x:Float,y:Float):Action? {
   if(x<width*.03f || x>width*.97f || y<height*.035f || y>height*.97f)return null
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
  fun home()=has("付費商店","前哨基地","作戰出擊","隊員招募") &&
   has("方舟","商店","妮姬","好友")
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
  // The annotated PDFs demonstrate each screen; unexpected views are not tapped.
  when(s.id){
   "login"->{
    if(has("TOUCHTOCONTINUE"))return label("進入遊戲","TOUCH TO CONTINUE","TOUCHTOCONTINUE")
    if(has("公告") && has("活動公告","系統公告"))return tap("關閉公告",.88f,.105f)
    if(has("第三次地面收復戰","TRAILMARKER","NEWCHAPTER"))return tap("關閉活動彈窗",.85f,.19f)
    reward()?.let{return it}
    if(home()){states[s.id]="已完成";advance()}
    else status="登入：等待遊戲首頁或已知彈窗"
   }
   "mail"->{
    if(has("REWARD"))return reward()
    if(has("郵箱","郵件") && has("全部領取","全部领取")){
     return label("信箱全部領取","全部領取","全部领取")
    }
    if(has("郵箱","郵件") && has("已領取","已领取")){
     states[s.id]="已完成";advance();return null
    }
    if(home())return tap("開啟郵箱",.79f,.035f)
    status="信箱：未確認郵箱畫面，請手動返回主畫面"
   }
   "dispatch"->{
    if(has("REWARD"))return reward()
    if(has("派遣公告欄","派遣公告栏") && has("全部領取","全部领取"))
     return label("派遣全部領取","全部領取","全部领取")
    if(has("派遣公告欄","派遣公告栏") && has("全部派遣","全部派遣"))
     return label("全部派遣","全部派遣")
    if(has("派遣公告欄","派遣公告栏")){
     status="派遣：已無可辨識操作，請確認後切換下一項";return null
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
     return label("確認 0 鑽石信用點購買","購買","购买")
    }
    if(has("普通商店","百貨商店") && has("信用點","信用点") && has("100%SALE","FREE"))
      return label("選擇免費信用點","信用點","信用点")
    if(home())return label("進入一般商店","商店")
    status="商店：未確認免費 0 鑽石商品，禁止購買"
   }
   "gift"->{
    if(has("REWARD"))return reward()
    if(has("每日免費禮包","每日免费礼包") && has("免費","免费"))
      return label("領取每日免費禮包","免費","免费")
    if(has("普通禮包","普通礼包") && has("每日"))
      return label("切換每日禮包","每日")
    if(has("STEPUPPACKAGE","限時禮包","STEPUP禮包"))
      return tap("切換普通禮包",.18f,.20f)
    if(home())return label("開啟付費商店","付費商店","付费商店")
    status="免費禮包：找不到明確免費標記，不點擊任何付費項目"
   }
   "friends"->{
    if(has("贈送結果","赠送结果"))return label("確認贈送結果","確認","确认")
    if(has("好友目錄","好友目录") && has("贈送","赠送"))
      return label("贈送好友點數","贈送","赠送")
    if(home())return label("開啟好友","好友")
    status="好友：等待好友目錄／贈送結果"
   }
   "upgrade"->{
    if(has("升級","升级") && has("升級材料","升级材料") && has("LV.2")){
     // One level only; no maxing out; optionally user can disable module.
     if(stage==0){stage=1;return tap("升級一級",.50f,.79f)}
     status="升級：已嘗試一次，請確認再切換下一項"
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
   "arena"->{
    if(has("DEFEAT","VICTORY","REWARD") && has("ROOKIE","ARENA")){
     return label("競技場結果下一步","點擊進行下一步","点击进行下一步")
       ?: tap("結算下一步",.50f,.85f)
    }
    if(has("新人競技場","ROOKIEARENA") && has("進入戰鬥","进入战斗")){
     // Only enter if quick-battle is confirmed on; manual verification else.
     if(has("快速戰鬥","快速战斗","ON"))return label("開始新人競技場","進入戰鬥","进入战斗")
     paused=true;status="競技場：快速戰鬥未確認開啟，已暫停";return null
    }
    if(has("ROOKIEARENA","新人競技場") && has("進入戰鬥","进入战斗"))
      return label("選擇第三位對手","進入戰鬥","进入战斗")
    if(has("ARENA") && has("ROOKIE"))return label("進入新人競技場","ROOKIE")
    if(has("競技場","ARENA"))return label("進入競技場","競技場","ARENA")
    if(has("方舟"))return label("進入方舟","方舟")
    if(home())return label("進入方舟","方舟")
    status="競技場：等待 Rookie Arena 畫面，無法確認對手時不點擊"
   }
  }
  return null
 }
}
