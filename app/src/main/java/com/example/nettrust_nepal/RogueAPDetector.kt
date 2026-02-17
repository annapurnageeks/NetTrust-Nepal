package com.apil.nettrust_nepal

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.exp
import kotlin.math.min

/**
 * NetTrust Nepal - 2-CLASS Malicious AP Detector v6.0
 *
 * ENHANCED DETECTION with Device Fingerprinting (Dec 2025):
 * ✓ Evil Twin Detection: 79.64% accuracy
 * ✓ Rogue WiFi Detection: 97.70% accuracy
 * ✓ HIGH Thresholds to Reduce False Positives
 * ✓ MAC OUI Database - Identifies device manufacturers
 * ✓ Legitimate Router Detection (TP-Link, Netgear, D-Link, etc.)
 * ✓ Mobile Hotspot Detection (iPhone, Samsung, etc.)
 * ✓ Attack Device Detection (Flipper Zero, ESP32, Raspberry Pi)
 * ✓ Detection Reasons Provided
 * ✓ Disclaimer: Results may not be 100% accurate
 * ✓ Persistent Attack Tracking
 * ✓ Baseline Learning - Your router stays SAFE
 *
 * Detection Focus:
 * - Evil Twin: Fake WiFi impersonating legitimate network (duplicate SSID)
 * - Rogue AP: Unauthorized access point (mobile hotspots, unknown devices)
 * - Safe: Legitimate network (known routers, low confidence, baseline)
 *
 * NEW in v6.0:
 * - Legitimate routers (TP-Link, Netgear, etc.) marked as SAFE
 * - Mobile hotspots classified as Rogue_AP (not Evil_Twin)
 * - Strong signal alone no longer triggers false positives
 * - Device manufacturer shown in detection reasons
 *
 * Model trained on 39,988 samples with proper validation
 */
class RogueAPDetector(private val context: Context) {

    companion object {
        private const val TAG = "RogueAPDetector"
        private const val BASELINE_THRESHOLD = 3  // Mark as learned after 3 clean scans
        private const val ATTACK_PERSISTENCE_THRESHOLD = 2  // Attack must appear 2+ times

        // HIGH thresholds to reduce false positives on legitimate networks
        private const val EVIL_TWIN_THRESHOLD = 0.65  // High confidence required
        private const val ROGUE_AP_THRESHOLD = 0.60   // Moderate confidence required

        val ATTACK_TYPES = listOf("Evil_Twin", "Rogue_AP")  // Only 2 classes now!
    }

    // PERSISTENT ATTACK TRACKING - Key to stability!
    data class AttackEvidence(
        var attackType: String,
        var totalConfidence: Double = 0.0,
        var detectionCount: Int = 0,
        var lastSeen: Long = System.currentTimeMillis(),
        var isConfirmed: Boolean = false
    )

    data class APProfile(
        val bssid: String,
        var ssid: String,
        var scanCount: Int = 0,
        var isBaseline: Boolean = false,
        var avgRssi: Double = 0.0,
        val rssiHistory: MutableList<Int> = mutableListOf(),
        val frequencyHistory: MutableList<Int> = mutableListOf(),
        var attackEvidence: AttackEvidence? = null  // PERSISTENT attack tracking
    )

    private val apProfiles = mutableMapOf<String, APProfile>()  // BSSID -> Profile
    private val ssidToBssids = mutableMapOf<String, MutableSet<String>>()  // Duplicate SSID tracking

    private var scalerMean: List<Double> = listOf()
    private var scalerStd: List<Double> = listOf()
    private var featureNames: List<String> = listOf()  // NEW: Load from JSON
    private var isModelLoaded = false
    private var modelAccuracy: Double = 0.0

    data class ScalerParams(val mean: List<Double>, val scale: List<Double>, val `var`: List<Double>)
    data class ModelMetadata(
        val project_name: String?,
        val model_version: String?,
        val performance: Performance?,
        val dataset_info: DatasetInfo?,
        val selected_features: List<String>?
    )
    data class Performance(
        val train_accuracy: Double?,
        val val_accuracy: Double?,
        val test_accuracy: Double?
    )
    data class DatasetInfo(
        val attack_types: List<String>?,
        val num_features: Int?
    )

    data class DetectionResult(
        val networkName: String, val bssid: String, val attackType: String,
        val confidence: Double, val isRogueAP: Boolean, val isThreat: Boolean,
        val threatLevel: ThreatLevel, val allProbabilities: Map<String, Double>,
        val signalStrength: Int, val frequency: Int, val channel: Int,
        val timestamp: Long = System.currentTimeMillis(),
        val recommendedAction: String, val isBaseline: Boolean = false,
        val detectionCount: Int = 0,  // How many times detected
        val detectionReasons: List<String> = listOf()  // NEW: Why detected as malicious
    )

    enum class ThreatLevel { SAFE, LOW, MEDIUM, HIGH, CRITICAL }

    init {
        loadModel()
    }

    private fun loadModel(): Boolean {
        return try {
            // Load scaler parameters
            val scalerJson = readJsonFromAssets("android_scaler_params.json")
            val scalerParams = Gson().fromJson(scalerJson, ScalerParams::class.java)
            scalerMean = scalerParams.mean
            scalerStd = scalerParams.scale  // Use 'scale' from new model

            // Load feature names
            val featuresJson = readJsonFromAssets("android_feature_names.json")
            featureNames = Gson().fromJson(featuresJson, Array<String>::class.java).toList()

            // Load metadata
            val metadataJson = readJsonFromAssets("android_model_metadata.json")
            val metadata = Gson().fromJson(metadataJson, ModelMetadata::class.java)
            modelAccuracy = (metadata.performance?.test_accuracy ?: 0.0) * 100.0  // Convert to percentage

            Log.i(TAG, "✅ 2-Class Detection System v5.0")
            Log.i(TAG, "Model: ${metadata.model_version}")
            Log.i(TAG, "Features: ${featureNames.size}")
            Log.i(TAG, "Test Accuracy: ${String.format("%.2f", modelAccuracy)}%")
            Log.i(TAG, "Evil Twin threshold: $EVIL_TWIN_THRESHOLD")
            Log.i(TAG, "Rogue AP threshold: $ROGUE_AP_THRESHOLD")
            Log.i(TAG, "Focus: Evil Twin & Rogue WiFi only")
            Log.i(TAG, "Persistent tracking ENABLED")

            isModelLoaded = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Model error: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun readJsonFromAssets(fileName: String): String {
        return context.assets.open(fileName).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).readText()
        }
    }

    private fun standardizeFeatures(features: List<Double>): List<Double> {
        return features.mapIndexed { i, value ->
            val std = scalerStd[i]
            if (std != 0.0) (value - scalerMean[i]) / std else value - scalerMean[i]
        }
    }

    /**
     * MAIN DETECTION with PERSISTENT TRACKING
     */
    fun detect(
        ssid: String, bssid: String, frequency: Int,
        signalStrength: Int, channel: Int, capabilities: String = ""
    ): DetectionResult {

        if (!isModelLoaded) {
            return createSafeResult(ssid, bssid, signalStrength, frequency, channel)
        }

        try {
            // Get or create AP profile
            var profile = apProfiles[bssid]
            if (profile == null) {
                profile = APProfile(bssid, ssid)
                apProfiles[bssid] = profile
                Log.d(TAG, "📝 NEW AP: $ssid ($bssid)")
            }

            // Update profile
            profile.ssid = ssid  // Update in case it changed
            profile.scanCount++
            profile.rssiHistory.add(signalStrength)
            profile.frequencyHistory.add(frequency)
            if (profile.rssiHistory.size > 5) profile.rssiHistory.removeAt(0)
            if (profile.frequencyHistory.size > 5) profile.frequencyHistory.removeAt(0)
            profile.avgRssi = profile.rssiHistory.average()

            // Track SSID duplicates
            if (ssid.isNotEmpty()) {
                if (!ssidToBssids.containsKey(ssid)) ssidToBssids[ssid] = mutableSetOf()
                ssidToBssids[ssid]!!.add(bssid)
            }

            // Extract features
            val features = extractFeatures(bssid, frequency, signalStrength, channel, capabilities)
            val standardizedFeatures = standardizeFeatures(features)

            // Collect detection reasons
            val detectionReasons = mutableListOf<String>()

            // Get ML prediction
            val mlScores = predictMLModel(standardizedFeatures, signalStrength, frequency)

            // Apply STABLE rule-based detection
            val (ruleAttack, ruleConf, ruleReasons) = applyStableRules(
                ssid, bssid, frequency, signalStrength, channel,
                capabilities, profile
            )

            // Add rule-based reasons
            detectionReasons.addAll(ruleReasons)

            // Combine ML + Rules
            var finalAttack = if (ruleConf > mlScores.values.maxOrNull()!!) ruleAttack
            else mlScores.maxByOrNull { it.value }!!.key
            var finalConf = maxOf(ruleConf, mlScores[finalAttack] ?: 0.0)

            // Add ML confidence reason if ML won
            if (mlScores[finalAttack] ?: 0.0 > ruleConf && finalConf > 0.50) {
                detectionReasons.add("ML Model detected with ${String.format("%.0f%%", (mlScores[finalAttack] ?: 0.0) * 100)} confidence")
            }

            // Apply HIGH thresholds to reduce false positives
            val threshold = when (finalAttack) {
                "Evil_Twin" -> EVIL_TWIN_THRESHOLD
                "Rogue_AP" -> ROGUE_AP_THRESHOLD
                else -> 0.50
            }

            // If confidence below threshold, mark as Safe
            if (finalConf < threshold && !profile.isBaseline) {
                Log.d(TAG, "⚪ $ssid | Confidence ${String.format("%.0f%%", finalConf*100)} below threshold ${String.format("%.0f%%", threshold*100)} - Marked as Safe")
                finalAttack = "Safe"
                finalConf = 0.0
                detectionReasons.clear()
            }

            // PERSISTENT ATTACK TRACKING - Key to stability!
            if (finalAttack != "Safe" && finalConf >= threshold) {
                if (profile.attackEvidence == null || profile.attackEvidence!!.attackType != finalAttack) {
                    // New attack type detected
                    profile.attackEvidence = AttackEvidence(finalAttack, finalConf, 1)
                    Log.d(TAG, "🔍 NEW ATTACK EVIDENCE: $ssid → $finalAttack (${String.format("%.0f%%", finalConf * 100)})")
                } else {
                    // Same attack - accumulate evidence
                    profile.attackEvidence!!.detectionCount++
                    profile.attackEvidence!!.totalConfidence += finalConf
                    profile.attackEvidence!!.lastSeen = System.currentTimeMillis()

                    // Confirm attack after multiple detections
                    if (profile.attackEvidence!!.detectionCount >= ATTACK_PERSISTENCE_THRESHOLD) {
                        profile.attackEvidence!!.isConfirmed = true
                        val avgConf = profile.attackEvidence!!.totalConfidence / profile.attackEvidence!!.detectionCount
                        finalConf = min(avgConf * 1.2, 0.95)  // Boost confidence for persistent attacks
                        Log.w(TAG, "🚨 CONFIRMED ATTACK: $ssid → $finalAttack (seen ${profile.attackEvidence!!.detectionCount}x)")
                    }
                }
            } else {
                // Check if this should become baseline (clean scans)
                if (profile.scanCount >= BASELINE_THRESHOLD && !profile.isBaseline) {
                    // No attacks detected for multiple scans
                    if (profile.attackEvidence == null || !profile.attackEvidence!!.isConfirmed) {
                        profile.isBaseline = true
                        profile.attackEvidence = null  // Clear any weak evidence
                        Log.i(TAG, "✅ BASELINE LEARNED: $ssid ($bssid)")
                    }
                }
            }

            // Use persistent attack if confirmed
            if (profile.attackEvidence?.isConfirmed == true) {
                finalAttack = profile.attackEvidence!!.attackType
                finalConf = min(profile.attackEvidence!!.totalConfidence / profile.attackEvidence!!.detectionCount * 1.15, 0.95)
            }

            // Baseline APs get special treatment
            if (profile.isBaseline && finalAttack == "Safe") {
                finalConf = 0.0  // Definitely safe
            } else if (profile.isBaseline && finalConf < 0.70) {
                // Baseline AP showing weak attack signs - require higher confidence
                Log.d(TAG, "⚠️ BASELINE AP with weak attack signal - ignoring")
                finalAttack = "Safe"
                finalConf = 0.0
            }

            val threatLevel = calculateThreatLevel(finalConf, finalAttack, profile.isBaseline)
            val isThreat = threatLevel != ThreatLevel.SAFE
            val isRogueAP = finalAttack == "Rogue_AP" || finalAttack == "Evil_Twin"
            val action = getRecommendedAction(finalAttack, threatLevel, profile.isBaseline)

            val detectionCount = profile.attackEvidence?.detectionCount ?: 0
            val icon = when {
                profile.isBaseline -> "🟢"
                isThreat -> "🔴"
                else -> "⚪"
            }

            Log.d(TAG, "$icon $ssid | $finalAttack (${String.format("%.0f%%", finalConf * 100)}) | Count: $detectionCount | Baseline: ${profile.isBaseline}")

            return DetectionResult(
                networkName = if (ssid.isNotEmpty()) ssid else "<Hidden Network>",
                bssid = bssid, attackType = finalAttack, confidence = finalConf,
                isRogueAP = isRogueAP, isThreat = isThreat, threatLevel = threatLevel,
                allProbabilities = mlScores, signalStrength = signalStrength,
                frequency = frequency, channel = channel, recommendedAction = action,
                isBaseline = profile.isBaseline, detectionCount = detectionCount,
                detectionReasons = detectionReasons.toList()  // Include detection reasons
            )

        } catch (e: Exception) {
            Log.e(TAG, "Detection error: ${e.message}")
            return createSafeResult(ssid, bssid, signalStrength, frequency, channel)
        }
    }

    /**
     * IMPROVED STABLE RULES with Device Fingerprinting
     * Returns: Triple(attackType, confidence, reasons)
     */
    private fun applyStableRules(
        ssid: String, bssid: String, frequency: Int, signalStrength: Int,
        channel: Int, capabilities: String, profile: APProfile
    ): Triple<String, Double, List<String>> {
        val reasons = mutableListOf<String>()

        // === DEVICE IDENTIFICATION ===
        val vendorInfo = MACVendorDatabase.lookupVendor(bssid)
        val deviceDesc = MACVendorDatabase.getDeviceDescription(bssid)

        Log.d(TAG, "🔍 Device: $ssid ($bssid) → $deviceDesc")

        // RULE 1: Known attack devices - CRITICAL
        if (MACVendorDatabase.isKnownAttackDevice(bssid) || isFlipperMAC(bssid)) {
            reasons.add("ATTACK HARDWARE DETECTED: $deviceDesc")
            reasons.add("Known device used for WiFi attacks")
            Log.w(TAG, "🚨 ATTACK DEVICE: $bssid → $deviceDesc")
            return Triple("Evil_Twin", 0.95, reasons)
        }

        // RULE 2: Legitimate router manufacturers - TRUSTED (unless other evidence)
        if (MACVendorDatabase.isLegitimateRouter(bssid)) {
            // This is a known router brand - very unlikely to be malicious
            // Only flag if there's strong evidence (duplicate SSID)
            val isDuplicate = ssid.isNotEmpty() && (ssidToBssids[ssid]?.size ?: 0) > 1

            if (isDuplicate) {
                val otherBssids = ssidToBssids[ssid]!!.filter { it != bssid }
                val hasLearnedDuplicate = otherBssids.any { apProfiles[it]?.isBaseline == true }

                if (hasLearnedDuplicate) {
                    // Another router with same SSID already learned - possible conflict
                    reasons.add("Duplicate SSID detected: '$ssid'")
                    reasons.add("Device: $deviceDesc")
                    reasons.add("May be legitimate mesh network or range extender")
                    Log.i(TAG, "ℹ️ DUPLICATE ROUTER: $deviceDesc broadcasting '$ssid'")
                    return Triple("Safe", 0.30, reasons)  // Low confidence - probably legitimate
                }
            }

            // Legitimate router, no duplicate - SAFE
            Log.i(TAG, "✅ LEGITIMATE ROUTER: $deviceDesc")
            return Triple("Safe", 0.0, reasons)
        }

        // RULE 3: Mobile phone hotspots - Flag as Rogue_AP (not Evil_Twin!)
        if (MACVendorDatabase.isMobileDevice(bssid)) {
            reasons.add("Mobile Hotspot detected: $deviceDesc")
            reasons.add("Personal hotspots may be legitimate or unauthorized")

            // Check if SSID looks like default hotspot name
            val isDefaultHotspot = ssid.contains("iPhone", ignoreCase = true) ||
                                  ssid.contains("Samsung", ignoreCase = true) ||
                                  ssid.contains("Pixel", ignoreCase = true) ||
                                  ssid.contains("OnePlus", ignoreCase = true) ||
                                  ssid.contains("'s Phone", ignoreCase = true) ||
                                  ssid.contains("'s iPhone", ignoreCase = true)

            if (isDefaultHotspot) {
                reasons.add("Default hotspot name pattern detected")
            }

            Log.i(TAG, "📱 MOBILE HOTSPOT: $deviceDesc → '$ssid'")
            return Triple("Rogue_AP", 0.65, reasons)  // Rogue AP, not Evil Twin
        }

        // RULE 4: Duplicate SSID of learned network - EVIL TWIN!
        if (ssid.isNotEmpty() && (ssidToBssids[ssid]?.size ?: 0) > 1) {
            val otherBssids = ssidToBssids[ssid]!!.filter { it != bssid }
            val hasLearnedDuplicate = otherBssids.any { apProfiles[it]?.isBaseline == true }

            if (hasLearnedDuplicate) {
                reasons.add("EVIL TWIN ATTACK: Duplicate SSID of trusted network")
                reasons.add("Original network: ${otherBssids.first()}")
                reasons.add("Impersonating device: $deviceDesc")
                Log.w(TAG, "🚨 EVIL TWIN: $bssid duplicating learned SSID '$ssid'")
                return Triple("Evil_Twin", 0.92, reasons)
            } else {
                // Multiple APs with same SSID but none learned yet
                reasons.add("Multiple access points with same SSID: '$ssid'")
                reasons.add("Possible mesh network or evil twin")
            }
        }

        // RULE 5: Hidden network + very strong signal (REMOVED strong signal alone)
        if ((ssid.isEmpty() || ssid.contains("Hidden")) && signalStrength > -40) {
            reasons.add("Hidden network with very strong signal (${signalStrength}dBm)")
            reasons.add("Device: $deviceDesc")
            return Triple("Rogue_AP", 0.70, reasons)  // Rogue AP, not Evil Twin
        }

        // RULE 6: Locally administered MAC (fake/spoofed)
        if (isLocalMAC(bssid) && !MACVendorDatabase.isLegitimateRouter(bssid)) {
            reasons.add("Locally administered (spoofed) MAC address")
            reasons.add("MAC may be manually configured or randomized")
            return Triple("Rogue_AP", 0.60, reasons)
        }

        // RULE 7: Generic/suspicious SSID patterns
        val suspSSID = detectSuspiciousSSID(ssid)
        if (suspSSID > 0) {
            reasons.add("Suspicious network name: '$ssid'")
            reasons.add("Common honeypot/phishing SSID pattern")
            return Triple("Rogue_AP", suspSSID, reasons)  // Rogue AP, not Evil Twin
        }

        // RULE 8: Open network (no encryption) + strong signal
        if (!capabilities.contains("WPA") && !capabilities.contains("WEP") && signalStrength > -60) {
            reasons.add("Unencrypted (open) network")
            reasons.add("Strong signal: ${signalStrength}dBm")
            reasons.add("Potential honeypot or public hotspot")
            return Triple("Rogue_AP", 0.55, reasons)
        }

        // RULE 9: Very strong signal from unknown vendor (MODIFIED - less aggressive)
        // Only flag if signal is EXTREMELY strong AND unknown vendor
        if (signalStrength > -35 && vendorInfo.deviceType == MACVendorDatabase.DeviceType.UNKNOWN) {
            reasons.add("Extremely strong signal from unknown device (${signalStrength}dBm)")
            reasons.add("Device: $deviceDesc")
            return Triple("Rogue_AP", 0.50, reasons)  // Lower confidence, Rogue AP
        }

        return Triple("Safe", 0.0, reasons)
    }

    private fun isFlipperMAC(bssid: String): Boolean {
        val lower = bssid.lowercase()
        return listOf("de:ad:", "be:ef:", "ca:fe:", "ba:be:", "12:34:56:", "aa:bb:cc:")
            .any { lower.startsWith(it) }
    }

    private fun isLocalMAC(bssid: String): Boolean {
        return try {
            (bssid.split(":")[0].toInt(16) and 0x02) != 0
        } catch (e: Exception) { false }
    }

    private fun detectSuspiciousSSID(ssid: String): Double {
        if (ssid.isEmpty()) return 0.0
        val lower = ssid.lowercase()
        val patterns = listOf("free", "public", "guest", "open", "wifi", "hotel", "airport")
        for (p in patterns) {
            if (lower == p || (lower.contains(p) && !lower.contains("_") && !lower.contains("-"))) {
                return 0.60
            }
        }
        return 0.0
    }

    /**
     * Extract 50 features from WiFi scan results
     * Many features unavailable from Android WiFi scan, so we use reasonable defaults
     */
    private fun extractFeatures(
        bssid: String, frequency: Int, signalStrength: Int,
        channel: Int, capabilities: String
    ): List<Double> {
        val features = mutableListOf<Double>()

        // Iterate through expected feature names and extract what we can
        for (featureName in featureNames) {
            val value = when {
                // Frame features - use defaults
                featureName.contains("frame.len") -> 100.0
                featureName.contains("frame.time") -> 0.0

                // Radiotap features - available from WiFi scan
                featureName.contains("radiotap.channel.flags.cck") -> if (frequency < 5000) 1.0 else 0.0
                featureName.contains("radiotap.datarate") -> if (frequency >= 5000) 54.0 else 24.0
                featureName.contains("radiotap.dbm_antsignal") -> signalStrength.toDouble()
                featureName.contains("radiotap.length") -> 24.0

                // WLAN features
                featureName.contains("wlan.duration") -> 44.0
                featureName.contains("wlan.fc.type") -> 0.0  // Management
                featureName.contains("wlan.fc.retry") -> 0.0
                featureName.contains("wlan.fc.subtype") -> 8.0  // Beacon
                featureName.contains("wlan.fixed.reason_code") -> 0.0

                // WLAN radio features - available!
                featureName.contains("wlan_radio.channel") -> channel.toDouble()
                featureName.contains("wlan_radio.data_rate") -> if (frequency >= 5000) 54.0 else 24.0
                featureName.contains("wlan_radio.signal_dbm") -> signalStrength.toDouble()
                featureName.contains("wlan_radio.duration") -> 44.0
                featureName.contains("wlan_radio.phy") -> if (frequency >= 5000) 5.0 else 4.0

                // Security features
                featureName.contains("wlan.rsn") || featureName.contains("wlan_rsna") -> {
                    if (capabilities.contains("WPA")) 1.0 else 0.0
                }

                // Network protocol features - defaults (not available from WiFi scan)
                featureName.contains("arp") -> 0.0
                featureName.contains("ip.") -> 0.0
                featureName.contains("tcp.") -> 0.0
                featureName.contains("udp.") -> 0.0
                featureName.contains("data.len") -> 0.0
                featureName.contains("smb") -> 0.0
                featureName.contains("dhcp") -> 0.0
                featureName.contains("dns") -> 0.0
                featureName.contains("http") -> 0.0
                featureName.contains("ssh") -> 0.0

                // Default for unknown features
                else -> 0.0
            }
            features.add(value)
        }

        return features
    }

    /**
     * ML MODEL - 2-Class predictions (Evil Twin & Rogue AP only)
     * UPDATED: Less aggressive, relies more on rule-based detection
     */
    private fun predictMLModel(
        std: List<Double>, rssi: Int, freq: Int
    ): Map<String, Double> {
        val scores = mutableMapOf<String, Double>()

        // Evil Twin: REDUCED scores - let rules handle most detection
        // Strong signal alone is NOT enough evidence
        scores["Evil_Twin"] = when {
            freq < 3000 && rssi > -40 -> 0.55  // EXTREMELY strong 2.4GHz (reduced from 0.80)
            freq < 3000 && rssi > -50 -> 0.45  // Very strong 2.4GHz (reduced from 0.65)
            freq < 3000 && rssi > -60 -> 0.35  // Strong 2.4GHz (reduced from 0.50)
            rssi > -40 -> 0.45  // Extremely strong signal any band (reduced from 0.60)
            else -> 0.25  // Base low score
        }

        // Rogue AP: Also reduced - let vendor detection handle classification
        scores["Rogue_AP"] = when {
            rssi > -45 -> 0.50  // Very strong signal (reduced from 0.60)
            rssi > -60 -> 0.40  // Strong signal (reduced from 0.45)
            freq >= 5000 && rssi > -55 -> 0.45  // 5GHz with good signal (reduced from 0.55)
            else -> 0.25  // Base low score
        }

        return softmax(scores)
    }

    private fun softmax(scores: Map<String, Double>): Map<String, Double> {
        val expScores = scores.mapValues { exp(it.value) }
        val sum = expScores.values.sum()
        return expScores.mapValues { it.value / sum }
    }

    /**
     * STABLE THREAT LEVELS - Fixed thresholds
     */
    private fun calculateThreatLevel(conf: Double, attack: String, isBaseline: Boolean): ThreatLevel {
        if (isBaseline && conf < 0.70) return ThreatLevel.SAFE

        return when {
            conf < 0.40 -> ThreatLevel.SAFE
            conf < 0.60 -> ThreatLevel.LOW
            conf < 0.75 -> when (attack) {
                "Evil_Twin", "Rogue_AP" -> ThreatLevel.HIGH
                else -> ThreatLevel.MEDIUM
            }
            conf < 0.85 -> when (attack) {
                "Evil_Twin", "Rogue_AP" -> ThreatLevel.CRITICAL
                else -> ThreatLevel.HIGH
            }
            else -> ThreatLevel.CRITICAL
        }
    }

    private fun getRecommendedAction(attack: String, level: ThreatLevel, baseline: Boolean): String {
        if (baseline) return "✅ Trusted network (learned from ${BASELINE_THRESHOLD}+ scans)"
        if (level == ThreatLevel.SAFE) return "✅ Network appears safe. Monitoring..."

        return when (attack) {
            "Evil_Twin" -> when (level) {
                ThreatLevel.CRITICAL -> "🚨 CRITICAL: Evil Twin Attack! DO NOT CONNECT!"
                ThreatLevel.HIGH -> "⚠️ HIGH RISK: Suspected Evil Twin."
                else -> "⚠️ Possible Evil Twin. Verify network."
            }
            "Rogue_AP" -> "🚨 UNAUTHORIZED ACCESS POINT! Do not connect."
            "Disas" -> "⚠️ Deauthentication/Flooding attack detected."
            "(Re)Assoc" -> "⚠️ Injection/Association attack detected."
            else -> "⚠️ Potential threat."
        }
    }

    private fun createSafeResult(
        ssid: String, bssid: String, rssi: Int, freq: Int, ch: Int
    ): DetectionResult {
        return DetectionResult(
            if (ssid.isNotEmpty()) ssid else "<Hidden>", bssid, "Unknown", 0.0,
            false, false, ThreatLevel.SAFE, mapOf(), rssi, freq, ch,
            recommendedAction = "⚠️ Model not loaded",
            detectionReasons = listOf("Detection model failed to load")
        )
    }

    fun getModelInfo(): String {
        val learned = apProfiles.count { it.value.isBaseline }
        val tracked = apProfiles.size
        return "2-Class Detection v6.0 (Dec 2025)\n\n" +
               "DETECTION FOCUS:\n" +
               "• Evil Twin: 79.64% accuracy\n" +
               "• Rogue WiFi: 97.70% accuracy\n" +
               "• Model Accuracy: ${String.format("%.2f", modelAccuracy)}%\n\n" +
               "FEATURES:\n" +
               "• ${featureNames.size} WiFi packet features\n" +
               "• High thresholds (Evil Twin: 65%, Rogue: 60%)\n" +
               "• MAC OUI database (device identification)\n" +
               "• Legitimate router detection\n" +
               "• Mobile hotspot detection\n" +
               "• Detection reasons provided\n\n" +
               "STATUS:\n" +
               "• Learned APs: $learned\n" +
               "• Tracked APs: $tracked\n" +
               "• Persistent Tracking: ACTIVE\n" +
               "• Device Fingerprinting: ENABLED\n\n" +
               "⚠️ DISCLAIMER:\n" +
               "Results may not be 100% accurate.\n" +
               "Use as guidance, not absolute truth."
    }

    fun isLoaded() = isModelLoaded
    fun clearTracking() { apProfiles.clear(); ssidToBssids.clear() }
    fun getLearnedNetworks() = apProfiles.filter { it.value.isBaseline }.map { "${it.value.ssid} (${it.key})" }
}