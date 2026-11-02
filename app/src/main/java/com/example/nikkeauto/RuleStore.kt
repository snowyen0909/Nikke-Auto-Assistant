package com.example.nikkeauto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** User-editable OCR / image conditions, actions and failure routes. */
object RuleStore {
 data class Rule(
  val name:String,
  val method:String="OCR",
  val target:String="",
  val region:String="FULL",
  val success:String="TAP",
  val failure:String="RETRY",
  val delayMs:Long=3000,
  val attempts:Int=3,
  val enabled:Boolean=true
 )
 private val cache=mutableMapOf<String,MutableList<Rule>>()
 @Synchronized fun rules(ctx:Context,id:String):MutableList<Rule>{
  cache[id]?.let{return it}
  val raw=ctx.getSharedPreferences("rule_editor",Context.MODE_PRIVATE)
   .getString(id,"[]")?:"[]"
  val result=mutableListOf<Rule>()
  runCatching {
   val arr=JSONArray(raw)
   for(i in 0 until arr.length()){
    val o=arr.getJSONObject(i)
    result.add(Rule(
     o.optString("name","步驟 ${i+1}"),o.optString("method","OCR"),
     o.optString("target",""),o.optString("region","FULL"),
     o.optString("success","TAP"),o.optString("failure","RETRY"),
     o.optLong("delayMs",3000),o.optInt("attempts",3),
     o.optBoolean("enabled",true)))
   }
  }
  cache[id]=result
  return result
 }
 @Synchronized fun save(ctx:Context,id:String,items:List<Rule>){
  cache[id]=items.toMutableList()
  val arr=JSONArray()
  items.forEach { r ->
   arr.put(JSONObject().apply{
    put("name",r.name);put("method",r.method);put("target",r.target)
    put("region",r.region);put("success",r.success);put("failure",r.failure)
    put("delayMs",r.delayMs.coerceIn(500,15000))
    put("attempts",r.attempts.coerceIn(0,10));put("enabled",r.enabled)
   })
  }
  ctx.getSharedPreferences("rule_editor",Context.MODE_PRIVATE)
   .edit().putString(id,arr.toString()).apply()
 }
 @Synchronized fun export(ctx:Context,id:String):String{
  val arr=JSONArray()
  rules(ctx,id).forEach {r ->
   arr.put(JSONObject().apply{
    put("name",r.name);put("method",r.method);put("target",r.target)
    put("region",r.region);put("success",r.success);put("failure",r.failure)
    put("delayMs",r.delayMs);put("attempts",r.attempts);put("enabled",r.enabled)
   })
  }
  return arr.toString(2)
 }
 @Synchronized fun import(ctx:Context,id:String,json:String):Boolean{
  return runCatching{
   val arr=JSONArray(json)
   require(arr.length()<=100)
   val list=mutableListOf<Rule>()
   for(i in 0 until arr.length()){
    val o=arr.getJSONObject(i)
    val method=o.optString("method","OCR")
    require(method in listOf("OCR","IMAGE","BOTH"))
    list.add(Rule(o.optString("name","步驟 ${i+1}").take(80),method,
     o.optString("target","").take(160),o.optString("region","FULL"),
     o.optString("success","TAP"),o.optString("failure","RETRY"),
     o.optLong("delayMs",3000),o.optInt("attempts",3),
     o.optBoolean("enabled",true)))
   }
   save(ctx,id,list)
  }.isSuccess
 }
}
