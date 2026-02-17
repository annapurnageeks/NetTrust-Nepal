package com.apil.nettrust_nepal

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.apil.nettrust_nepal.databinding.ItemDetectionBinding

class DetectionAdapter(private var detections: List<RogueAPDetector.DetectionResult>) :
    RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {

    inner class DetectionViewHolder(private val binding: ItemDetectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(detection: RogueAPDetector.DetectionResult) {
            // Basic Info
            binding.tvNetworkName.text = detection.networkName
            binding.tvBssid.text = "${detection.bssid} • ${detection.signalStrength} dBm • Ch ${detection.channel} (${detection.frequency} MHz)"
            binding.tvTimestamp.text = "Detected: ${detection.timestamp}"

            // Show/Hide Rogue AP Badge
            binding.badgeRogueAP.visibility = if (detection.isRogueAP) View.VISIBLE else View.GONE

            // Threat Information
            if (detection.attackType != "Safe") {
                binding.layoutThreatInfo.visibility = View.VISIBLE
                binding.tvAttackType.text = "Attack: ${detection.attackType}"
                binding.tvConfidence.text = "Confidence: ${String.format("%.1f%%", detection.confidence * 100)}"
                binding.tvThreatLevel.text = detection.threatLevel.toString()

                // Show recommendation
                binding.tvRecommendation.visibility = View.VISIBLE
                binding.tvRecommendation.text = getRecommendation(detection.threatLevel)

                // Set threat colors
                val bgColor = when (detection.threatLevel) {
                    RogueAPDetector.ThreatLevel.CRITICAL -> R.color.threat_critical
                    RogueAPDetector.ThreatLevel.HIGH -> R.color.threat_high
                    RogueAPDetector.ThreatLevel.MEDIUM -> R.color.threat_medium
                    RogueAPDetector.ThreatLevel.LOW -> R.color.threat_low
                    else -> R.color.threat_safe
                }

                binding.layoutThreatInfo.setBackgroundColor(
                    ContextCompat.getColor(binding.root.context, bgColor)
                )

                // Pulse animation for critical threats
                if (detection.threatLevel == RogueAPDetector.ThreatLevel.CRITICAL) {
                    val pulse = ObjectAnimator.ofFloat(binding.layoutThreatInfo, "alpha", 1f, 0.7f, 1f)
                    pulse.duration = 1000
                    pulse.repeatCount = ObjectAnimator.INFINITE
                    pulse.start()
                }
            } else {
                binding.layoutThreatInfo.visibility = View.GONE
            }

            // Probabilities - format for 2-class model (Evil Twin & Rogue AP only)
            val probText = buildString {
                if (detection.allProbabilities.isNotEmpty()) {
                    detection.allProbabilities.forEach { (attackType, prob) ->
                        append("• $attackType: ${String.format("%.1f%%", prob * 100)}\n")
                    }
                } else {
                    // Fallback if probabilities not available
                    append("Primary Detection: ${detection.attackType}\n")
                    append("Confidence: ${String.format("%.1f%%", detection.confidence * 100)}\n")
                    append("Threat Level: ${detection.threatLevel}")
                }
            }
            binding.tvProbabilities.text = probText.trim()

            // Detection Reasons
            if (detection.detectionReasons.isNotEmpty()) {
                binding.tvDetectionReasonsLabel.visibility = View.VISIBLE
                binding.tvDetectionReasons.visibility = View.VISIBLE
                val reasonsText = buildString {
                    detection.detectionReasons.forEach { reason ->
                        append("• $reason\n")
                    }
                }
                binding.tvDetectionReasons.text = reasonsText.trim()
            } else {
                binding.tvDetectionReasonsLabel.visibility = View.GONE
                binding.tvDetectionReasons.visibility = View.GONE
            }

            // Show disclaimer when threat is detected
            binding.tvDisclaimer.visibility = if (detection.isThreat) View.VISIBLE else View.GONE

            // Expand/Collapse functionality
            var isExpanded = false
            binding.root.setOnClickListener {
                isExpanded = !isExpanded
                binding.layoutExpandedDetails.visibility = if (isExpanded) View.VISIBLE else View.GONE
                binding.ivExpandIcon.rotation = if (isExpanded) 180f else 0f
            }
        }

        private fun getRecommendation(threatLevel: RogueAPDetector.ThreatLevel): String {
            return when (threatLevel) {
                RogueAPDetector.ThreatLevel.CRITICAL -> "⚠️ Do not connect! This network is extremely dangerous."
                RogueAPDetector.ThreatLevel.HIGH -> "⚠️ Avoid connecting to this network."
                RogueAPDetector.ThreatLevel.MEDIUM -> "⚠️ Use caution if connecting to this network."
                RogueAPDetector.ThreatLevel.LOW -> "ℹ️ Monitor this network for suspicious activity."
                else -> "✅ This network appears safe."
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val binding = ItemDetectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DetectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        holder.bind(detections[position])
    }

    override fun getItemCount(): Int = detections.size

    fun updateDetections(newDetections: List<RogueAPDetector.DetectionResult>) {
        detections = newDetections
        notifyDataSetChanged()
    }
}