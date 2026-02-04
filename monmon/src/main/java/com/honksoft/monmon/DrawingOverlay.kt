package com.honksoft.monmon

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Nullable

class DrawingOverlay(context: Context?, attrs: AttributeSet?) :
  View(context, attrs) {
  private val paint: Paint = Paint()
  //private var detectedObjects: MutableList<FaceMesh>? = null

  init {
    paint.color = Color.RED
    paint.strokeWidth = 5.0f
    paint.style = Paint.Style.STROKE
  }

//  fun drawOverlay(newObjects: MutableList<FaceMesh>) {
//    detectedObjects = newObjects
//  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

//    if (detectedObjects != null) {
//      // Calculate the scaling factor for each dimension
//      // TODO: Address landscape orientation
//      val scaleX: Float = width.toFloat() / 480.0f;
//      val scaleY: Float = height.toFloat() / 640.0f;
//
//      for (faceMesh in detectedObjects!!) {
//        val bounds: Rect = faceMesh.boundingBox
//        // Gets all points
////                val faceMeshpoints = faceMesh.allPoints
////                for (faceMeshpoint in faceMeshpoints) {
////                    val index: Int = faceMeshpoint.index
////                    val position = faceMeshpoint.position
////                }
//
//        // Gets triangle info
//        val triangles: List<Triangle<FaceMeshPoint>> = faceMesh.allTriangles
//        for (triangle in triangles) {
//          // 3 Points connecting to each other and representing a triangle area.
//          val connectedPoints = triangle.allPoints
//          for (point in connectedPoints) {
//            // Subtract from width if camera is front-facing
//            canvas.drawPoint(width - (point.position.x * scaleX), point.position.y * scaleY, paint)
//          }
//        }
//      }
//    }
//    invalidate()
  }
}