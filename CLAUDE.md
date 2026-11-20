# Time Tracker Android wrapper: notes for Claude

Capacitor 8 wrapper for the web app in rfrancesco/TimeTracker (its CLAUDE.md has
the data invariants). The app loads `server.url` = GitHub Pages; `www/` only holds
an offline error page. Owner's phone: Pixel 6a, Android 17.

## Native code (android/app/src/main/java/io/github/rfrancesco/timetracker)
- `TimeTrackerPlugin`: plugin "TimeTracker": `showTimer({start,title,color,onColor,icon,promote,showTime})`,
  `hideTimer()`, `peekPendingStop()` + `clearPendingStop({start})` (old: `takePendingStop()`),
  `shareFile({name,text})`, `status()`, `openNotificationSettings()`, `haptic({kind})`, events `stop`
  (older versions) and `openTimer` (notification tapped while the app is open).
  Web side: `src/native.ts` in the web repo (a no-op in browsers; older wrappers
  must keep working, so methods are only added, never changed).
- `TimerNotification`: ongoing, no actions, tap opens the Timer. `promote` (the
  area's live update setting): `setRequestPromotedOngoing` → chip at the far left, top
  of the lock screen (may hide other icons); false → plain notification (ranks after
  messaging apps). `showTime` (running time On): system chronometer; false: no
  chronometer/"when", so an icon-only chip. Chip and notification share that clock.
  Public on the lock screen. Promoted notifications must not be colorized. Channel
  `running_timer` (DEFAULT importance, no sound/vibration: LOW counts as silent).
- Pending stop (peek/clear/take): only older versions recorded one (Stop action);
  kept so the plugin API never changes.
- Haptics: `haptic({kind: tick|long|confirm})` → `performHapticFeedback` (no VIBRATE
  permission). The WebView's own haptic feedback is disabled (its long-press buzz came
  on Android's schedule, not the web app's), so the web app's gestures decide.
- `MainActivity`: back = WebView goBack (the web app's history guards close dialogs);
  `onNewIntent` with the open-Timer extra → `openTimer` event.

## Principles
- Minimal permissions: INTERNET, POST_NOTIFICATIONS, POST_PROMOTED_NOTIFICATIONS.
  No foreground service, no boot receiver.
- No suggestions/automation until the owner decides.

## Build
- CI: `.github/workflows/build.yml` (every push → APK artifact; main + signing
  secrets → release for Obtainium). versionCode = run number.
- Locally: `npm run build:debug`. In Claude's cloud sandbox the Gradle wrapper
  download is blocked; use a system Gradle 8.14 and `local.properties` with sdk.dir.
- Things only testable on the phone: the chip, lock-screen auth, share sheet,
  permission prompt. Say so explicitly.
