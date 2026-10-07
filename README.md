# NotifyTV

Forward Android phone notifications to an Android TV over WiFi.

- `tv/` - TV receiver. Shows a QR code; draws notifications as an overlay.
- `phone/` - Phone sender. Scan the TV QR, pick apps, corner, duration, color.

APKs are built by GitHub Actions on every push and attached to a Release (see the Releases page).

## Setup
1. Install `NotifyTV-tv.apk` on the TV, open it. Grant "Display over other apps" (or `adb shell appops set com.example.notifytv.tv SYSTEM_ALERT_WINDOW allow`).
2. Install `NotifyTV-phone.apk` on the phone. Grant notification access (Android 13+: App info -> menu -> Allow restricted settings first).
3. Tap **Scan TV QR**, pick apps, done.
