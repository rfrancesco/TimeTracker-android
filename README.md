# Tracker for Android

A thin Android wrapper (Capacitor 8) around the
[Time Tracker web app](https://rfrancesco.github.io/TimeTracker/). It loads the web
app from GitHub Pages (so web updates arrive without reinstalling) and adds one
thing a PWA can't do: a **running-timer notification** that Android 16+ promotes
to a **Live Update** (status-bar chip with the elapsed time).

- Per area, a live update (running time in the status bar and on the lock screen)
  or a quiet notification (area icon, no ticking time): Settings → Android app →
  Live updates. Tapping the notification opens the Timer.
- Data stays in the web app's storage on the phone. The wrapper only mirrors the timer.

Permissions: Internet (to load the app), notifications, promoted notifications.

## Builds

Every push builds an APK (Actions → the run → Artifacts). On `main`, a signed
build is also published as a release `v1.0.<run>`. Point
[Obtainium](https://github.com/ImranR98/Obtainium) at this repo to get updates.

### Signing key (one-time)

Android only installs an update if it's signed with the same key as the installed
app. Create a key once, keep a copy somewhere safe, and add it as repository
secrets (Settings → Secrets and variables → Actions):

```bash
keytool -genkeypair -keystore timetracker.jks -alias timetracker -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 timetracker.jks   # → secret KEYSTORE_BASE64
```

Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`timetracker`),
`KEY_PASSWORD`. Without them, CI builds a debug-signed APK whose key changes
on every run, so it can't be installed over a previous build, and no release is published.

## Local build

Needs JDK 21 and the Android SDK (platform 36).

```bash
npm ci
npm run build:debug   # → android/app/build/outputs/apk/debug/app-debug.apk
```

## Moving data from the PWA

The app's storage is separate from Chrome's, even on the same address. In the
PWA: Settings → Export backup; in the app: Settings → Restore backup.
