package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import java.util.zip.Inflater
import kotlin.math.*

/** Transparent user-supplied ▶▶ SKIP icon, normalized to a 96x25 binary mask.
 * Scan only the top-right story toolbar, preventing AUTO being chosen.
 */
object SkipDetector {
 data class Match(val x:Float,val y:Float,val score:Float)
 private const val WIDTH=96
 private const val HEIGHT=25
 private const val MASK="eNrt1MESgCAIBNDd///pDk2FwRI4HpopL8Zkj1QU+NvLG492RrBPphsGtvn9uyGR8DnHWw73hIt9t2DUCc6QwhfLHW2DzWyIoS/4xij5tzlHvo3Z93n1SXX6uOwT8e7T1z/F/8v9ZfIWvv4ffeR+tkZu1ALfFQGR+kBjfSJHnK/8iEJOLnLaflKfymfdhxqi7je0/Px+kD5L/tz9j0W+nNkn2ga5DgLW"
 private val pixels:ByteArray by lazy {
  val compressed=Base64.decode(MASK,Base64.DEFAULT)
  val inflater=Inflater()
  inflater.setInput(compressed)
  val raw=ByteArray(WIDTH*HEIGHT)
  try {val n=inflater.inflate(raw);if(n!=raw.size)ByteArray(WIDTH*HEIGHT) else raw}
  finally{inflater.end()}
 }
 fun find(b:Bitmap):Match? {
  val w=b.width;val h=b.height
  if(w<200 || h<200)return null
  val template=pixels
  var best:Match?=null
  val xMin=(w*.62f).toInt()
  val xMax=(w*.99f).toInt()
  val yMin=0
  val yMax=(h*.11f).toInt()
  val reference=w/680f
  for(factor in floatArrayOf(.65f,.8f,1f,1.2f,1.45f,1.7f)) {
   val scale=factor*reference
   val tw=max(45,(WIDTH*scale).toInt())
   val th=max(12,(HEIGHT*scale).toInt())
   if(tw>=xMax-xMin || th>=yMax)continue
   val step=max(3,(tw*.045f).toInt())
   for(y in yMin..(yMax-th) step step)for(x in xMin..(xMax-tw) step step) {
    var pos=0;var neg=0;var posTotal=0;var negTotal=0
    for(ty in 2 until HEIGHT-2 step 2)for(tx in 2 until WIDTH-2 step 2) {
     val xx=x+tx*tw/WIDTH
     val yy=y+ty*th/HEIGHT
     val c=b.getPixel(xx,yy)
     val luma=(Color.red(c)*3+Color.green(c)*6+Color.blue(c))/10
     val white=luma>185
     if(template[ty*WIDTH+tx].toInt()!=0) {posTotal++;if(white)pos++}
     else {negTotal++;if(!white)neg++}
    }
    if(posTotal==0 || negTotal==0)continue
    val score=pos.toFloat()/posTotal*.77f+neg.toFloat()/negTotal*.23f
    if(score>.84f && (best==null || score>best.score))best=Match(x+tw*.5f,y+th*.5f,score)
   }
  }
  return best
 }
}
