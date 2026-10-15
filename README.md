# Shot Bubble

A floating, iOS AssistiveTouch-style button for Android (built and tested on Pixel). Tap it to take a screenshot that goes **straight to the clipboard without being saved to the gallery** — no more deleting screenshots afterwards.

一顆像 iOS 輔助觸控的懸浮小白點：點一下就截圖並複製到剪貼簿，**不會存進相簿**，不用再回相簿刪截圖。

## Features

- Tap the bubble → screenshot copied to clipboard (paste into Discord, LINE, Gboard, …)
- Drag to move; snaps to the nearest screen edge and dims when idle
- Notification after each capture with a **Save to gallery** action (`Pictures/Screenshots`)
- Quick Settings tile to show / hide the bubble
- Haptic feedback that works even with system touch feedback turned off
- Only the latest screenshot is kept in the app's private cache

## Requirements

Android 11 (API 30) or later.

## Install

1. Download `shot-bubble-<version>.apk` from [Releases](../../releases) and install it.
2. Open **Shot Bubble** and allow notifications.
3. Go to **Settings → Accessibility → Shot Bubble** and turn it on.
   - If the toggle is greyed out ("Restricted setting"), open **Settings → Apps → Shot Bubble → ⋮ → Allow restricted settings**, then try again.
4. Optional: add the **截圖小白點** tile in Quick Settings.

### Why Accessibility?

Android only lets accessibility services take screenshots silently (`AccessibilityService.takeScreenshot`) and draw a floating overlay without extra permissions. The service does not read screen content or listen to accessibility events. Screenshots are FLAG_SECURE-aware: apps that block screenshots (e.g. banking apps) still block them.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleDebug          # debug build
./gradlew assembleRelease        # release build, signed if keystore.properties exists
```

`keystore.properties` (not committed):

```properties
storeFile=/absolute/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```
