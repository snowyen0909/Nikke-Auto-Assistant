package com.example.nikkeauto

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Executes user-authored rules only when explicitly enabled in the module.
 * Unrecognized IMAGE/BOTH templates never trigger clicks.
 */
object RuleEngine {
 data class Decision(val name:String,val x:Float,val y:Float,val tap:Boolean)
 private var module=""
 private var step=0
 private var misses=0
 private var lastChecked=0L
 @Synchronized fun reset(){module="";step=0;misses=0;lastChecked=0L}
 @Synchronized fun active(ctx:Context,id:String):Boolean=
  RuleStore.rules(ctx,id).any{it.enabled}
 @Synchronized fun position():Int=step
 @Synchronized fun decide(ctx:Context,id:String,
  lines:List<Triple<String,Float,Float>>,w:Int,h:Int,
  bitmap:Bitmap):Decision?{
  if(module!=id){module=id;step=0;misses=0;lastChecked=0L}
  val rules=RuleStore.rules(ctx,id).filter{it.enabled}
  if(step>=rules.size){
   DailyRoutine.completeRuleModule()
   reset();return null
  }
  val rule=rules[step]
  val now=SystemClock.elapsedRealtime()
  if(now-lastChecked<rule.delayMs.coerceAtLeast(500L) && misses>0)return null
  lastChecked=now
  val matched=when(rule.method){
   "OCR"->findText(lines,w,h,rule)
   "IMAGE"->findImage(ctx,bitmap,rule)
   "BOTH"->{
    val loc=findText(lines,w,h,rule)
    val image=findImage(ctx,bitmap,rule)
    if(loc!=null && image!=null && kotlin.math.abs(loc.first-image.first)<w*.10f
     && kotlin.math.abs(loc.second-image.second)<h*.10f)loc else null
   }
   else->null
  }
  if(matched==null){
   misses++
   if(misses>rule.attempts){
    when(rule.failure){
     "SKIP"->{step++;misses=0}
     "BACK"->{step=(step-1).coerceAtLeast(0);misses=0}
     "PAUSE"->{DailyRoutine.pause()}
     else->{misses=0}
    }
   }
   DailyRoutine.status="規則 ${step+1}/${rules.size}：找不到 ${rule.target}"
   return null
  }
  misses=0
  return when(rule.success){
   "PAUSE"->{DailyRoutine.pause();null}
   "WAIT","NEXT"->{step++;null}
   "TAP"->Decision(rule.name,matched.first,matched.second,true)
   else->null
  }
 }
 @Synchronized fun onTapAccepted(){
  step++;misses=0;lastChecked=0L
 }
 private fun region(x:Float,y:Float,w:Int,h:Int,which:String):Boolean{
  val nx=x/w;val ny=y/h
  return when(which){
   "TOP"->ny<.38f
   "BOTTOM"->ny>.62f
   "LEFT"->nx<.45f
   "RIGHT"->nx>.55f
   "CENTER"->nx in 0.2f..0.8f && ny in 0.2f..0.8f
   else->true
  }
 }
 private fun findText(lines:List<Triple<String,Float,Float>>,w:Int,h:Int,r:RuleStore.Rule):Pair<Float,Float>?{
  val key=r.target.replace(" ","").uppercase()
  if(key.isBlank())return null
  val match=lines.filter{
   it.first.replace(" ","").uppercase().contains(key) &&
    region(it.second,it.third,w,h,r.region)
  }.minByOrNull{it.first.length}?:return null
  return match.second to match.third
 }
 private fun findImage(ctx:Context,b:Bitmap,r:RuleStore.Rule):Pair<Float,Float>?{
  // Only built-in verified templates currently supported.
  // Arbitrary user-provided IMAGE names must fail closed.
  val target=when(r.target.uppercase()){
   "BLUE_DIAMOND","藍色驚嘆號","藍色菱形" ->
    MapDetector.navigationDiamond(b,false,ctx.assets)
   "SKIP" -> SkipDetector.find(b)
   else->null
  }?:return null
  return if(region(target.x,target.y,b.width,b.height,r.region))
   target.x to target.y else null
 }
}
