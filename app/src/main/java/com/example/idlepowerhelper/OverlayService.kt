package com.example.idlepowerhelper

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Foreground service that:
 *  1. Draws a small floating control panel over any app.
 *  2. Captures the screen via MediaProjection.
 *  3. Runs the auto-merge loop: analyse → find move → swipe → repeat.
 */
class OverlayService : Service() {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private const val NOTIF_ID   = 1
        private const val CHANNEL_ID = "iph_channel"

        const val ACTION_STOP = "com.example.idlepowerhelper.STOP"

        /** Delay between merge attempts in milliseconds, keyed by speed setting. */
        private val SPEED_DELAY_MS = mapOf("slow" to 1500L, "normal" to 800L, "fast" to 350L)

        /** Smallest allowed grid width/height (px) — guards against edge nudges crossing over. */
        private const val MIN_GRID_SIZE_PX = 160
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var screenWidth  = 0
    private var screenHeight = 0

    private var debugView: GridDebugView? = null
    private var debugShown = false
    private var currentBounds: GridBounds? = null
    private val nudgeStep = 40

    private var isRunning    = false
    private var speedKey     = "normal"
    private var sweepIter    = 0

    /**
     * Y offset (pixels) between the overlay view's coordinate space and the
     * absolute screen coordinates expected by dispatchGesture().
     *
     * On Android 12+ TYPE_APPLICATION_OVERLAY windows may start below the
     * status bar / display cutout even when FLAG_LAYOUT_IN_SCREEN is set.
     * GridBounds are stored in the overlay's local coordinate space, so we
     * must add this offset when dispatching gestures (which use absolute
     * screen coords). Measured once at service startup via
     * [measureGestureOffset], and re-measured whenever the debug overlay is
     * shown (harmless — same value, just keeps the debug view's own
     * measurement path intact).
     */
    private var gestureYOffset = 0

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        resolveScreenSize()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        showOverlay()
        measureGestureOffset()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val code = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (code != -1 && data != null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(code, data)
            setupCapture()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        scope.cancel()
        virtualDisplay?.release()
        mediaProjection?.stop()
        imageReader?.close()
        overlayView?.let { windowManager.removeView(it) }
        debugView?.let { runCatching { windowManager.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Screen size ───────────────────────────────────────────────────────────

    private fun resolveScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth  = bounds.width()
            screenHeight = bounds.height()
        } else {
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            screenWidth  = dm.widthPixels
            screenHeight = dm.heightPixels
        }
    }

    /**
     * Measures [gestureYOffset] once at startup so gestures land correctly
     * even if the user never opens the debug overlay (which previously was
     * the only place this offset got computed). Adds a throwaway 1×1
     * fullscreen-anchored probe view at (0,0), reads back its true on-screen
     * position, then removes it.
     */
    private fun measureGestureOffset() {
        val probe = View(this)
        val params = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        runCatching { windowManager.addView(probe, params) }.onFailure { return }

        probe.post {
            val loc = IntArray(2)
            probe.getLocationOnScreen(loc)
            gestureYOffset = loc[1]
            android.util.Log.d("IPH", "gestureYOffset(startup)=$gestureYOffset  screen=${screenWidth}x${screenHeight}")
            runCatching { windowManager.removeView(probe) }
        }
    }

    // ── Screen capture setup ──────────────────────────────────────────────────

    private fun setupCapture() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "IphCapture",
            screenWidth, screenHeight,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )
    }

    private fun captureScreen(): Bitmap? {
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val plane      = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * screenWidth
            val bmp = Bitmap.createBitmap(
                screenWidth + rowPadding / plane.pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            bmp
        } finally {
            image.close()
        }
    }

    // ── Floating overlay ──────────────────────────────────────────────────────

    private fun showOverlay() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_panel, null)
        overlayView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 4
            y = 200
        }

        windowManager.addView(view, params)
        makeDraggable(view, params)

        view.findViewById<Button>(R.id.btn_start_stop).setOnClickListener { toggleMerge() }
        view.findViewById<Button>(R.id.btn_close).setOnClickListener { stopSelf() }
        view.findViewById<Button>(R.id.btn_debug).setOnClickListener { toggleDebug() }
        view.findViewById<Button>(R.id.btn_reset).setOnClickListener { resetMoveCounter() }

        // Edge nudge buttons
        fun nudge(transform: GridBounds.() -> GridBounds) {
            val base = currentBounds ?: GridConfig.load(this) ?: GridConfig.default(screenWidth, screenHeight)
            val next = base.transform()
            // Guard against edges crossing each other (e.g. repeatedly tapping
            // "Left in"/"Right in") which would produce a zero/negative-size
            // grid and break cellCenter() math. Ignore nudges that would
            // shrink either dimension below a sane minimum.
            if (next.width < MIN_GRID_SIZE_PX || next.height < MIN_GRID_SIZE_PX) return
            currentBounds = next
            debugView?.bounds = currentBounds
            debugView?.postInvalidate()
        }

        view.findViewById<Button>(R.id.btn_top_up).setOnClickListener    { nudge { copy(top    = top    - nudgeStep) } }
        view.findViewById<Button>(R.id.btn_top_down).setOnClickListener   { nudge { copy(top    = top    + nudgeStep) } }
        view.findViewById<Button>(R.id.btn_bot_up).setOnClickListener     { nudge { copy(bottom = bottom - nudgeStep) } }
        view.findViewById<Button>(R.id.btn_bot_down).setOnClickListener   { nudge { copy(bottom = bottom + nudgeStep) } }
        view.findViewById<Button>(R.id.btn_left_in).setOnClickListener    { nudge { copy(left   = left   + nudgeStep) } }
        view.findViewById<Button>(R.id.btn_left_out).setOnClickListener   { nudge { copy(left   = left   - nudgeStep) } }
        view.findViewById<Button>(R.id.btn_right_in).setOnClickListener   { nudge { copy(right  = right  - nudgeStep) } }
        view.findViewById<Button>(R.id.btn_right_out).setOnClickListener  { nudge { copy(right  = right  + nudgeStep) } }

        // Move entire grid (shift all 4 edges, preserving cell size)
        view.findViewById<Button>(R.id.btn_move_up).setOnClickListener    { nudge { copy(top = top - nudgeStep, bottom = bottom - nudgeStep) } }
        view.findViewById<Button>(R.id.btn_move_down).setOnClickListener  { nudge { copy(top = top + nudgeStep, bottom = bottom + nudgeStep) } }
        view.findViewById<Button>(R.id.btn_move_left).setOnClickListener  { nudge { copy(left = left - nudgeStep, right = right - nudgeStep) } }
        view.findViewById<Button>(R.id.btn_move_right).setOnClickListener { nudge { copy(left = left + nudgeStep, right = right + nudgeStep) } }

        view.findViewById<Button>(R.id.btn_save_grid_overlay).setOnClickListener {
            currentBounds?.let {
                GridConfig.save(this, it)
                setStatus("Grid saved ✓")
            }
        }

        view.findViewById<RadioGroup>(R.id.rg_speed).setOnCheckedChangeListener { _, id ->
            speedKey = when (id) {
                R.id.rb_slow   -> "slow"
                R.id.rb_fast   -> "fast"
                else           -> "normal"
            }
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startRawX = 0f
        var startRawY = 0f
        var startPx   = 0
        var startPy   = 0

        view.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = ev.rawX; startRawY = ev.rawY
                    startPx = params.x;  startPy = params.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startPx + (startRawX - ev.rawX).toInt()
                    params.y = startPy + (ev.rawY   - startRawY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Required so accessibility services (e.g. TalkBack) can
                    // properly recognize a tap-without-drag on this view.
                    v.performClick()
                    false
                }
                else -> false
            }
        }
    }

    private fun setStatus(text: String) {
        Handler(Looper.getMainLooper()).post {
            overlayView?.findViewById<TextView>(R.id.tv_status)?.text = text
        }
    }

    private fun logMove(label: String, move: MergeMove, fx: Int, fy: Int, tx: Int, ty: Int) {
        val entry = "$label(${move.fromRow},${move.fromCol})→(${move.toRow},${move.toCol}) $fx,$fy→$tx,$ty"
        android.util.Log.d("IPH", entry)
        Handler(Looper.getMainLooper()).post {
            debugView?.updateMove(move)
        }
    }

    // ── Debug grid overlay ────────────────────────────────────────────────────

    private fun toggleDebug() {
        if (debugShown) hideDebugOverlay() else showDebugOverlay()
    }

    private fun showDebugOverlay() {
        currentBounds = GridConfig.load(this) ?: GridConfig.default(screenWidth, screenHeight)

        val dv = GridDebugView(this).also {
            it.bounds = currentBounds
            debugView = it
        }

        val params = WindowManager.LayoutParams(
            screenWidth,
            screenHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = 0
        }

        windowManager.addView(dv, params)
        debugShown = true

        // Measure the true screen position of the overlay window once it is
        // laid out.  getLocationOnScreen() returns the absolute y of the
        // window origin; if it is > 0 (e.g. the window starts below the
        // status bar despite FLAG_LAYOUT_IN_SCREEN), we must add this offset
        // to every gesture Y coordinate so it lands in the right place.
        dv.post {
            val loc = IntArray(2)
            dv.getLocationOnScreen(loc)
            gestureYOffset = loc[1]
            android.util.Log.d("IPH", "gestureYOffset=$gestureYOffset  screen=${screenWidth}x${screenHeight}")
        }

        Handler(Looper.getMainLooper()).post {
            overlayView?.findViewById<Button>(R.id.btn_debug)?.text = "🔍 Hide"
            overlayView?.findViewById<android.view.View>(R.id.layout_grid_adjust)?.visibility =
                android.view.View.VISIBLE
        }

    }

    private fun hideDebugOverlay() {
        debugView?.let { runCatching { windowManager.removeView(it) } }
        debugView = null
        debugShown = false
        Handler(Looper.getMainLooper()).post {
            overlayView?.findViewById<Button>(R.id.btn_debug)?.text = "🔍 Debug"
            overlayView?.findViewById<android.view.View>(R.id.layout_grid_adjust)?.visibility =
                android.view.View.GONE
        }
    }

    // ── Auto-merge loop ───────────────────────────────────────────────────────

    private fun toggleMerge() {
        if (isRunning) stopMerge() else startMerge()
    }

    private fun startMerge() {
        isRunning = true
        Handler(Looper.getMainLooper()).post {
            overlayView?.findViewById<Button>(R.id.btn_start_stop)?.text = "⏸ Pause"
            overlayView?.findViewById<android.view.View>(R.id.layout_idle)?.visibility = android.view.View.GONE
            overlayView?.findViewById<android.view.View>(R.id.layout_running)?.visibility = android.view.View.VISIBLE
            overlayView?.findViewById<android.view.View>(R.id.btn_close)?.visibility = android.view.View.GONE
        }
        scope.launch {
            while (isRunning) {
                executeMergeStep()
                delay(SPEED_DELAY_MS[speedKey] ?: 800L)
            }
        }
    }

    private fun stopMerge() {
        isRunning = false
        Handler(Looper.getMainLooper()).post {
            overlayView?.findViewById<Button>(R.id.btn_start_stop)?.text = "▶ Resume"
            overlayView?.findViewById<android.view.View>(R.id.layout_idle)?.visibility = android.view.View.VISIBLE
            overlayView?.findViewById<android.view.View>(R.id.layout_running)?.visibility = android.view.View.GONE
            overlayView?.findViewById<android.view.View>(R.id.btn_close)?.visibility = android.view.View.VISIBLE
        }
        setStatus("Idle")
    }

    /**
     * Resets the move counter back to the start of the sweep sequence.
     * Only reachable while paused/idle — `btn_reset` lives inside
     * `layout_idle`, which is hidden whenever the merge loop is running.
     * Guarded here too so a stray call while running is a no-op.
     */
    private fun resetMoveCounter() {
        if (isRunning) return
        sweepIter = 0
        setStatus("Reset ✓")
    }

    private suspend fun executeMergeStep() {
        val bounds = currentBounds
            ?: GridConfig.load(this@OverlayService)
            ?: GridConfig.default(screenWidth, screenHeight)

        val move = BatteryMerger.sweepMove(sweepIter)
        val moveNum  = (sweepIter % BatteryMerger.SWEEP_SIZE) + 1
        val movesLeft = BatteryMerger.SWEEP_SIZE - moveNum
        val etaMs    = movesLeft * (SPEED_DELAY_MS[speedKey] ?: 800L)
        val etaStr   = formatDuration(etaMs)

        logMove("SW[$moveNum]:", move,
            bounds.cellCenter(move.fromRow, move.fromCol).first.toInt(),
            bounds.cellCenter(move.fromRow, move.fromCol).second.toInt(),
            bounds.cellCenter(move.toRow, move.toCol).first.toInt(),
            bounds.cellCenter(move.toRow, move.toCol).second.toInt())

        Handler(Looper.getMainLooper()).post {
            overlayView?.findViewById<TextView>(R.id.tv_move_number)?.text = "$moveNum / ${BatteryMerger.SWEEP_SIZE}"
            overlayView?.findViewById<TextView>(R.id.tv_moves_left)?.text = "$movesLeft left"
            overlayView?.findViewById<TextView>(R.id.tv_eta)?.text = "ETA $etaStr"
        }

        sweepIter++
        executeSwipe(move, bounds)
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0  -> "${h}h ${m}m"
            m > 0  -> "${m}m ${s}s"
            else   -> "${s}s"
        }
    }

    /**
     * Dispatches the swipe on the main thread and suspends until it finishes.
     */
    private suspend fun executeSwipe(move: MergeMove, bounds: GridBounds) {
        val svc = SwipeAccessibilityService.instance ?: run {
            setStatus("No accessibility svc")
            return
        }

        val (fromX, fromY) = bounds.cellCenter(move.fromRow, move.fromCol)
        val (toX,   toY)   = bounds.cellCenter(move.toRow,   move.toCol)

        suspendCoroutine { cont ->
            Handler(Looper.getMainLooper()).post {
                svc.performSwipe(fromX, fromY + gestureYOffset, toX, toY + gestureYOffset) {
                    cont.resume(Unit)
                }
            }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        // minSdk is 26 (O), so notification channels always need creating here.
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Idle Power Helper",
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Idle Power Helper")
            .setContentText("Auto-merge overlay active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_delete, "Stop service", stopIntent)
            .build()
    }
}
