package com.apil.nettrust_nepal

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.apil.nettrust_nepal.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var detector: RogueAPDetector
    private lateinit var wifiManager: WifiManager
    private lateinit var adapter: DetectionAdapter

    private var isScanning = false
    private var autoScanEnabled = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val handler = Handler(Looper.getMainLooper())
    private val autoScanInterval = 10000L // 10 seconds

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // WiFi scan receiver
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processScanResults()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        initializeComponents()
        checkPermissions()
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "NetTrust Nepal"

        // Setup RecyclerView
        adapter = DetectionAdapter(emptyList())
        binding.recyclerDetections.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }

        // Button listeners
        binding.btnScan.setOnClickListener { performScan() }
        binding.btnClearResults.setOnClickListener { clearResults() }
        binding.switchAutoScan.setOnCheckedChangeListener { _, isChecked ->
            toggleAutoScan(isChecked)
        }
        binding.cardModelInfo.setOnClickListener { showModelInfo() }

        // Initial state
        showEmptyState()
    }

    private fun initializeComponents() {
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        detector = RogueAPDetector(this)

        if (!detector.isLoaded()) {
            showError("⚠️ Model failed to load. Check asset files.")
        } else {
            updateModelInfo()
        }
    }

    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                showSuccess("✅ Permissions granted")
                performScan()
            } else {
                showError("❌ WiFi scanning requires location permission")
            }
        }
    }

    private fun performScan() {
        if (!detector.isLoaded()) {
            showError("Model not loaded")
            return
        }

        if (!hasRequiredPermissions()) {
            checkPermissions()
            return
        }

        if (!wifiManager.isWifiEnabled) {
            showError("Please enable WiFi")
            return
        }

        if (isScanning) {
            return
        }

        isScanning = true
        showScanningState()

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Trigger WiFi scan
                    wifiManager.startScan()
                    delay(2000) // Wait for scan to complete
                }

                processScanResults()

            } catch (e: Exception) {
                showError("Scan failed: ${e.message}")
            } finally {
                isScanning = false
                hideScanningState()
            }
        }
    }

    private fun processScanResults() {
        scope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val scanResults = wifiManager.scanResults

                    scanResults.map { result ->
                        detector.detect(
                            ssid = result.SSID,
                            bssid = result.BSSID,
                            frequency = result.frequency,
                            signalStrength = result.level,
                            channel = getChannelFromFrequency(result.frequency),
                            capabilities = result.capabilities
                        )
                    }.sortedByDescending { it.threatLevel.ordinal }
                }

                // Update UI
                if (results.isNotEmpty()) {
                    adapter.updateDetections(results)
                    updateStatistics(results)
                    hideEmptyState()

                    // Animate results
                    binding.recyclerDetections.scheduleLayoutAnimation()

                    // Check for threats
                    val threats = results.filter { it.isThreat }
                    if (threats.isNotEmpty()) {
                        showThreatAlert(threats.size)
                    }
                } else {
                    showEmptyState()
                }

            } catch (e: Exception) {
                showError("Error processing results: ${e.message}")
            }
        }
    }

    private fun updateStatistics(results: List<RogueAPDetector.DetectionResult>) {
        val total = results.size
        val threats = results.count { it.isThreat }
        val rogueAPs = results.count { it.isRogueAP }
        val critical = results.count { it.threatLevel == RogueAPDetector.ThreatLevel.CRITICAL }

        binding.apply {
            tvTotalNetworks.text = total.toString()
            tvThreatsDetected.text = threats.toString()
            tvRogueAPs.text = rogueAPs.toString()
            tvCriticalThreats.text = critical.toString()

            // Update card colors
            cardStatistics.setCardBackgroundColor(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (threats > 0) R.color.background else R.color.background
                )
            )

            // Animate statistics
            cardStatistics.startAnimation(
                AnimationUtils.loadAnimation(this@MainActivity, R.anim.scale_up)
            )
        }
    }

    private fun showThreatAlert(count: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Threats Detected!")
            .setMessage("Found $count potential threat(s). Please review the results carefully.")
            .setIcon(R.drawable.ic_warning_24)
            .setPositiveButton("View") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun toggleAutoScan(enabled: Boolean) {
        autoScanEnabled = enabled

        if (enabled) {
            showSuccess("Auto-scan enabled")
            startAutoScan()
        } else {
            showSuccess("Auto-scan disabled")
            stopAutoScan()
        }
    }

    private val autoScanRunnable = object : Runnable {
        override fun run() {
            if (autoScanEnabled) {
                performScan()
                handler.postDelayed(this, autoScanInterval)
            }
        }
    }

    private fun startAutoScan() {
        handler.post(autoScanRunnable)
    }

    private fun stopAutoScan() {
        handler.removeCallbacks(autoScanRunnable)
    }

    private fun clearResults() {
        adapter.updateDetections(emptyList())
        showEmptyState()
        resetStatistics()
    }

    private fun resetStatistics() {
        binding.apply {
            tvTotalNetworks.text = "0"
            tvThreatsDetected.text = "0"
            tvRogueAPs.text = "0"
            tvCriticalThreats.text = "0"
            cardStatistics.setCardBackgroundColor(
                ContextCompat.getColor(this@MainActivity, R.color.card_background)
            )
        }
    }

    private fun showModelInfo() {
        val info = detector.getModelInfo()
        MaterialAlertDialogBuilder(this)
            .setTitle("Model Information")
            .setMessage(info)
            .setIcon(R.drawable.ic_info_24)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateModelInfo() {
        binding.tvModelStatus.text = "Model: Loaded ✓"
    }

    private fun showScanningState() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            btnScan.isEnabled = false
            tvScanStatus.text = "Scanning WiFi networks..."
            tvScanStatus.visibility = View.VISIBLE
        }
    }

    private fun hideScanningState() {
        binding.apply {
            progressBar.visibility = View.GONE
            btnScan.isEnabled = true
            tvScanStatus.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        binding.apply {
            layoutEmpty.visibility = View.VISIBLE
            recyclerDetections.visibility = View.GONE
        }
    }

    private fun hideEmptyState() {
        binding.apply {
            layoutEmpty.visibility = View.GONE
            recyclerDetections.visibility = View.VISIBLE
        }
    }

    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.threat_safe))
            .show()
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.threat_red))
            .show()
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            frequency in 2412..2484 -> {
                if (frequency == 2484) 14 else (frequency - 2407) / 5
            }
            frequency in 5170..5825 -> (frequency - 5000) / 5
            else -> 0
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoScan()
        scope.cancel()
    }
}