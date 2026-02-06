package com.honksoft.monmon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.honksoft.monmon.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
  private val activityResultLauncher =
    registerForActivityResult(
      ActivityResultContracts.RequestMultiplePermissions()
    )
    { permissions ->
      // Handle Permission granted/rejected
      var permissionGranted = true
      permissions.entries.forEach {
        if (it.key in REQUIRED_PERMISSIONS && it.value == false) {
          permissionGranted = false
        }
      }
      if (!permissionGranted) {
        Toast.makeText(
          baseContext,
          "Permission request denied",
          Toast.LENGTH_SHORT
        ).show()
      } else {
        openDevice()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    val colorInt = ContextCompat.getColor(this, R.color.semitransparent)
    supportActionBar?.setBackgroundDrawable(ColorDrawable(colorInt))

    binding.button.setOnClickListener { openDevice() }
  }

  private fun openDevice() {

    // Request camera permissions
    if (allPermissionsGranted()) {
      startActivity(Intent(this@MainActivity, FullscreenActivity::class.java))
    } else {
      requestPermissions()
    }
  }

  private fun requestPermissions() {
    activityResultLauncher.launch(REQUIRED_PERMISSIONS)
  }

  private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
    ContextCompat.checkSelfPermission(baseContext, it
    ) == PackageManager.PERMISSION_GRANTED
  }

  companion object {
    private val REQUIRED_PERMISSIONS =
      mutableListOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
      ).apply {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
          add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
      }.toTypedArray()
  }
}