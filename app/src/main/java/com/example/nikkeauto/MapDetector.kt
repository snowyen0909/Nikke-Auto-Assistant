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
 fun navigationDiamond(b:Bitmap):Target? {
  val w=b.width;val h=b.height;val s=max(3,min(w,h)/190)
  var best:Target?=null
  for(y in (h*.15f).toInt() until (h*.87f).toInt() step s)
   for(x in (w*.14f).toInt() until (w*.92f).toInt() step s){
    if(x<w*.24f && y<h*.32f)continue
    var cyan=0;var white=0;var samples=0
    val r=max(10,min(w,h)/55)
    for(dy in -r..r step max(3,r/4))for(dx in -r..r step max(3,r/4)){
     val xx=x+dx;val yy=y+dy
     if(xx !in 0 until w || yy !in 0 until h)continue
     val color=b.getPixel(xx,yy)
     val red=Color.red(color);val green=Color.green(color);val blue=Color.blue(color)
     if(blue>155 && green>120 && blue>red*1.30f && green>red*1.20f)cyan++
     if(red>205 && green>205 && blue>205)white++
     samples++
    }
    if(samples==0 || cyan<6 || white<3)continue
    // Prefer a sufficiently strong local cyan ring with a white interior.
    val score=cyan*2+white
    if(best==null || score>best.score)best=Target(x.toFloat(),y.toFloat(),score)
   }
  return best?.takeIf{it.score>=18}
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
  // User reference: X at roughly (0.36 w, 0.08 h), in the upper-right
  // corner of the large blue minimap. Verify the map panel, never tap blind.
  val x=(w*.363f).toInt();val y=(h*.079f).toInt()
  val r=max(4,(min(w,h)*.013f).toInt())
  if(x-r<0 || x+r>=w || y-r<0 || y+r>=h)return null
  var cross=0
  for(d in -r..r step max(1,r/4)){
   val p=b.getPixel(x+d,y+d);val q=b.getPixel(x+d,y-d)
   if(Color.red(p)>185 && Color.green(p)>185 && Color.blue(p)>185)cross++
   if(Color.red(q)>185 && Color.green(q)>185 && Color.blue(q)>185)cross++
  }
  if(cross<7)return null
  // The blue filled geographic map occupies x=.04-.40 and y=.07-.23.
  var blue=0;var sampled=0
  for(yy in (h*.115f).toInt()..(h*.215f).toInt() step max(3,h/90))
   for(xx in (w*.07f).toInt()..(w*.32f).toInt() step max(3,w/80)){
    val p=b.getPixel(xx,yy)
    if(Color.blue(p)>Color.red(p)*1.18f &&
       Color.blue(p)>Color.green(p)*1.06f && Color.blue(p)>80)blue++
    sampled++
   }
  return if(sampled>0 && blue>=12 && blue.toFloat()/sampled>.08f)
   Target(x.toFloat(),y.toFloat(),cross+blue) else null
 }
 /** Unactivated ground pad in reference image 2: dark octagonal center and
  * concentric metallic rings. Activated image 1 is distinctly bright.
  * This is intentionally conservative to avoid tapping scenery.
  */
 fun inactiveGroundPad(b:Bitmap):Target? {
  val w=b.width;val h=b.height
  val scale=min(w,h)
  var best:Target?=null
  // Exclude navigation HUD and expanded minimap.
  for(y in (h*.28f).toInt() until (h*.82f).toInt() step max(10,scale/32))
   for(x in (w*.17f).toInt() until (w*.85f).toInt() step max(10,scale/32)){
    for(radius in intArrayOf(scale/17,scale/13,scale/10)){
     if(x-radius<0 || x+radius>=w || y-radius<0 || y+radius>=h)continue
     var centerBrightness=0f;var outerBrightness=0f
     var centerN=0;var outerN=0;var metallic=0
     for(k in 0 until 24){
      val theta=k*Math.PI/12.0
      val ix=x+(radius*.35*cos(theta)).toInt()
      val iy=y+(radius*.35*.72*sin(theta)).toInt()
      val ox=x+(radius*.92*cos(theta)).toInt()
      val oy=y+(radius*.92*.72*sin(theta)).toInt()
      val pc=b.getPixel(ix,iy);val po=b.getPixel(ox,oy)
      val cr=Color.red(pc);val cg=Color.green(pc);val cb=Color.blue(pc)
      val or=Color.red(po);val og=Color.green(po);val ob=Color.blue(po)
      centerBrightness+=(cr+cg+cb)/3f;centerN++
      outerBrightness+=(or+og+ob)/3f;outerN++
      if(kotlin.math.abs(or-og)<29 && kotlin.math.abs(og-ob)<29)metallic++
     }
     centerBrightness/=centerN;outerBrightness/=outerN
     // Dark center of the unactivated pad; activated pad has pale center.
     if(centerBrightness !in 28f..115f || outerBrightness !in 50f..165f)continue
     if(metallic<16 || kotlin.math.abs(outerBrightness-centerBrightness)<13f)continue
     // Require approximately symmetric outer ring, not a random dark patch.
     var symmetry=0
     for(k in 0 until 12){
      val theta=k*Math.PI/6.0
      val xx=x+(radius*.75*cos(theta)).toInt()
      val yy=y+(radius*.75*.72*sin(theta)).toInt()
      val px=b.getPixel(xx,yy)
      val v=(Color.red(px)+Color.green(px)+Color.blue(px))/3f
      if(kotlin.math.abs(v-outerBrightness)<50f)symmetry++
     }
     if(symmetry<9)continue
     val score=metallic+symmetry
     if(best==null || score>best.score)best=Target(x.toFloat(),y.toFloat(),score)
    }
   }
  return best
 }

}
