package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/** Lightweight pixel-based detector for red rings under outdoor monsters.
 * Coordinates are in the original MediaProjection bitmap space.
 * Requires an outdoor-map UI confirmation from the caller.
 */
object MapDetector {
 data class Target(val x:Float, val y:Float, val score:Int)
 fun redRingMonster(bitmap:Bitmap):Target? {
  val w=bitmap.width;val h=bitmap.height
  // Scan in a small grid and cluster red/orange pixels around elliptical ground rings.
  // Exclude HUD, minimap, and bottom navigation panel.
  val step=max(2,min(w,h)/250)
  val cw=(w+step-1)/step;val ch=(h+step-1)/step
  val mask=BooleanArray(cw*ch)
  val minY=(h*.13f).toInt()/step
  val maxY=(h*.88f).toInt()/step
  for(gy in minY until min(maxY,ch)) for(gx in 0 until cw) {
   val x=min(w-1,gx*step);val y=min(h-1,gy*step)
   if(x<w*.21f && y<h*.28f) continue
   if(x>w*.77f && y<h*.13f) continue
   val c=bitmap.getPixel(x,y)
   val r=Color.red(c);val g=Color.green(c);val b=Color.blue(c)
   // Orange-red encounter circle: high red, some green, weak blue.
   mask[gy*cw+gx]=(r>145 && r>g*1.25f && r>b*1.45f && g>35 && g<165)
  }
  val seen=BooleanArray(mask.size)
  val queue=IntArray(mask.size)
  var best:Target?=null
  for(start in mask.indices){
   if(!mask[start] || seen[start])continue
   var head=0;var tail=1;queue[0]=start;seen[start]=true
   var minX=cw;var maxX=0;var minRow=ch;var maxRow=0;var count=0
   while(head<tail){
    val idx=queue[head++];val x=idx%cw;val y=idx/cw;count++
    minX=min(minX,x);maxX=max(maxX,x);minRow=min(minRow,y);maxRow=max(maxRow,y)
    for(dy in -1..1)for(dx in -1..1){
     val nx=x+dx;val ny=y+dy
     if(nx in 0 until cw && ny in 0 until ch){
      val ni=ny*cw+nx
      if(mask[ni] && !seen[ni]){seen[ni]=true;queue[tail++]=ni}
     }
    }
   }
   val ringW=(maxX-minX+1)*step;val ringH=(maxRow-minRow+1)*step
   if(ringW !in (w*.035f).toInt()..(w*.26f).toInt())continue
   if(ringH !in (h*.008f).toInt()..(h*.095f).toInt())continue
   if(count<9)continue
   val ratio=ringW.toFloat()/ringH
   if(ratio<1.0f || ratio>6.5f)continue
   // Click ABOVE ground circle center, on the enemy body.
   val cx=(minX+maxX)*step/2f
   val ringCenterY=(minRow+maxRow)*step/2f
   val clickY=(ringCenterY-ringH*.9f).coerceIn(h*.12f,h*.86f)
   val score=count
   if(best==null || score>best.score)best=Target(cx,clickY,score)
  }
  return best
 }
 /** The selected enemy in the screenshot has a BLUE hexagonal tile
  * in the middle of a RED encounter ring. The old red-component detector
  * often misses it because the ring is thin / partially occluded.
  * Look for a compact blue center with red samples at radial offsets.
  * Tap ABOVE the circle, on the monster model.
  */
 fun blueCoreRedRing(b:Bitmap):Target? {
  val w=b.width;val h=b.height
  val step=max(4,min(w,h)/210)
  var best:Target?=null
  val radius=(min(w,h)*.060f).toInt()
  for(y in (h*.29f).toInt() until (h*.84f).toInt() step step*2)
   for(x in (w*.08f).toInt() until (w*.89f).toInt() step step*2) {
    // Do not scan the minimap or navigation HUD.
    if(y<h*.33f && x<w*.24f)continue
    val center=b.getPixel(x,y)
    val cr=Color.red(center);val cg=Color.green(center);val cb=Color.blue(center)
    if(!(cb>105 && cb>cr*1.22f && cb>cg*.95f))continue
    var blueCount=0
    for(dy in -8..8 step 4)for(dx in -8..8 step 4){
     val xx=x+dx;val yy=y+dy
     if(xx !in 0 until w || yy !in 0 until h)continue
     val c=b.getPixel(xx,yy)
     if(Color.blue(c)>105 && Color.blue(c)>Color.red(c)*1.20f)blueCount++
    }
    if(blueCount<5)continue
    var bestRing=0
    for(rf in intArrayOf(35,48,60,75,90,105)){
     val r=max(9,radius*rf/100)
     var red=0
     for(k in 0 until 24){
      val angle=k*Math.PI/12.0
      val xx=x+(r*cos(angle)).toInt()
      val yy=y+(r*.55f*sin(angle)).toInt()
      if(xx !in 0 until w || yy !in 0 until h)continue
      val pixel=b.getPixel(xx,yy)
      val rr=Color.red(pixel);val gg=Color.green(pixel);val bb=Color.blue(pixel)
      if(rr>125 && rr>gg*1.28f && rr>bb*1.20f)red++
     }
     bestRing=max(bestRing,red)
    }
    if(bestRing<7)continue
    val score=bestRing*3+blueCount
    val result=Target(x.toFloat(),(y-radius*.72f).coerceAtLeast(h*.17f),score)
    if(best==null || score>best.score)best=result
   }
  return best
 }
 /** Candidate blue/cyan exclamation markers inside the map, excluding HUD/minimap.
  * Verify a bright white center surrounded by cyan-blue pixels.
  */
 /** Cyan-bordered diamond exclamation; return center, not an arbitrary cyan pixel.
  * Shared for overhead enemy markers and offscreen camera-navigation markers.
  */
 fun navigationDiamond(b:Bitmap,mapExpanded:Boolean=false):Target? {
  // Prefer actual OpenCV contour/shape detection; keep legacy matcher as backup.
  OpenCvVision.blueDiamond(b,mapExpanded)?.let{return it}
  val w=b.width;val h=b.height
  val step=max(2,min(w,h)/260)
  val gw=(w+step-1)/step;val gh=(h+step-1)/step
  val mask=BooleanArray(gw*gh)
  fun cyan(px:Int,py:Int):Boolean {
   if(px !in 0 until w || py !in 0 until h)return false
   val p=b.getPixel(px,py)
   val r=Color.red(p);val g=Color.green(p);val bl=Color.blue(p)
   return bl>148 && g>105 && bl>r*1.25f && g>r*1.12f
  }
  for(gy in (h*.13f).toInt()/step until min(gh,(h*.925f).toInt()/step))
   for(gx in (w*.035f).toInt()/step until min(gw,(w*.965f).toInt()/step)){
    val x=min(w-1,gx*step);val y=min(h-1,gy*step)
    if(mapExpanded && x<w*.44f && y<h*.28f)continue
    if(y<h*.17f && x>w*.48f)continue
    mask[gy*gw+gx]=cyan(x,y)
   }
  val seen=BooleanArray(mask.size)
  val queue=IntArray(mask.size)
  var best:Target?=null
  for(i in mask.indices){
   if(!mask[i] || seen[i])continue
   var head=0;var tail=1;queue[0]=i;seen[i]=true
   var minX=gw;var maxX=0;var minY=gh;var maxY=0
   while(head<tail){
    val idx=queue[head++];val x=idx%gw;val y=idx/gw
    minX=min(minX,x);maxX=max(maxX,x);minY=min(minY,y);maxY=max(maxY,y)
    for(dy in -1..1)for(dx in -1..1){
     val xx=x+dx;val yy=y+dy
     if(xx in 0 until gw && yy in 0 until gh){
      val j=yy*gw+xx
      if(mask[j] && !seen[j]){seen[j]=true;queue[tail++]=j}
     }
    }
   }
   val bw=(maxX-minX+1)*step;val bh=(maxY-minY+1)*step
   // Refuse blue buildings/roads and thin arrow-only strokes.
   if(bw !in (w*.055f).toInt()..(w*.16f).toInt())continue
   if(bh !in (w*.055f).toInt()..(w*.19f).toInt())continue
   if(bw.toFloat()/bh !in .66f..1.35f || tail<12)continue
   val x0=minX*step;val x1=min(w-1,(maxX+1)*step)
   val y0=minY*step;val y1=min(h-1,(maxY+1)*step)
   // Locate the WHITE inner diamond, not the cyan cluster centroid:
   // a downward pointer attached to the frame otherwise shifts tap downward.
   var whiteX=0L;var whiteY=0L;var white=0
   var dark=0
   val mx=(x0+x1)/2;val my=(y0+y1)/2
   for(y in (my-bh/4).coerceAtLeast(0)..(my+bh/5).coerceAtMost(h-1) step max(1,step))
    for(x in (mx-bw/4).coerceAtLeast(0)..(mx+bw/4).coerceAtMost(w-1) step max(1,step)){
     val p=b.getPixel(x,y)
     val r=Color.red(p);val g=Color.green(p);val bl=Color.blue(p)
     if(r>192 && g>192 && bl>192){whiteX+=x;whiteY+=y;white++}
     if(r<110 && g<120 && bl<135)dark++
    }
   if(white<4 || dark<2)continue
   val cx=(whiteX/white).toInt();val cy=(whiteY/white).toInt()
   if(cy<h*.14f || cy>h*.91f)continue
   // Require cyan surrounding white on both axes at a comparable distance.
   val reach=(min(bw,bh)*.42f).toInt()
   var directions=0
   for((dx,dy) in arrayOf(0 to -1,0 to 1,-1 to 0,1 to 0)){
    var hit=false
    for(rad in (reach/2).coerceAtLeast(3)..reach step max(1,step)){
     if(cyan(cx+dx*rad,cy+dy*rad)){hit=true;break}
    }
    if(hit)directions++
   }
   if(directions<3)continue
   val score=white*2+tail+directions*10
   if(best==null || score>best.score)best=Target(cx.toFloat(),cy.toFloat(),score)
  }
  return best
 }
 /** Marker above a directly visible enemy: a red combat ring just below it. */
 fun overheadEnemyMarker(b:Bitmap,marker:Target?):Target? {
  if(marker==null)return null
  val w=b.width;val h=b.height
  val mx=marker.x.toInt();val my=marker.y.toInt()
  // In the user's 565 px wide screenshot, the diamond is at y≈550,
  // enemy at y≈625, and the blue-core/red-ring is at y≈675.
  // Search only directly below the marker to avoid unrelated red terrain.
  val minDy=(w*.13f).toInt()
  val maxDy=(w*.34f).toInt()
  val step=max(3,w/125)
  var best:Target?=null
  for(cy in my+minDy..min(h-1,my+maxDy) step step*2)
   for(cx in max(0,mx-(w*.095f).toInt())..min(w-1,mx+(w*.095f).toInt()) step step*2){
    val center=b.getPixel(cx,cy)
    val cr=Color.red(center);val cg=Color.green(center);val cb=Color.blue(center)
    // The middle of the target's ground disc is visibly BLUE.
    if(cb<100 || cb<cr*1.12f || cb<cg*.88f)continue
    var blue=0
    for(dy in -step*2..step*2 step step)
     for(dx in -step*2..step*2 step step){
      val xx=cx+dx;val yy=cy+dy
      if(xx !in 0 until w || yy !in 0 until h)continue
      val p=b.getPixel(xx,yy)
      if(Color.blue(p)>100 && Color.blue(p)>Color.red(p)*1.10f)blue++
     }
    if(blue<4)continue
    var maxRed=0
    // Multiple elliptical sizes support different device resolutions.
    for(rx in intArrayOf((w*.065f).toInt(),(w*.09f).toInt(),(w*.115f).toInt())){
     val ry=max(8,(rx*.53f).toInt())
     var red=0
     for(k in 0 until 32){
      val angle=k*Math.PI/16
      val px=cx+(rx*cos(angle)).toInt()
      val py=cy+(ry*sin(angle)).toInt()
      if(px !in 0 until w || py !in 0 until h)continue
      val p=b.getPixel(px,py)
      val r=Color.red(p);val g=Color.green(p);val bl=Color.blue(p)
      if(r>119 && r>g*1.17f && r>bl*1.12f)red++
     }
     maxRed=max(maxRed,red)
    }
    if(maxRed<6)continue
    val score=maxRed*5+blue-(abs(cx-mx)/step)
    // Aim at enemy body, not the ground ring or the diamond itself.
    val clickY=(cy-w*.11f).coerceIn(h*.15f,h*.86f)
    if(best==null || score>best.score)best=Target(cx.toFloat(),clickY,score)
   }
  return best
 }

 /** Boss portal: broad saturated red/orange glowing ground zone, not a tiny enemy ring. */
 fun bossPortal(b:Bitmap):Target? {
  val w=b.width;val h=b.height
  val s=max(4,min(w,h)/130)
  var best:Target?=null
  for(y in (h*.19f).toInt() until (h*.79f).toInt() step s*3)
   for(x in (w*.2f).toInt() until (w*.93f).toInt() step s*3){
    var reds=0;var total=0
    val radius=max(20,min(w,h)/13)
    for(dy in -radius..radius step s*2)for(dx in -radius..radius step s*2){
     val xx=x+dx;val yy=y+dy
     if(xx !in 0 until w || yy !in 0 until h)continue
     val c=b.getPixel(xx,yy)
     val r=Color.red(c);val g=Color.green(c);val blue=Color.blue(c)
     if(r>175 && r>g*1.5f && r>blue*1.6f)reds++
     total++
    }
    if(total<10)continue
    val fraction=reds.toFloat()/total
    if(fraction>.43f && (best==null || reds>best.score))
     best=Target(x.toFloat(),y.toFloat(),reds)
   }
  return best
 }
 /** Approximate enemy direction from pink markers in the top-left minimap.
  * Returns -1 (left), 0 (unknown), +1 (right). Never treated as tap coordinates.
  */
 fun minimapEnemyDirection(b:Bitmap):Int {
  val w=b.width;val h=b.height
  var sx=0L;var count=0
  for(y in (h*.065f).toInt() until (h*.24f).toInt() step 3)
   for(x in (w*.025f).toInt() until (w*.20f).toInt() step 3){
    val c=b.getPixel(x,y)
    val r=Color.red(c);val g=Color.green(c);val blue=Color.blue(c)
    if(r>125 && blue>95 && r>g*1.35f && blue>g*1.25f) {sx+=x;count++}
   }
  if(count<5)return 0
  return if(sx/count < w*.105f)-1 else 1
 }

 /** Expanded minimap covers upper-left field of view, including targets.
  * Check both its blue panel and the white X in the upper-right corner.
  * Layout varies with screen size, hence center is found from candidate scan.
  */
 fun expandedMinimap(b:Bitmap):Target? {
  val w=b.width;val h=b.height
  if(w<240 || h<400)return null
  // UI can start at y=.06 on one phone but y=.11 on another.
  // In both supplied screenshots the X is close to x=.365 of the display.
  val xFrom=(w*.326f).toInt()
  val xTo=(w*.402f).toInt()
  val yFrom=(h*.053f).toInt()
  val yTo=(h*.176f).toInt()
  val r=max(4,(w*.014f).toInt())
  val step=max(2,r/3)
  var best:Target?=null
  for(y in yFrom..yTo step step)for(x in xFrom..xTo step step){
   if(x-2*r<0 || x+2*r>=w || y-r<0 || y+2*r>=h)continue
   // The X has two bright diagonals, not a single blue patch.
   var diagonalA=0;var diagonalB=0
   var axisBright=0
   for(d in -r..r step max(1,r/4)){
    fun bright(xx:Int,yy:Int):Boolean {
     val p=b.getPixel(xx,yy)
     return Color.red(p)>167 && Color.green(p)>167 &&
      Color.blue(p)>167 &&
      max(Color.red(p),max(Color.green(p),Color.blue(p)))-
       min(Color.red(p),min(Color.green(p),Color.blue(p)))<75
    }
    if(bright(x+d,y+d))diagonalA++
    if(bright(x+d,y-d))diagonalB++
    if(bright(x+d,y))axisBright++
   }
   if(diagonalA<5 || diagonalB<5)continue
   // Avoid bright horizontal labels or the minimap blue diamond.
   if(axisBright>=diagonalA+diagonalB-3)continue

   // Expanded panel has a dark blue/gray interior directly below and left
   // of its X. Validate a substantial patch, not its blue-map fill amount.
   var panel=0;var samples=0;var blue=0
   val py0=y+(h*.026f).toInt()
   val py1=min(h-1,y+(h*.105f).toInt())
   val px0=max(1,x-(w*.27f).toInt())
   val px1=x-(w*.045f).toInt()
   for(yy in py0..py1 step max(3,h/185))
    for(xx in px0..px1 step max(3,w/105)){
     val p=b.getPixel(xx,yy)
     val red=Color.red(p);val green=Color.green(p);val bl=Color.blue(p)
     if(red<115 && green<157 && bl<190)panel++
     if(bl>94 && bl>red*1.20f && bl>green*1.06f)blue++
     samples++
    }
   if(samples==0 || panel.toFloat()/samples<.48f || blue<6)continue
   val score=(diagonalA+diagonalB)*12+blue/2-
    (kotlin.math.abs(x-w*.365f)/3f).toInt()
   if(best==null || score>best.score)best=Target(x.toFloat(),y.toFloat(),score)
  }
  return best
 }

}
