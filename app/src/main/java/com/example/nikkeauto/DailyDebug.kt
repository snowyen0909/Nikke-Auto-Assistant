package com.example.nikkeauto

import android.content.Context
import android.os.SystemClock

/**
 * Per-module diagnostic policy. Similar to an image-recognition rule editor:
 * retry, retry delay, post-click wait, and what to do after exhausting retries.
 * No blind custom taps or auto-repeat of payment/upgrade/arena actions.
 */
object DailyDebug {
 enum class Failure { PAUSE, SKIP }
 data class Config(
  val postTapMs:Long=3000L,
  val retryDelayMs:Long=5000L,
  val retries:Int=4,
  val failure:Failure=Failure.PAUSE
 )
 private val default=Config()
 private val configs=mutableMapOf<String,Config>()
 private var module=""
 private var lastNoMatch=0L
 private var misses=0
 @Synchronized fun get(id:String):Config=configs[id]?:default
 @Synchronized fun update(ctx:Context,id:String,c:Config) {
  val safe=c.copy(
   postTapMs=c.postTapMs.coerceIn(1000L,12000L),
   retryDelayMs=c.retryDelayMs.coerceIn(1500L,12000L),
   retries=c.retries.coerceIn(0,5)
  )
  configs[id]=safe
  ctx.getSharedPreferences("daily_debug",Context.MODE_PRIVATE).edit()
   .putLong("${id}_post",safe.postTapMs).putLong("${id}_delay",safe.retryDelayMs)
   .putInt("${id}_retries",safe.retries)
   .putString("${id}_failure",safe.failure.name).apply()
 }
 @Synchronized fun load(ctx:Context){
  val pref=ctx.getSharedPreferences("daily_debug",Context.MODE_PRIVATE)
  for(s in DailyRoutine.steps){
   val id=s.id
   configs[id]=Config(pref.getLong("${id}_post",3000L),
    pref.getLong("${id}_delay",5000L),
    pref.getInt("${id}_retries",4),
    runCatching{Failure.valueOf(pref.getString("${id}_failure","PAUSE")?:"PAUSE")}
     .getOrDefault(Failure.PAUSE))
  }
 }
 @Synchronized fun reset(){module="";misses=0;lastNoMatch=0L}
 @Synchronized fun nextDelay(id:String):Long=get(id).postTapMs
 /** Call only on a complete OCR frame that did not produce an action. */
 @Synchronized fun onNoMatch(id:String):Failure? {
  val now=SystemClock.elapsedRealtime()
  if(module!=id){module=id;misses=0;lastNoMatch=now;return null}
  val cfg=get(id)
  if(now-lastNoMatch<cfg.retryDelayMs)return null
  lastNoMatch=now;misses++
  if(misses>cfg.retries)return cfg.failure
  return null
 }
 @Synchronized fun onMatch(id:String){
  module=id;misses=0;lastNoMatch=0L
 }
}
