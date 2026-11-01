package com.example.nikkeauto

import android.os.SystemClock
import kotlin.math.abs

/**
 * Exclusive map navigation state machine. A target click must be followed
 * by fresh frames; camera transitions cannot be tapped again immediately.
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
 private var lastMonster=0L
 private var noTargetSince=0L
 private var attempts=0

 fun reset(){
  phase=Phase.SCANNING;isCameraMoving=false;until=0L
  stableKind="";stable=0;noTargetSince=0L;attempts=0
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
   isCameraMoving=false
   phase=Phase.AWAITING_MONSTER
   stable=0;stableKind=""
  }
  if(phase==Phase.CLOSING_MAP){
   if(now<until)return null
   phase=Phase.SCANNING
   stable=0;stableKind=""
  }
  if(phase==Phase.APPROACHING_MONSTER && now<until)return null
  if(phase==Phase.APPROACHING_MONSTER)phase=Phase.SCANNING

  // Overlay always wins, even if something underneath resembles a monster.
  if(minimap!=null){
   noTargetSince=0L
   if(now-lastClose<2300L)return null
   return confirm("close-minimap",minimap,width,height,1)?.also {
    lastClose=now;phase=Phase.CLOSING_MAP;until=now+1100L
   }
  }
  // After a navigation jump, engage the newly revealed monster first.
  // This prevents tapping a still-visible offscreen marker repeatedly.
  val immediate=monster ?: marker
  if(phase==Phase.AWAITING_MONSTER && immediate!=null){
   noTargetSince=0L
   if(now-lastMonster<2200L)return null
   return confirm("map-monster",immediate,width,height,2)?.also {
    lastMonster=now;phase=Phase.APPROACHING_MONSTER;until=now+1800L
   }
  }
  // Priority 1: blue diamond / offscreen direction marker.
  // If an enemy also appears, navigate to the marked destination first.
  if(offscreen!=null){
   noTargetSince=0L
   if(now-lastNavigation<2300L)return null
   return confirm("map-nav",offscreen,width,height,2)?.also {
    lastNavigation=now
    phase=Phase.CAMERA_MOVING
    isCameraMoving=true
    until=now+1050L
   }
  }
  // Priority 2: a visible red-ring monster, or diamond above its body.
  if(immediate!=null){
   noTargetSince=0L
   if(now-lastMonster<2200L)return null
   return confirm("map-monster",immediate,width,height,2)?.also {
    lastMonster=now;phase=Phase.APPROACHING_MONSTER;until=now+1800L
   }
  }
  // Wait for the new camera frame after transition; then patrol if needed.
  if(noTargetSince==0L)noTargetSince=now
  phase=Phase.SCANNING
  stableKind="";stable=0
  return null
 }

 fun canPatrol(now:Long=SystemClock.elapsedRealtime()):Boolean=
  !isCameraMoving && phase==Phase.SCANNING &&
   noTargetSince>0L && now-noTargetSince>5500L

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
