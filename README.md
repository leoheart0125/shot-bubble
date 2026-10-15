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
   If it's blocked, see [Restricted settings](#restricted-settings) below.
4. Optional: add the **截圖小白點** tile in Quick Settings.

Updates signed with the same key install over the previous version and keep your settings.

### Restricted settings

On Android 13 and later, apps installed from a browser download or a file manager can't turn on an accessibility service until you allow it. The toggle is greyed out, or a "Restricted setting" dialog appears. To unblock it:

1. Try to turn on **Settings → Accessibility → Shot Bubble** once. Android blocks it, and this is what makes the next option appear.
2. Open **Settings → Apps → Shot Bubble**. The **Open app info** button on the app's main screen goes straight there.
3. Tap **⋮** (top right) → **Allow restricted settings**, then confirm with your fingerprint or PIN.
4. Go back to **Settings → Accessibility → Shot Bubble** and turn it on.

The ⋮ menu option only shows up after step 1. The app's main screen shows these same steps while the service is off.

在 Android 13 以上，從瀏覽器或檔案管理員安裝的 App 會被擋住無障礙權限（開關是灰色，或跳出「受限制的設定」）：

1. 先到 **設定 → 無障礙 → Shot Bubble** 試著開啟一次，會被擋下，這一步是讓下一步的選項出現
2. 到 **設定 → 應用程式 → Shot Bubble**（App 主畫面的「開啟應用程式資訊」按鈕可以直接跳過去）
3. 點右上角 **⋮** →「**允許受限制的設定**」，再驗證指紋或 PIN
4. 回到無障礙設定開啟 Shot Bubble

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
