package com.honksoft.monmon

import android.R.attr.onClick
import android.annotation.SuppressLint
import android.gesture.Gesture
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.honksoft.monmon.FullscreenActivity.Companion.AUTO_HIDE
import com.honksoft.monmon.FullscreenActivity.Companion.AUTO_HIDE_DELAY_MILLIS
import com.honksoft.monmon.databinding.ActivityFullscreenBinding
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import com.serenegiant.widget.AspectRatioSurfaceView
import org.opencv.android.OpenCVLoader
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
class FullscreenActivity : AppCompatActivity() {
  private val hideHandler = Handler(Looper.myLooper()!!)
  private var cameraHelper: ICameraHelper? = null
  private lateinit var binding: ActivityFullscreenBinding
  private lateinit var fullscreenContent: TextView
  private lateinit var fullscreenContentControls: LinearLayout
  private lateinit var cameraViewMain: AspectRatioSurfaceView
  private lateinit var cameraOverlay: ImageView
  private lateinit var imageTools: ImageTools
  private lateinit var scaleGestureDetector: ScaleGestureDetector
  private lateinit var panGestureDetector: GestureDetector

  @SuppressLint("InlinedApi")
  private val hidePart2Runnable = Runnable {
    // Delayed removal of status and navigation bar
    if (Build.VERSION.SDK_INT >= 30) {
      fullscreenContent.windowInsetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    } else {
      // Note that some of these constants are new as of API 16 (Jelly Bean)
      // and API 19 (KitKat). It is safe to use them, as they are inlined
      // at compile-time and do nothing on earlier devices.
      fullscreenContent.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LOW_PROFILE or
          View.SYSTEM_UI_FLAG_FULLSCREEN or
          View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
          View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
          View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
  }
  private val showPart2Runnable = Runnable {
    // Delayed display of UI elements
    supportActionBar?.show()
    fullscreenContentControls.visibility = View.VISIBLE
  }
  private var isFullscreen: Boolean = false

  private var isPinching = false

  private val hideRunnable = Runnable { hide() }

  /**
   * Touch listener to use for in-layout UI controls to delay hiding the
   * system UI. This is to prevent the jarring behavior of controls going away
   * while interacting with activity UI.
   */
  private val delayHideTouchListener = View.OnTouchListener { view, motionEvent ->
    when (motionEvent.action) {
      MotionEvent.ACTION_DOWN -> if (AUTO_HIDE) {
        delayedHide(AUTO_HIDE_DELAY_MILLIS)
      }

      MotionEvent.ACTION_UP -> view.performClick()
      else -> {
      }
    }
    false
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    // Init OpenCV
    if (OpenCVLoader.initLocal()) {
      Log.i(TAG, "OpenCV loaded successfully");
    } else {
      Toast.makeText(
        this,
        "OpenCV initialization failed!",
        Toast.LENGTH_LONG)
        .show()
    }

    binding = ActivityFullscreenBinding.inflate(layoutInflater)
    setContentView(binding.root)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    isFullscreen = true

    imageTools = ImageTools(this)

    // Set up the user interaction to manually show or hide the system UI.
    fullscreenContent = binding.fullscreenContent
    fullscreenContent.setOnTouchListener { v, event ->
      if (event.pointerCount == 2) {
        panGestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
      }

      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          isPinching = false
        }
        MotionEvent.ACTION_POINTER_DOWN -> {
          // A second finger touched the screen
          isPinching = true
        }
        MotionEvent.ACTION_UP -> {
          // Final check: was a pinch active during this touch sequence?
          // scaleDetector.isInProgress remains true until fingers are lifted
          if (!isPinching && !scaleGestureDetector.isInProgress) {
            toggle()
          }
        }
      }
      true // Consume the event
    }

    fullscreenContentControls = binding.fullscreenContentControls

    // Upon interacting with UI controls, delay any scheduled hide()
    // operations to prevent the jarring behavior of controls going away
    // while interacting with the UI.
    supportActionBar
    cameraViewMain = findViewById(R.id.svCameraViewMain)
    cameraViewMain.setAspectRatio(640, 480)
    cameraOverlay = findViewById(R.id.svCameraOverlay)
    scaleGestureDetector = ScaleGestureDetector(this,
      ScaleListener(cameraOverlay))
    panGestureDetector = GestureDetector(this, PanListener(cameraOverlay))

    cameraViewMain.getHolder().addCallback(object : SurfaceHolder.Callback {
      override fun surfaceCreated(holder: SurfaceHolder) {
        cameraHelper?.addSurface(holder.getSurface(), false)
      }

      override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
      }

      override fun surfaceDestroyed(holder: SurfaceHolder) {
        cameraHelper?.removeSurface(holder.getSurface())
      }
    })
  }

  override fun onStart() {
    super.onStart()
    initCameraHelper()
  }

  override fun onStop() {
    super.onStop()
    clearCameraHelper()
  }

  private fun initCameraHelper() {
    if (cameraHelper == null) {
      cameraHelper = CameraHelper()
      cameraHelper?.setStateCallback(stateListener)

      // select a uvc device
      val list: MutableList<UsbDevice?>? = cameraHelper?.getDeviceList()
      if (list != null && list.size > 0) {
        Toast.makeText(
          this,
          "Device: %1s".format(list.get(0)?.deviceName),
          Toast.LENGTH_SHORT)
          .show()
        cameraHelper?.selectDevice(list.get(0))
      } else {
        Toast.makeText(
          this,
          "No usable USB UVC device found!",
          Toast.LENGTH_LONG)
          .show()
      }
    }
  }

  private fun clearCameraHelper() {
    cameraHelper?.release()
    cameraHelper = null
  }

  private fun selectDevice(device: UsbDevice) {
    cameraHelper?.selectDevice(device)
  }

  private val stateListener: ICameraHelper.StateCallback = object : ICameraHelper.StateCallback {
    override fun onAttach(device: UsbDevice) {
      selectDevice(device)
    }

    override fun onDeviceOpen(device: UsbDevice?, isFirstOpen: Boolean) {
      cameraHelper?.openCamera()
    }

    override fun onCameraOpen(device: UsbDevice?) {
      cameraHelper?.startPreview()

//      val size: Size? = cameraHelper?.getPreviewSize()
//      if (size != null) {
//        val width = size.width
//        val height = size.height
//        //auto aspect ratio
//        cameraViewMain.setAspectRatio(width, height)
//      }

      //cameraHelper?.addSurface(cameraViewMain.getHolder().getSurface(), false)
      cameraHelper?.setFrameCallback(IFrameCallback { frame: ByteBuffer? ->
        val nv21 = ByteArray(frame!!.remaining())
        frame.get(nv21, 0, nv21.size)

        val size: Size? = cameraHelper?.getPreviewSize()

        // START PROCESSING FRAME. This could probably be more efficient but requires digging more
        // into OpenCV, which is currently a heap of undocumented trash.

        // Convert from nv21 to something OpenCV can work with
        var yuv = Mat(size!!.height + size.height/2, size.width, CvType.CV_8UC1)
        yuv.put(0, 0, nv21);
        var rgba = Mat(size.width, size.height, CvType.CV_8UC1)
        Imgproc.cvtColor( yuv, rgba, Imgproc.COLOR_YUV2RGB_NV21, 4 );

        val imgGray = Mat()
        val cannyEdges = Mat()
        val colorized = Mat()
        val mergedImage = Mat()

        // Convert source image to grayscale
        Imgproc.cvtColor(rgba, imgGray, Imgproc.COLOR_RGBA2GRAY)

        // Blur source image for easier edge detection
        Imgproc.GaussianBlur(imgGray, imgGray, org.opencv.core.Size(5.0, 5.0), 6.0, 6.0)

        // Do teh edging
        Imgproc.Canny(imgGray, cannyEdges, 80.0, 100.0)

        // Convert grayscale edges to RGBA colorspace (still gray though)
        Imgproc.cvtColor(cannyEdges, colorized, Imgproc.COLOR_GRAY2RGBA)

        // Colorize edges to overlay on source image
        Core.multiply(colorized, Scalar(5.0, 0.0, 0.0), colorized)

        // Thicken lines for better visibility
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, org.opencv.core.Size(5.0, 5.0))
        // Apply dilation (iterations=1 for subtle thickening, more for thicker lines)
        Imgproc.dilate(colorized, colorized, kernel, Point(-1.0, -1.0), 1)

        // Merge overlay with source image
        Core.addWeighted(rgba, 1.0, colorized, 0.7, 0.0, mergedImage)

        // Convert the OpenCV matrix to a bitmap that Android can use
        val bmp: Bitmap? = imageTools.matToBitMap(mergedImage)

        runOnUiThread(Runnable {
          cameraOverlay.setImageBitmap(bmp)
        })
      }, UVCCamera.PIXEL_FORMAT_NV21)
    }

    override fun onCameraClose(device: UsbDevice?) {
      if (cameraHelper != null) {
        cameraHelper?.removeSurface(cameraViewMain.getHolder().getSurface())
      }
    }

    override fun onDeviceClose(device: UsbDevice?) {
    }

    override fun onDetach(device: UsbDevice?) {
    }

    override fun onCancel(device: UsbDevice?) {
    }
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)

    // Trigger the initial hide() shortly after the activity has been
    // created, to briefly hint to the user that UI controls
    // are available.
    delayedHide(100)
  }

  private fun toggle() {
    if (isFullscreen) {
      hide()
    } else {
      show()
    }
  }

  private fun hide() {
    // Hide UI first
    supportActionBar?.hide()
    fullscreenContentControls.visibility = View.GONE
    isFullscreen = false

    // Schedule a runnable to remove the status and navigation bar after a delay
    hideHandler.removeCallbacks(showPart2Runnable)
    hideHandler.postDelayed(hidePart2Runnable, UI_ANIMATION_DELAY.toLong())
  }

  private fun show() {
    // Show the system bar
    if (Build.VERSION.SDK_INT >= 30) {
      fullscreenContent.windowInsetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    } else {
      fullscreenContent.systemUiVisibility =
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
          View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
    isFullscreen = true

    // Schedule a runnable to display UI elements after a delay
    hideHandler.removeCallbacks(hidePart2Runnable)
    hideHandler.postDelayed(showPart2Runnable, UI_ANIMATION_DELAY.toLong())
  }

  /**
   * Schedules a call to hide() in [delayMillis], canceling any
   * previously scheduled calls.
   */
  private fun delayedHide(delayMillis: Int) {
    hideHandler.removeCallbacks(hideRunnable)
    hideHandler.postDelayed(hideRunnable, delayMillis.toLong())
  }

  private class ScaleListener(var imageView: ImageView) : SimpleOnScaleGestureListener() {
    private var scaleFactor = 1.0f

    override fun onScale(scaleGestureDetector: ScaleGestureDetector): Boolean {
      scaleFactor *= scaleGestureDetector.getScaleFactor()
      scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f))
      imageView.setScaleX(scaleFactor)
      imageView.setScaleY(scaleFactor)
      return true
    }
  }

  private class PanListener(var imageView: ImageView) : GestureDetector.SimpleOnGestureListener() {
    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
      // Calculate current scaled boundaries
      val scaledWidth = imageView.width * imageView.scaleX
      val scaledHeight = imageView.height * imageView.scaleY

      // Calculate max pan distance (assuming image is centered initially)
      val maxX = (scaledWidth - imageView.width) / 2f
      val maxY = (scaledHeight - imageView.height) / 2f

      // Apply and clamp the translations
      imageView.translationX = (imageView.translationX - dX).coerceIn(-maxX, maxX)
      imageView.translationY = (imageView.translationY - dY).coerceIn(-maxY, maxY)
      return false
    }
  }

  companion object {
    private const val TAG = "MonMon"
    /**
     * Whether or not the system UI should be auto-hidden after
     * [AUTO_HIDE_DELAY_MILLIS] milliseconds.
     */
    private const val AUTO_HIDE = true

    /**
     * If [AUTO_HIDE] is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private const val AUTO_HIDE_DELAY_MILLIS = 3000

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private const val UI_ANIMATION_DELAY = 300
  }
}
