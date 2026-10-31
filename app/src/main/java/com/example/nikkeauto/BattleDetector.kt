package com.example.nikkeauto

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Strict battle button color verification. Coordinates come from OCR text
 * bounding boxes, not from arbitrary blue/orange objects on the map.
 */
object BattleDetector {
 enum class ColorState { ORANGE, BLUE, GRAY, UNKNOWN }

 fun stateNear(b:Bitmap, x:Float, y:Float):ColorState {
  val w=b.width;val h=b.height
  if(x < w*.35f || x > w*.96f || y < h*.58f || y > h*.98f) return ColorState.UNKNOWN
  val radiusX=(w*.055f).toInt().coerceAtLeast(12)
  val radiusY=(h*.012f).toInt().coerceAtLeast(8)
  var orange=0;var blue=0;var gray=0;var total=0
  for(dy in -radiusY..radiusY step 5) for(dx in -radiusX..radiusX step 6) {
   val xx=x.toInt()+dx;val yy=y.toInt()+dy
   if(xx !in 0 until w || yy !in 0 until h) continue
   val c=b.getPixel(xx,yy)
   val r=Color.red(c);val g=Color.green(c);val bb=Color.blue(c)
   if(r>195 && g in 65..210 && bb<145 && r>g+40) orange++
   if(bb>125 && bb>r*1.25f && g>r*1.20f && g>75)blue++
   if(kotlin.math.abs(r-g)<28 && kotlin.math.abs(g-bb)<28 && r in 55..185)gray++
   total++
  }
  if(total<10)return ColorState.UNKNOWN
  if(orange.toFloat()/total>.18f)return ColorState.ORANGE
  if(blue.toFloat()/total>.18f)return ColorState.BLUE
  if(gray.toFloat()/total>.30f)return ColorState.GRAY
  return ColorState.UNKNOWN
 }
}
