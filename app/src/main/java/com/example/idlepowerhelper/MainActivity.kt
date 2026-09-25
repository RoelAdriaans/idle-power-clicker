package com.example.idlepowerhelper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Setup screen.  Guides the user through the three required permissions,
 * lets them calibrate the grid position, then launches the overlay service.
 */
class MainActivity : AppCompatActivity() {

    private val projectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    // ── Permission launchers ──────────────────────────────────────────────────

    private val overlayPermLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshStatus() }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            launchOverlayService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindButtons()
        bindSliders()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ── UI wiring ─────────────────────────────────────────────────────────────

    private fun bindButtons() {
        // Step 1 — overlay permission
        findViewById<Button>(R.id.btn_overlay_permission).setOnClickListener {
            overlayPermLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        // Step 2 — accessibility service
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Find \"Idle Power Helper\" and toggle it ON",
                Toast.LENGTH_LONG
            ).show()
        }

        // Step 3 — save grid
        findViewById<Button>(R.id.btn_save_grid).setOnClickListener { saveGrid() }

        // Step 4 — launch
        findViewById<Button>(R.id.btn_launch).setOnClickListener {
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    /** Sliders let the user fine-tune the grid bounds as screen percentages. */
    private fun bindSliders() {
        val dm = resources.displayMetrics

        fun updateLabel(seekId: Int, labelId: Int, suffix: String = "%") {
            val seek  = findViewById<SeekBar>(seekId)
            val label = findViewById<TextView>(labelId)
            label.text = "${seek.progress}$suffix"
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) {
                    label.text = "$v$suffix"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = Unit
                override fun onStopTrackingTouch(sb: SeekBar?) = Unit
            })
        }

        updateLabel(R.id.seek_grid_top,    R.id.tv_grid_top_val)
        updateLabel(R.id.seek_grid_bottom, R.id.tv_grid_bottom_val)
        updateLabel(R.id.seek_grid_left,   R.id.tv_grid_left_val)
        updateLabel(R.id.seek_grid_right,  R.id.tv_grid_right_val)

        // Pre-fill sliders from saved config or defaults
        val dm2  = resources.displayMetrics
        val saved = GridConfig.load(this)
        if (saved != null) {
            fun pct(px: Int, total: Int) = ((px.toFloat() / total) * 100).toInt()
            findViewById<SeekBar>(R.id.seek_grid_top).progress    = pct(saved.top,    dm2.heightPixels)
            findViewById<SeekBar>(R.id.seek_grid_bottom).progress = pct(saved.bottom, dm2.heightPixels)
            findViewById<SeekBar>(R.id.seek_grid_left).progress   = pct(saved.left,   dm2.widthPixels)
            findViewById<SeekBar>(R.id.seek_grid_right).progress  = pct(saved.right,  dm2.widthPixels)
        }
    }

    private fun saveGrid() {
        val dm = resources.displayMetrics
        fun px(seekId: Int, total: Int) =
            (findViewById<SeekBar>(seekId).progress / 100f * total).toInt()

        val bounds = GridBounds(
            left   = px(R.id.seek_grid_left,   dm.widthPixels),
            top    = px(R.id.seek_grid_top,    dm.heightPixels),
            right  = px(R.id.seek_grid_right,  dm.widthPixels),
            bottom = px(R.id.seek_grid_bottom, dm.heightPixels)
        )
        GridConfig.save(this, bounds)
        Toast.makeText(this, "Grid saved (${bounds.width}×${bounds.height} px)", Toast.LENGTH_SHORT).show()
    }

    // ── Status checks ─────────────────────────────────────────────────────────

    private fun refreshStatus() {
        val hasOverlay      = Settings.canDrawOverlays(this)
        val hasAccessibility = accessibilityEnabled()

        findViewById<TextView>(R.id.tv_overlay_status).text =
            if (hasOverlay) "✅ Overlay permission granted" else "❌ Overlay permission needed"

        findViewById<TextView>(R.id.tv_accessibility_status).text =
            if (hasAccessibility) "✅ Accessibility service enabled"
            else "❌ Accessibility service not enabled"

        val ready = hasOverlay && hasAccessibility
        findViewById<Button>(R.id.btn_launch).isEnabled = ready
        findViewById<TextView>(R.id.tv_launch_hint).text =
            if (ready) "Ready! Tap Launch, then switch to Idle Power."
            else "Complete steps 1 and 2 first."
    }

    private fun accessibilityEnabled(): Boolean {
        val name = "$packageName/${SwipeAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(name, ignoreCase = true) }
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    private fun launchOverlayService(resultCode: Int, data: Intent) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
            putExtra(OverlayService.EXTRA_RESULT_DATA, data)
        }
        // minSdk is 26 (O), so startForegroundService() is always available here.
        startForegroundService(intent)
        // Go back to the game — the floating panel is now in charge
        finish()
    }
}
