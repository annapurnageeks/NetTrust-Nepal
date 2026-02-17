package com.apil.nettrust_nepal

import android.util.Log

/**
 * MAC OUI (Organizationally Unique Identifier) Database
 * Identifies device manufacturers from MAC addresses to improve detection accuracy
 *
 * This helps distinguish between:
 * - Legitimate routers (TP-Link, Netgear, D-Link, etc.)
 * - Mobile devices creating hotspots (Apple, Samsung, Google, etc.)
 * - Attack devices (Flipper Zero, ESP32, Raspberry Pi, etc.)
 */
object MACVendorDatabase {

    private const val TAG = "MACVendor"

    enum class DeviceType {
        ROUTER,              // Legitimate home/office routers
        MOBILE_PHONE,        // Smartphones that can create hotspots
        COMPUTER,            // Laptops/desktops
        IOT_DEVICE,          // Smart home devices
        ATTACK_DEVICE,       // Known attack hardware
        UNKNOWN              // Unknown vendor
    }

    data class VendorInfo(
        val vendor: String,
        val deviceType: DeviceType,
        val isTrusted: Boolean  // Known legitimate manufacturer
    )

    // MAC OUI to Vendor mapping (first 6 characters: XX:XX:XX)
    // Format: "XX:XX:XX" to VendorInfo
    private val ouiDatabase = mapOf(
        // === LEGITIMATE ROUTERS (TRUSTED) ===
        // TP-Link
        "f4:ec:38" to VendorInfo("TP-Link", DeviceType.ROUTER, true),
        "50:c7:bf" to VendorInfo("TP-Link", DeviceType.ROUTER, true),
        "c0:25:e9" to VendorInfo("TP-Link", DeviceType.ROUTER, true),
        "14:cc:20" to VendorInfo("TP-Link", DeviceType.ROUTER, true),
        "b0:4e:26" to VendorInfo("TP-Link", DeviceType.ROUTER, true),
        "ac:84:c6" to VendorInfo("TP-Link", DeviceType.ROUTER, true),

        // Netgear
        "a0:40:a0" to VendorInfo("Netgear", DeviceType.ROUTER, true),
        "e0:46:9a" to VendorInfo("Netgear", DeviceType.ROUTER, true),
        "20:e5:2a" to VendorInfo("Netgear", DeviceType.ROUTER, true),
        "c0:3f:0e" to VendorInfo("Netgear", DeviceType.ROUTER, true),
        "9c:3d:cf" to VendorInfo("Netgear", DeviceType.ROUTER, true),

        // D-Link
        "14:d6:4d" to VendorInfo("D-Link", DeviceType.ROUTER, true),
        "b8:a3:86" to VendorInfo("D-Link", DeviceType.ROUTER, true),
        "c8:be:19" to VendorInfo("D-Link", DeviceType.ROUTER, true),
        "cc:b2:55" to VendorInfo("D-Link", DeviceType.ROUTER, true),

        // ASUS
        "04:d4:c4" to VendorInfo("ASUS", DeviceType.ROUTER, true),
        "10:c3:7b" to VendorInfo("ASUS", DeviceType.ROUTER, true),
        "38:2c:4a" to VendorInfo("ASUS", DeviceType.ROUTER, true),
        "f0:79:59" to VendorInfo("ASUS", DeviceType.ROUTER, true),
        "2c:fd:a1" to VendorInfo("ASUS", DeviceType.ROUTER, true),

        // Linksys
        "00:14:bf" to VendorInfo("Linksys", DeviceType.ROUTER, true),
        "c4:41:1e" to VendorInfo("Linksys", DeviceType.ROUTER, true),
        "48:f8:b3" to VendorInfo("Linksys", DeviceType.ROUTER, true),
        "e8:9f:80" to VendorInfo("Linksys", DeviceType.ROUTER, true),

        // Cisco
        "00:1f:ca" to VendorInfo("Cisco", DeviceType.ROUTER, true),
        "d8:b3:77" to VendorInfo("Cisco", DeviceType.ROUTER, true),
        "6c:41:6a" to VendorInfo("Cisco", DeviceType.ROUTER, true),

        // Huawei
        "00:e0:fc" to VendorInfo("Huawei", DeviceType.ROUTER, true),
        "f8:e7:1e" to VendorInfo("Huawei", DeviceType.ROUTER, true),
        "68:db:f5" to VendorInfo("Huawei", DeviceType.ROUTER, true),
        "00:66:4b" to VendorInfo("Huawei", DeviceType.ROUTER, true),

        // Mercusys (TP-Link budget brand)
        "48:7d:2e" to VendorInfo("Mercusys", DeviceType.ROUTER, true),
        "98:25:4a" to VendorInfo("Mercusys", DeviceType.ROUTER, true),

        // Tenda
        "c8:3a:35" to VendorInfo("Tenda", DeviceType.ROUTER, true),
        "98:fc:11" to VendorInfo("Tenda", DeviceType.ROUTER, true),

        // Xiaomi (Mi Router)
        "64:09:80" to VendorInfo("Xiaomi Router", DeviceType.ROUTER, true),
        "34:ce:00" to VendorInfo("Xiaomi Router", DeviceType.ROUTER, true),
        "78:11:dc" to VendorInfo("Xiaomi Router", DeviceType.ROUTER, true),

        // === MOBILE PHONES (HOTSPOT SOURCES) ===
        // Apple (iPhone hotspots)
        "00:cd:fe" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "3c:06:30" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "a8:5b:78" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "f0:db:e2" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "bc:9f:ef" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "40:98:ad" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "d0:03:4b" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),
        "78:7b:8a" to VendorInfo("Apple", DeviceType.MOBILE_PHONE, false),

        // Samsung
        "08:d4:2b" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),
        "c8:19:f7" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),
        "50:32:75" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),
        "38:aa:3c" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),
        "18:4f:32" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),
        "68:ef:bd" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),
        "a0:82:1f" to VendorInfo("Samsung", DeviceType.MOBILE_PHONE, false),

        // Google (Pixel phones)
        "f4:f5:e8" to VendorInfo("Google", DeviceType.MOBILE_PHONE, false),
        "ac:37:43" to VendorInfo("Google", DeviceType.MOBILE_PHONE, false),
        "88:75:56" to VendorInfo("Google", DeviceType.MOBILE_PHONE, false),

        // OnePlus
        "ac:37:43" to VendorInfo("OnePlus", DeviceType.MOBILE_PHONE, false),
        "8c:88:c0" to VendorInfo("OnePlus", DeviceType.MOBILE_PHONE, false),

        // Xiaomi (phones)
        "34:80:b3" to VendorInfo("Xiaomi Phone", DeviceType.MOBILE_PHONE, false),
        "50:8f:4c" to VendorInfo("Xiaomi Phone", DeviceType.MOBILE_PHONE, false),
        "f8:c3:9e" to VendorInfo("Xiaomi Phone", DeviceType.MOBILE_PHONE, false),

        // Oppo
        "94:7b:e7" to VendorInfo("Oppo", DeviceType.MOBILE_PHONE, false),
        "20:47:ed" to VendorInfo("Oppo", DeviceType.MOBILE_PHONE, false),

        // Vivo
        "30:84:2a" to VendorInfo("Vivo", DeviceType.MOBILE_PHONE, false),
        "f0:72:8c" to VendorInfo("Vivo", DeviceType.MOBILE_PHONE, false),

        // Realme
        "e0:9d:fa" to VendorInfo("Realme", DeviceType.MOBILE_PHONE, false),

        // === ATTACK DEVICES (SUSPICIOUS) ===
        // Flipper Zero (known attack patterns)
        "de:ad:be" to VendorInfo("Flipper Zero", DeviceType.ATTACK_DEVICE, false),

        // Raspberry Pi (can be used for attacks)
        "b8:27:eb" to VendorInfo("Raspberry Pi", DeviceType.ATTACK_DEVICE, false),
        "dc:a6:32" to VendorInfo("Raspberry Pi", DeviceType.ATTACK_DEVICE, false),
        "e4:5f:01" to VendorInfo("Raspberry Pi", DeviceType.ATTACK_DEVICE, false),

        // ESP32/ESP8266 (common in DIY attack devices)
        "24:0a:c4" to VendorInfo("Espressif (ESP32)", DeviceType.ATTACK_DEVICE, false),
        "30:ae:a4" to VendorInfo("Espressif (ESP32)", DeviceType.ATTACK_DEVICE, false),
        "a4:cf:12" to VendorInfo("Espressif (ESP32)", DeviceType.ATTACK_DEVICE, false),
        "ec:fa:bc" to VendorInfo("Espressif (ESP32)", DeviceType.ATTACK_DEVICE, false),

        // Arduino (can be used with WiFi shields)
        "90:a2:da" to VendorInfo("Arduino", DeviceType.ATTACK_DEVICE, false)
    )

    /**
     * Lookup vendor information from MAC address
     */
    fun lookupVendor(bssid: String): VendorInfo {
        try {
            // Extract OUI (first 3 octets)
            val oui = bssid.lowercase().substring(0, 8)  // "xx:xx:xx"

            val info = ouiDatabase[oui]
            if (info != null) {
                Log.d(TAG, "✓ Identified: $bssid → ${info.vendor} (${info.deviceType})")
                return info
            }

            // Check if it's a locally administered MAC (potential fake)
            if (isLocallyAdministered(bssid)) {
                Log.d(TAG, "⚠ Locally administered MAC: $bssid")
                return VendorInfo("Unknown (Local MAC)", DeviceType.UNKNOWN, false)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MAC: $bssid - ${e.message}")
        }

        return VendorInfo("Unknown", DeviceType.UNKNOWN, false)
    }

    /**
     * Check if MAC address is locally administered (manually configured)
     * Bit 1 of first octet = 1 means locally administered
     */
    private fun isLocallyAdministered(bssid: String): Boolean {
        return try {
            val firstOctet = bssid.split(":")[0].toInt(16)
            (firstOctet and 0x02) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if this is a known router manufacturer
     */
    fun isLegitimateRouter(bssid: String): Boolean {
        val vendor = lookupVendor(bssid)
        return vendor.deviceType == DeviceType.ROUTER && vendor.isTrusted
    }

    /**
     * Check if this is a mobile phone (hotspot source)
     */
    fun isMobileDevice(bssid: String): Boolean {
        val vendor = lookupVendor(bssid)
        return vendor.deviceType == DeviceType.MOBILE_PHONE
    }

    /**
     * Check if this is a known attack device
     */
    fun isKnownAttackDevice(bssid: String): Boolean {
        val vendor = lookupVendor(bssid)
        return vendor.deviceType == DeviceType.ATTACK_DEVICE
    }

    /**
     * Get human-readable device description
     */
    fun getDeviceDescription(bssid: String): String {
        val vendor = lookupVendor(bssid)
        return when (vendor.deviceType) {
            DeviceType.ROUTER -> "${vendor.vendor} Router (Legitimate)"
            DeviceType.MOBILE_PHONE -> "${vendor.vendor} Mobile Device"
            DeviceType.ATTACK_DEVICE -> "${vendor.vendor} (SUSPICIOUS HARDWARE)"
            DeviceType.COMPUTER -> "${vendor.vendor} Computer"
            DeviceType.IOT_DEVICE -> "${vendor.vendor} IoT Device"
            DeviceType.UNKNOWN -> if (vendor.vendor != "Unknown") vendor.vendor else "Unknown Manufacturer"
        }
    }
}
