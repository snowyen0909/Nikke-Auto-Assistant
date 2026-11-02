package com.example.nikkeauto

import android.os.SystemClock
import kotlin.math.abs

/**
 * Prevents infinite camera ping-pong while prioritizing a blue navigation diamond.
 * State transitions happen only after a gesture is accepted for dispatch.
 */
class MapNavigationState {
 enum class Phase { SCANNING, CLOSING_MAP, CAMERA_MOVING, AWAITING_MONSTER, APPROACHING_MONSTER }
 data class Choice(val kind:String,val x:Float,val y:Float)
 var phase=Phase.SCANNING
  private set
 var isCameraMoving=false
  private set
 private var until=0L
 private var stableKind=""
 private var stableX=0f
 private var stableY=0f
 private var stable=0
 private var lastNavigation=0L
 private var lastClose=0L
 private var closeAttempts=0
 private var lastMonster=0L
 private var noTargetSince=0L
 private var lastNavX=-1f
 private var lastNavY=-1f
 private var retryCount=0
 private var previousNavX=-1f
 private var previousNavY=-1f
 private var monsterSearchUntil=0L

 fun reset(){
  phase=Phase.SCANNING;isCameraMoving=false;until=0L
  stableKind="";stable=0;noTargetSince=0L
  closeAttempts=0;retryCount=0;lastNavX=-1f;lastNavY=-1f
  previousNavX=-1f;previousNavY=-1f;monsterSearchUntil=0L
 }

 fun next(
  width:Int,height:Int,
  minimap:MapDetector.Target?,
  monster:MapDetector.Target?,
  marker:MapDetector.Target?,
  offscreen:MapDetector.Target?,
  now:Long=SystemClock.elapsedRealtime()
 ):Choice? {
  if(isCameraMoving){
   if(now<until)return null
   isCameraMoving=false;phase=Phase.AWAITING_MONSTER
   stable=0;stableKind=""
  }
  if(phase==Phase.CLOSING_MAP){
   if(now<until)return null
   phase=Phase.SCANNING;stable=0;stableKind=""
  }
  if(phase==Phase.APPROACHING_MONSTER){
   if(now<until)return null
   phase=Phase.SCANNING
  }
  // The expanded minimap must always be closed before map interaction.
  if(minimap!=null){
   noTargetSince=0L
   if(closeAttempts>=3){
    Settings.paused=true
    Settings.status="無法關閉小地圖，已暫停避免誤點；請手動關閉後繼續"
    return null
   }
   if(now-lastClose<2800L)return null
   return confirm("close-minimap",minimap,width,height,2)
  }
  closeAttempts=0
  // A cyan navigation diamond is never itself the monster tap target.
  // Treating it as a monster after a camera jump made the squad walk randomly.
  val immediate=monster
  // A jump must be followed by a sustained search for the monster, NOT another jump.
  if(now<monsterSearchUntil || phase==Phase.AWAITING_MONSTER){
   if(immediate!=null && now-lastMonster>2000L){
    noTargetSince=0L
    return confirm("map-monster",immediate,width,height,2)
   }
   if(now<monsterSearchUntil){
    stableKind="";stable=0
    return null
   }
   phase=Phase.SCANNING
  }
  // User preference: navigate to a blue diamond before ordinary red-ring targets.
  if(offscreen!=null && now-lastNavigation>=3500L){
   val sameAsLast=lastNavX>=0f &&
    abs(offscreen.x-lastNavX)<width*.13f &&
    abs(offscreen.y-lastNavY)<height*.12f
   val reverse=previousNavX>=0f &&
    abs(offscreen.x-previousNavX)<width*.13f &&
    abs(offscreen.y-previousNavY)<height*.12f
   // Repeated or alternating navigation directions are likely a loop.
   if((sameAsLast && retryCount>=1) || reverse){
    if(immediate!=null && now-lastMonster>2000L)
     return confirm("map-monster",immediate,width,height,2)
    noTargetSince=if(noTargetSince==0L)now else noTargetSince
    stableKind="";stable=0
    return null
   }
   noTargetSince=0L
   return confirm("map-nav",offscreen,width,height,2)
  }
  if(immediate!=null && now-lastMonster>2200L){
   noTargetSince=0L
   return confirm("map-monster",immediate,width,height,2)
  }
  if(noTargetSince==0L)noTargetSince=now
  stableKind="";stable=0
  return null
 }

 fun onTapAccepted(kind:String,x:Float,y:Float,now:Long=SystemClock.elapsedRealtime()){
  stableKind="";stable=0
  when(kind){
   "close-minimap"->{
    lastClose=now;closeAttempts++
    phase=Phase.CLOSING_MAP;until=now+1200L
   }
   "map-monster"->{
    lastMonster=now;phase=Phase.APPROACHING_MONSTER;until=now+2500L
    retryCount=0;previousNavX=-1f;previousNavY=-1f
    monsterSearchUntil=0L
   }
   "map-nav"->{
    val same=lastNavX>=0f && abs(x-lastNavX)<60f && abs(y-lastNavY)<85f
    if(same)retryCount++ else retryCount=1
    previousNavX=lastNavX;previousNavY=lastNavY
    lastNavX=x;lastNavY=y
    lastNavigation=now;phase=Phase.CAMERA_MOVING
    isCameraMoving=true;until=now+1300L
    monsterSearchUntil=now+8000L
   }
  }
 }

 fun canPatrol(now:Long=SystemClock.elapsedRealtime()):Boolean=
  !isCameraMoving && phase==Phase.SCANNING &&
   now>=monsterSearchUntil && noTargetSince>0L && now-noTargetSince>5500L

 private fun confirm(kind:String,target:MapDetector.Target,w:Int,h:Int,needed:Int):Choice?{
  val valid=stableKind==kind &&
   abs(target.x-stableX)<w*.04f && abs(target.y-stableY)<h*.045f
  if(valid)stable++ else{stableKind=kind;stable=1}
  stableX=target.x;stableY=target.y
  if(stable<needed)return null
  stable=0;stableKind=""
  return Choice(kind,target.x,target.y)
 }
}
