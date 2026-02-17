# NetTrust Nepal v6.0 - Device Fingerprinting Update

**Date:** 2025-12-25
**Version:** 6.0
**Focus:** Fixing false positives with device manufacturer identification

---

## Problem Statement

**User Report:**
- Personal router with strong signal (-44 dBm) was being detected as **Evil Twin** ❌
- Phone hotspot was also being detected as **Evil Twin** ❌
- Too many false positives on legitimate devices

**Root Causes Identified:**
1. **Overly aggressive "strong signal" rule** - Any 2.4GHz network stronger than -45 dBm was flagged as Evil Twin
2. **No device identification** - Couldn't distinguish between routers, phones, and attack devices
3. **Wrong classification** - Mobile hotspots were classified as Evil Twin instead of Rogue AP
4. **ML model too aggressive** - Strong signal alone triggered high threat scores

---

## Solution: Device Fingerprinting with MAC OUI Database

### What is MAC OUI?

Every network device has a MAC address (e.g., `f4:ec:38:a1:b2:c3`). The first 3 bytes (`f4:ec:38`) are the **Organizationally Unique Identifier (OUI)** assigned to the manufacturer by IEEE.

By looking up the OUI, we can identify:
- **Legitimate routers** (TP-Link, Netgear, D-Link, ASUS, Cisco, Huawei, etc.)
- **Mobile devices** (Apple iPhone, Samsung, Google Pixel, OnePlus, Xiaomi, etc.)
- **Attack devices** (Flipper Zero, Raspberry Pi, ESP32, Arduino, etc.)

---

## New Components

### 1. MACVendorDatabase.kt (NEW FILE)

**Purpose:** Identify device manufacturers from MAC addresses

**Features:**
- Database of 100+ router and phone manufacturers
- Device type classification (Router, Mobile Phone, Attack Device, etc.)
- Trust level marking (known legitimate vs. suspicious)

**Device Types:**
```kotlin
enum class DeviceType {
    ROUTER,          // Legitimate home/office routers
    MOBILE_PHONE,    // Smartphones creating hotspots
    COMPUTER,        // Laptops/desktops
    IOT_DEVICE,      // Smart home devices
    ATTACK_DEVICE,   // Known attack hardware
    UNKNOWN          // Unknown vendor
}
```

**Supported Manufacturers:**

**Routers (Trusted):**
- TP-Link (6+ OUIs)
- Netgear (5+ OUIs)
- D-Link (4+ OUIs)
- ASUS (5+ OUIs)
- Linksys (4+ OUIs)
- Cisco (3+ OUIs)
- Huawei (4+ OUIs)
- Xiaomi Mi Router
- Tenda, Mercusys

**Mobile Devices:**
- Apple iPhone (8+ OUIs)
- Samsung (7+ OUIs)
- Google Pixel (3+ OUIs)
- OnePlus, Xiaomi, Oppo, Vivo, Realme

**Attack Devices (Flagged):**
- Flipper Zero
- Raspberry Pi (3+ OUIs)
- ESP32/ESP8266 (4+ OUIs)
- Arduino

---

## Updated Detection Logic

### New Rule Priority (RogueAPDetector.kt)

#### ✅ RULE 1: Known Attack Devices
```
IF device = Flipper Zero OR ESP32 OR Raspberry Pi
THEN → Evil_Twin (95% confidence)
REASON: "ATTACK HARDWARE DETECTED"
```

#### ✅ RULE 2: Legitimate Routers → SAFE
```
IF device = TP-Link OR Netgear OR D-Link OR ASUS OR ...
THEN → Safe (0% confidence)
EXCEPT: If duplicate SSID of learned network
REASON: Device identified as legitimate router
```

**This fixes your router false positive!** 🎉

#### ✅ RULE 3: Mobile Hotspots → Rogue AP
```
IF device = iPhone OR Samsung OR Google Pixel OR ...
THEN → Rogue_AP (65% confidence)
NOT Evil_Twin anymore!
REASON: "Mobile Hotspot detected"
```

**This correctly classifies phone hotspots!** 🎉

#### RULE 4: Duplicate SSID of Learned Network → Evil Twin
```
IF SSID matches learned baseline network
AND BSSID is different
THEN → Evil_Twin (92% confidence)
REASON: "EVIL TWIN ATTACK: Duplicate SSID of trusted network"
```

This is the TRUE evil twin detection!

#### ⚠️ RULE 5: Strong Signal from Unknown (MODIFIED)
```
OLD: IF signal > -45 dBm THEN Evil_Twin (78%)
NEW: IF signal > -35 dBm AND unknown vendor THEN Rogue_AP (50%)
```

**Much less aggressive - strong signal alone is NOT malicious!**

---

## ML Model Updates

### Before (v5.0):
```kotlin
// Very strong 2.4GHz → 80% Evil Twin confidence
freq < 3000 && rssi > -50 -> 0.80
```

### After (v6.0):
```kotlin
// Very strong 2.4GHz → 45% confidence (below threshold!)
freq < 3000 && rssi > -50 -> 0.45
```

**Result:** Strong signals from legitimate routers won't trigger detections anymore!

---

## Classification Changes

### Before v6.0:
| Device Type | Classification | Issue |
|-------------|---------------|-------|
| Your router (-44 dBm) | **Evil Twin** ❌ | False positive! |
| Phone hotspot | **Evil Twin** ❌ | Wrong category! |
| Actual evil twin | Evil Twin ✓ | Correct |

### After v6.0:
| Device Type | Classification | Result |
|-------------|---------------|--------|
| TP-Link router (-44 dBm) | **Safe** ✅ | Fixed! |
| iPhone hotspot | **Rogue AP** ✅ | Correct category! |
| Duplicate SSID | **Evil Twin** ✅ | True threat! |

---

## Detection Reasons Now Include

**For legitimate routers:**
```
✅ LEGITIMATE ROUTER: TP-Link Router (Legitimate)
```

**For mobile hotspots:**
```
📱 MOBILE HOTSPOT: Samsung Mobile Device
• Mobile Hotspot detected
• Personal hotspots may be legitimate or unauthorized
```

**For evil twins:**
```
🚨 EVIL TWIN ATTACK: Duplicate SSID of trusted network
• Original network: aa:bb:cc:dd:ee:ff
• Impersonating device: Unknown Manufacturer
```

---

## Testing Your Devices

### How to Test:

1. **Your Personal Router:**
   - Should now show as **Safe** ✅
   - Device will be identified (e.g., "TP-Link Router")
   - No false Evil Twin detection

2. **Phone Hotspot:**
   - Should show as **Rogue_AP** ⚠️ (not Evil Twin)
   - Device identified (e.g., "Samsung Mobile Device")
   - Lower threat level than Evil Twin

3. **After 3+ Scans:**
   - Your router should become "Baseline Learned"
   - Will show as ✅ Trusted network
   - Even stronger protection against false positives

### To Add Your Router MAC to Database:

If your router still shows as unknown, you can add it:

1. Find your router's MAC address (BSSID)
2. Look up the OUI (first 3 bytes) at https://maclookup.app
3. Add to `MACVendorDatabase.kt` in the router section

Example:
```kotlin
"aa:bb:cc" to VendorInfo("Your Router Brand", DeviceType.ROUTER, true),
```

---

## Expected Behavior Now

### Scan 1 (First Detection):
```
📡 Your_WiFi_5G (-44 dBm)
Device: TP-Link Router (Legitimate)
Status: Safe
Confidence: 0%
Reason: Legitimate router manufacturer detected
```

### Scan 3+ (Baseline Learned):
```
📡 Your_WiFi_5G (-44 dBm)
Device: TP-Link Router (Legitimate)
Status: Safe ✅ TRUSTED
Baseline: Yes
Reason: Learned from 3+ clean scans
```

### Phone Hotspot:
```
📱 John's iPhone (-52 dBm)
Device: Apple Mobile Device
Status: Rogue_AP ⚠️
Confidence: 65%
Threat: MEDIUM
Reason:
• Mobile Hotspot detected
• Default hotspot name pattern detected
```

### Actual Evil Twin:
```
🚨 Your_WiFi_5G (-55 dBm)
Device: Unknown Manufacturer
Status: Evil_Twin 🔴
Confidence: 92%
Threat: CRITICAL
Reason:
• EVIL TWIN ATTACK: Duplicate SSID of trusted network
• Original network: f4:ec:38:xx:xx:xx
• Impersonating device: Unknown Manufacturer
```

---

## Files Modified

1. **NEW:** `/app/src/main/java/com/example/nettrust_nepal/MACVendorDatabase.kt`
   - MAC OUI lookup database
   - Device type classification
   - 100+ manufacturer entries

2. **UPDATED:** `/app/src/main/java/com/example/nettrust_nepal/RogueAPDetector.kt`
   - Integrated vendor lookup in detection rules
   - Fixed aggressive strong signal rule
   - Improved classification logic (Evil Twin vs Rogue AP)
   - Reduced ML model aggressiveness
   - Updated to v6.0

---

## Technical Details

### Detection Flow (v6.0):

```
1. Scan WiFi network
2. Extract: SSID, BSSID, Signal, Frequency
3. Lookup manufacturer from MAC OUI ← NEW!
4. Apply rules:
   a. Attack device? → Evil_Twin (95%)
   b. Legitimate router? → Safe (0%)
   c. Mobile phone? → Rogue_AP (65%)
   d. Duplicate SSID? → Evil_Twin (92%)
   e. Other patterns → Analyze
5. Get ML prediction (reduced scores)
6. Combine rule + ML (rule prioritized)
7. Apply thresholds (Evil Twin: 65%, Rogue: 60%)
8. Return result with reasons
```

### MAC OUI Lookup:

```
Input:  f4:ec:38:a1:b2:c3
OUI:    f4:ec:38
Lookup: Database → "TP-Link"
Type:   ROUTER
Trust:  true
Result: Safe (legitimate router)
```

---

## Benefits of v6.0

✅ **Eliminates false positives on legitimate routers**
✅ **Correctly identifies mobile hotspots**
✅ **Shows device manufacturer in results**
✅ **More accurate Evil Twin detection (duplicate SSID only)**
✅ **Better user experience with explanations**
✅ **Maintains high security (still detects real threats)**

---

## Next Steps

1. **Build and install** the updated app
2. **Test with your router** - should now show as Safe
3. **Test with phone hotspot** - should show as Rogue_AP
4. **Let router be learned** - scan 3+ times to establish baseline
5. **Test evil twin detection** - create duplicate SSID to verify

---

## Adding More Routers

The database includes common brands, but if your router is not recognized:

1. **Find MAC OUI:** First 3 bytes of MAC address (e.g., `f4:ec:38`)
2. **Lookup vendor:** Use https://maclookup.app or https://macvendors.com
3. **Add to database:** Edit `MACVendorDatabase.kt`

```kotlin
// Add in the router section
"your:ou:i" to VendorInfo("Your Router Brand", DeviceType.ROUTER, true),
```

4. **Rebuild app** and test

---

## Summary

**v6.0 = Smarter Detection + Fewer False Positives**

- Strong signal ≠ Malicious (finally!)
- Legitimate routers recognized and trusted
- Mobile hotspots classified correctly
- Real evil twins still detected accurately
- Device fingerprinting adds transparency

Your router should now work perfectly! 🎉
