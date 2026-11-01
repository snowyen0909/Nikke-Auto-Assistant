package com.example.nikkeauto
object Settings {
 @Volatile var skip=true
 @Volatile var battle=true
 @Volatile var map=true
 @Volatile var patrol=false
 @Volatile var running=false
 @Volatile var paused=false
 @Volatile var dailyMode=false
 @Volatile var dailyOutpost=true
 @Volatile var dailyDispatch=true
 @Volatile var dailyGift=true
 @Volatile var dailyFriends=true
 @Volatile var dailyRewards=true
 @Volatile var dailyStep="未啟動"
 @Volatile var dailyDone=0
 @Volatile var tap:TapService?=null
 @Volatile var status="未啟動"
 @Volatile var lastRecognized=""
 @Volatile var frames=0
 @Volatile var taps=0
}