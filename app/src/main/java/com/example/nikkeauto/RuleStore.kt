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
  val imageName:String="",
  val region:String="FULL",
  val success:String="TAP",
  val failure:String="RETRY",
  val delayMs:Long=3000,
  val attempts:Int=3,
  val enabled:Boolean=true
 )
 private val cache=mutableMapOf<String,MutableList<Rule>>()
 private fun predefined(id:String):MutableList<Rule>{
  // Editable representation of the steps described in the user's daily PDFs.
  // OCR for readable controls; non-text icons remain disabled until an image is set.
  fun o(name:String,text:String,region:String="FULL")=
   Rule(name,"OCR",text,"",region,"TAP","PAUSE",3000,3,true)
  fun image(name:String)=Rule(name,"IMAGE","","","FULL","TAP","PAUSE",3000,3,false)
  return when(id){
   "mail"-> mutableListOf(image("首頁：點擊信箱圖示"),
    o("信箱：全部領取","全部領取","BOTTOM"),
    o("確認獎勵","點擊領取獎勵","BOTTOM"),
    image("關閉信箱 X"),image("確認返回首頁"))
   "idle"-> mutableListOf(o("前哨基地防禦入口","前哨基地","BOTTOM"),
    o("獲得獎勵","獲得獎勵","BOTTOM"),o("領取 REWARD","點擊領取獎勵","BOTTOM"),
    o("一舉殲滅","一舉殲滅","BOTTOM"),
    o("確認每日首次免費","每日首次免費"),
    o("進行殲滅","進行殲滅","BOTTOM"),
    o("確認免費獎勵","點擊領取獎勵","BOTTOM"),image("返回大廳"))
   "dispatch"-> mutableListOf(o("進入前哨基地","前哨基地"),
    o("打開派遣公告欄","派遣公告欄"),
    o("全部領取","全部領取","BOTTOM"),
    o("獎勵確認","點擊領取獎勵","BOTTOM"),
    o("全部派遣","全部派遣","BOTTOM"),
    o("確認派遣","派遣","BOTTOM"),image("關閉公告欄並返回首頁"))
   "shop"-> mutableListOf(o("進入商店","商店"),
    o("普通商店","普通商店"),o("選擇免費商品","FREE"),
    Rule("購買前檢查價格：必須 0","OCR","消耗資金 0","","BOTTOM","PAUSE","PAUSE",3000,0,false),
    o("免費商品已售罄確認","SOLD OUT"),image("返回大廳"))
   "gift"-> mutableListOf(o("進入付費商店","付費商店"),
    o("普通禮包","普通禮包"),o("每日分頁","每日"),
    o("每日免費禮包","每日免費禮包"),o("領取獎勵","領取獎勵","BOTTOM"),
    o("確認已領取","SOLD OUT"),image("返回大廳"))
   "friends"-> mutableListOf(o("進入好友","好友"),
    o("贈送好友點數","贈送","BOTTOM"),o("確認贈送結果","確認","BOTTOM"),
    image("關閉好友並回首頁"))
   "upgrade"-> mutableListOf(o("進入妮姬","妮姬"),
    image("選擇 Lv.1 角色"),o("升級一次","升級","BOTTOM"),
    Rule("等級重置需人工確認","OCR","等級重置","","BOTTOM","PAUSE","PAUSE",3000,0,false),
    image("返回大廳"))
   "arena"-> mutableListOf(o("進入方舟","方舟"),o("競技場","競技場"),
    o("新人競技場","新人競技場"),
    o("第三位免費對手","免費 進入戰鬥","BOTTOM"),
    Rule("快速戰鬥 ON 檢查","OCR","ON","","BOTTOM","PAUSE","PAUSE",3000,0,false),
    o("第一場進入戰鬥","進入戰鬥","BOTTOM"),
    o("第一場結算","點擊進行下一步","BOTTOM"),
    o("更新目錄","更新目錄","TOP"),
    o("第二場進入戰鬥","進入戰鬥","BOTTOM"),
    o("第二場結算","點擊進行下一步","BOTTOM"),image("返回大廳"))
   else-> mutableListOf()
  }
 }
 @Synchronized fun customEnabled(ctx:Context,id:String):Boolean=
  ctx.getSharedPreferences("rule_editor",Context.MODE_PRIVATE).getBoolean("mode_$id",false)
 @Synchronized fun setCustomEnabled(ctx:Context,id:String,on:Boolean){
  ctx.getSharedPreferences("rule_editor",Context.MODE_PRIVATE).edit()
   .putBoolean("mode_$id",on).apply()
 }

 @Synchronized fun rules(ctx:Context,id:String):MutableList<Rule>{
  cache[id]?.let{return it}
  val raw=ctx.getSharedPreferences("rule_editor",Context.MODE_PRIVATE)
   .getString(id,null)
  if(raw==null){
   val initial=predefined(id)
   save(ctx,id,initial)
   return initial
  }
  val result=mutableListOf<Rule>()
  runCatching {
   val arr=JSONArray(raw)
   for(i in 0 until arr.length()){
    val o=arr.getJSONObject(i)
    result.add(Rule(
     o.optString("name","步驟 ${i+1}"),o.optString("method","OCR"),
     o.optString("target",""),o.optString("imageName",""),o.optString("region","FULL"),
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
    put("imageName",r.imageName)
    put("imageName",r.imageName)
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
     o.optString("target","").take(160),o.optString("imageName","").take(80),o.optString("region","FULL"),
     o.optString("success","TAP"),o.optString("failure","RETRY"),
     o.optLong("delayMs",3000),o.optInt("attempts",3),
     o.optBoolean("enabled",true)))
   }
   save(ctx,id,list)
  }.isSuccess
 }
}
