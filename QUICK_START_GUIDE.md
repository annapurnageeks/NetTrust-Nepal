# Quick Start Guide - NetTrust Nepal v6.0

## Testing Your Updated App

### Step 1: Check Your Router's MAC Address

Your router will be identified automatically if it's a common brand:
- TP-Link
- Netgear
- D-Link
- ASUS
- Linksys
- Cisco
- Huawei
- Xiaomi
- Tenda
- Mercusys

### Step 2: Test Detection

1. **Open the app** and start scanning
2. **Look for your router** in the results
3. **Expected result:**
   ```
   Network Name: Your_WiFi_5G
   Signal: -44 dBm
   Device: TP-Link Router (Legitimate)  ← NEW!
   Status: Safe ✅
   Confidence: 0%
   ```

4. **Scan 3 times** to establish baseline
5. **After 3rd scan:**
   ```
   Status: Safe ✅ TRUSTED
   Baseline: Yes
   Recommendation: Trusted network (learned from 3+ scans)
   ```

### Step 3: Test Phone Hotspot

1. **Enable hotspot** on your phone
2. **Scan** with the app
3. **Expected result:**
   ```
   Network Name: John's iPhone
   Device: Apple Mobile Device  ← NEW!
   Status: Rogue_AP ⚠️ (not Evil Twin!)
   Confidence: 65%
   Threat: MEDIUM
   Reasons:
   • Mobile Hotspot detected: Apple Mobile Device
   • Personal hotspots may be legitimate or unauthorized
   ```

### Step 4: If Your Router Shows as "Unknown"

If your router is not recognized:

1. **Find the MAC address** (BSSID) from the app
   Example: `f4:ec:38:a1:b2:c3`

2. **Extract the OUI** (first 3 bytes)
   Example: `f4:ec:38`

3. **Lookup the manufacturer:**
   - Visit: https://maclookup.app
   - Enter: `f4:ec:38`
   - Result: "TP-Link Technologies Co., Ltd."

4. **Add to database:**
   - Open: `MACVendorDatabase.kt`
   - Find the "=== LEGITIMATE ROUTERS (TRUSTED) ===" section
   - Add your router's OUI:
   ```kotlin
   "f4:ec:38" to VendorInfo("TP-Link", DeviceType.ROUTER, true),
   ```

5. **Rebuild and test**

### Step 5: Verify Evil Twin Detection Still Works

To test that real evil twins are still detected:

1. **Note your router's SSID** (e.g., "MyHome_WiFi")
2. **Create a duplicate:**
   - Use another phone/device to create hotspot
   - Name it EXACTLY the same: "MyHome_WiFi"
3. **Scan with the app**
4. **Expected result:**
   ```
   Network Name: MyHome_WiFi
   Device: Samsung Mobile Device
   Status: Evil_Twin 🚨
   Confidence: 92%
   Threat: CRITICAL
   Reasons:
   • EVIL TWIN ATTACK: Duplicate SSID of trusted network
   • Original network: f4:ec:38:xx:xx:xx
   • Impersonating device: Samsung Mobile Device
   ```

---

## Common Scenarios

### ✅ Scenario 1: Your Router (Now Fixed!)

**Before v6.0:**
```
Status: Evil_Twin 🔴 ❌
Confidence: 78%
Reason: Unusually strong signal for 2.4GHz
```

**After v6.0:**
```
Status: Safe ✅
Confidence: 0%
Device: TP-Link Router (Legitimate)
Reason: Legitimate router manufacturer detected
```

### ✅ Scenario 2: Phone Hotspot (Now Correct!)

**Before v6.0:**
```
Status: Evil_Twin 🔴 ❌
Confidence: 78%
Reason: Strong signal
```

**After v6.0:**
```
Status: Rogue_AP ⚠️ ✅
Confidence: 65%
Device: Apple Mobile Device
Reason: Mobile Hotspot detected
```

### ✅ Scenario 3: Actual Evil Twin (Still Detected!)

**Before v6.0:**
```
Status: Evil_Twin 🔴
Confidence: 92%
Reason: Duplicate SSID
```

**After v6.0:**
```
Status: Evil_Twin 🔴 ✅
Confidence: 92%
Device: Unknown Manufacturer
Reasons:
• EVIL TWIN ATTACK: Duplicate SSID of trusted network
• Original network: f4:ec:38:xx:xx:xx
• Impersonating device: Unknown Manufacturer
```

---

## Understanding Results

### Safe ✅
- Legitimate router manufacturer detected
- OR low confidence (below threshold)
- OR learned as baseline after 3+ clean scans

### Rogue_AP ⚠️
- Mobile phone hotspot
- Unknown device with moderate suspicion
- Open/unencrypted network
- Locally administered MAC

### Evil_Twin 🚨
- Duplicate SSID of trusted network
- Known attack device (Flipper Zero, ESP32, etc.)
- Strong evidence of impersonation

---

## Troubleshooting

### Problem: Router still shows as Evil Twin

**Solution:**
1. Check if MAC is recognized (logs show device name)
2. Add MAC OUI to database if missing
3. Scan 3+ times to establish baseline
4. Check threshold - confidence should be below 65%

### Problem: Phone hotspot shows as Evil Twin

**Solution:**
1. Check if phone brand is in database
2. Add phone MAC OUI if missing
3. v6.0 should automatically classify phones as Rogue_AP

### Problem: Can't find my device in logs

**Solution:**
1. Enable Android logs: `adb logcat | grep RogueAPDetector`
2. Look for: "🔍 Device: [SSID] ([BSSID]) → [Device Description]"
3. Check if manufacturer is recognized

---

## Adding Custom Devices

### Add Your Router Brand:

```kotlin
// In MACVendorDatabase.kt, under ROUTERS section:
"aa:bb:cc" to VendorInfo("Your Brand", DeviceType.ROUTER, true),
```

### Add Your Phone Brand:

```kotlin
// In MACVendorDatabase.kt, under MOBILE PHONES section:
"dd:ee:ff" to VendorInfo("Your Phone", DeviceType.MOBILE_PHONE, false),
```

### Mark Device as Attack Hardware:

```kotlin
// In MACVendorDatabase.kt, under ATTACK DEVICES section:
"11:22:33" to VendorInfo("Attack Device", DeviceType.ATTACK_DEVICE, false),
```

---

## Summary of Changes

| Feature | v5.0 | v6.0 |
|---------|------|------|
| Device identification | ❌ None | ✅ MAC OUI lookup |
| Router detection | ❌ Strong signal = threat | ✅ Manufacturer verified |
| Phone hotspots | ❌ Evil Twin | ✅ Rogue AP |
| False positives | ⚠️ High | ✅ Low |
| Detection reasons | ✅ Basic | ✅ Detailed with device info |
| Strong signal alone | ❌ Triggers Evil Twin | ✅ Safe for known routers |

---

## Need Help?

1. **Check logs:** Look for device identification in Android logs
2. **Read full docs:** See `VERSION_6_IMPROVEMENTS.md`
3. **Add your router:** Follow steps above to add MAC OUI
4. **Test thoroughly:** Scan multiple times to establish baselines

---

**Your router should now be safe!** 🎉
