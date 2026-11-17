# Time Tracker Android wrapper: notes for Claude

Capacitor 8 wrapper for the web app in rfrancesco/TimeTracker (its CLAUDE.md has
the data invariants). The app loads `server.url` = GitHub Pages; `www/` only holds
an offline error page. Owner's phone: Pixel 6a, Android 17.

## Native code (android/app/src/main/java/io/github/rfrancesco/timetracker)
- `TimeTrackerPlugin`: plugin "TimeTracker": `showTimer({start,title,color})`,
  `hideTimer()`, `peekPendingStop()` + `clearPendingStop({start})` (old: `takePendingStop()`),
  `shareFile({name,text})`, `status()`, `openNotificationSettings()`, event `stop`.
  Web side: `src/native.ts` in the web repo (a no-op in browsers; older wrappers
  must keep working, so methods are only added, never changed).
- `TimerNotification`: ongoing, `setRequestPromotedOngoing` (Live Update chip),
  system chronometer, public on the lock screen, Stop with
  `setAuthenticationRequired(true)`. Promoted notifications must not be colorized.
- `StopReceiver`: records {start, end} in SharedPreferences. Native code never
  writes the data: the web app applies it via `stopAt` + undo (live event, or on
  next launch/resume). Stale stops (another timer) are ignored.
- `MainActivity`: back = WebView goBack (the web app's history guards close dialogs).

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
