package com.example.nikkeauto

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Thread-safe recent diagnostic event buffer, with private on-device history. */
object EventLog {
 private val items=ArrayDeque<String>()
 private var lastEvent=""
 private var lastAt=0L
 @Synchronized fun add(context:Context, tag:String, message:String) {
  val key="$tag:$message"
  val now=System.currentTimeMillis()
  if(key==lastEvent && now-lastAt<3000)return
  lastEvent=key;lastAt=now
  val line="${SimpleDateFormat("HH:mm:ss",Locale.TAIWAN).format(Date(now))} [$tag] $message"
  items.addLast(line)
  while(items.size>150)items.removeFirst()
  try {
   context.openFileOutput("automation.log",Context.MODE_APPEND).bufferedWriter().use {
    it.appendLine(line)
   }
   val file=context.getFileStreamPath("automation.log")
   if(file.length()>800_000L) {
    val tail=items.joinToString("\n",postfix="\n")
    context.openFileOutput("automation.log",Context.MODE_PRIVATE).bufferedWriter().use{it.write(tail)}
   }
  }catch(_:Exception){}
 }
 @Synchronized fun recent():String=items.joinToString("\n")
 @Synchronized fun clear(context:Context){
  items.clear();lastEvent="";lastAt=0
  context.openFileOutput("automation.log",Context.MODE_PRIVATE).close()
 }
}
