package com.honksoft.monmon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    ContextCompat.checkSelfPermission(
      baseContext, it
    ) == PackageManager.PERMISSION_GRANTED
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.action_info -> {
        val message =
          SpannableString("Written with olev by Gunbard\nGitHub: https://github.com/Gunbard/MonMon")
        Linkify.addLinks(message, Linkify.WEB_URLS)
        val dialog = AlertDialog.Builder(this)
          .setTitle("About MonMon")
          .setMessage(message)
          .setPositiveButton("That's real fackin neato") { dialog, _ -> dialog.dismiss() }
          .show()

        val textView = dialog.findViewById<TextView>(android.R.id.message)
        textView?.movementMethod = LinkMovementMethod.getInstance()
        true
      }

      R.id.action_help -> {
        AlertDialog.Builder(this)
          .setTitle("MonMon Help")
          .setMessage("MonMon will look for the first connected device that supports USB UVC (HDMI capture devices, webcams, etc.)\n\nTAP the screen once to toggle fullscreen and non-fullscreen modes.\n\nPINCH to zoom in and out and use TWO FINGERS to drag the live view. Adjust peaking threshold and other settings with the wrench icon.")
          .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
          .show()
        true
      }

      else -> super.onOptionsItemSelected(item)
    }
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