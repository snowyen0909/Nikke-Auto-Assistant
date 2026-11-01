package com.example.nikkeauto
object Settings {
 @Volatile var skip=true
 @Volatile var battle=true
 @Volatile var map=true
 @Volatile var patrol=false
 @Volatile var running=false
 @Volatile var paused=false
 @Volatile var tap:TapService?=null
 @Volatile var status="未啟動"
 @Volatile var lastRecognized=""
 @Volatile var frames=0
 @Volatile var taps=0
}