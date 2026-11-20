package io.github.rfrancesco.timetracker;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

/**
 * The web app's view of the wrapper. Every method is optional for the web app:
 * in a browser the plugin doesn't exist and the app works as before.
 */
@CapacitorPlugin(name = "TimeTracker", permissions = @Permission(alias = "notifications", strings = Manifest.permission.POST_NOTIFICATIONS))
public class TimeTrackerPlugin extends Plugin {
    private static WeakReference<TimeTrackerPlugin> instance = new WeakReference<>(null);

    @Override
    public void load() {
        instance = new WeakReference<>(this);
        TimerNotification.ensureChannel(getContext());
    }

    /** The notification was tapped: the web app switches to the Timer tab. */
    static void notifyOpenTimer() {
        TimeTrackerPlugin p = instance.get();
        if (p != null) p.notifyListeners("openTimer", new JSObject());
    }


    /** {start: epoch ms, title, color?}: show or update the running-timer notification. */
    @PluginMethod
    public void showTimer(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED) {
            // Android itself stops showing the prompt after the user declines twice.
            requestPermissionForAlias("notifications", call, "afterPermission");
            return;
        }
        doShow(call);
    }

    @PermissionCallback
    private void afterPermission(PluginCall call) {
        doShow(call);
    }

    private void doShow(PluginCall call) {
        // Epoch ms arrive as Long (Capacitor's getDouble ignores Longs), so accept any number.
        Object rawStart = call.getData().opt("start");
        String title = call.getString("title");
        if (!(rawStart instanceof Number) || title == null) {
            call.reject("showTimer: start (number) and title are required, got start=" + rawStart);
            return;
        }
        long start = ((Number) rawStart).longValue();
        // Optional (newer web app): the area's icon as a white-on-transparent PNG (base64).
        android.graphics.Bitmap glyph = null;
        String icon = call.getString("icon");
        if (icon != null) {
            try {
                byte[] png = android.util.Base64.decode(icon, android.util.Base64.DEFAULT);
                glyph = android.graphics.BitmapFactory.decodeByteArray(png, 0, png.length);
            } catch (IllegalArgumentException ignored) {}
        }
        // Optional: promote (default true) = ask for a live update; showTime (default
        // true) = the running time ticks. Newer web apps always promote and choose the time per area.
        boolean promote = call.getBoolean("promote", true);
        boolean showTime = call.getBoolean("showTime", promote);
        boolean shown = TimerNotification.show(getContext(), start, title, call.getString("color"), call.getString("onColor"), glyph, promote, showTime);
        JSObject r = new JSObject();
        r.put("shown", shown);
        call.resolve(r);
    }

    /** Diagnostics for Settings: permission, channel, Live Update eligibility, last error. */
    @PluginMethod
    public void status(PluginCall call) {
        android.content.Context c = getContext();
        android.content.SharedPreferences p = TimerNotification.prefs(c);
        android.app.NotificationManager nm = c.getSystemService(android.app.NotificationManager.class);
        JSObject r = new JSObject();
        r.put("sdk", Build.VERSION.SDK_INT);
        r.put("permission", getPermissionState("notifications").toString());
        r.put("notificationsEnabled", nm.areNotificationsEnabled());
        if (Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationChannel ch = nm.getNotificationChannel(TimerNotification.CHANNEL);
            if (ch != null) r.put("channelImportance", ch.getImportance());
        }
        if (Build.VERSION.SDK_INT >= 36) {
            r.put("liveUpdatesAllowed", nm.canPostPromotedNotifications());
            if (p.contains("promotable")) r.put("promotable", p.getBoolean("promotable", false));
        }
        boolean posted = false;
        if (Build.VERSION.SDK_INT >= 23) {
            for (android.service.notification.StatusBarNotification sbn : nm.getActiveNotifications()) {
                if (sbn.getId() == TimerNotification.ID) {
                    posted = true;
                    if (Build.VERSION.SDK_INT >= 36) r.put("promoted", (sbn.getNotification().flags & android.app.Notification.FLAG_PROMOTED_ONGOING) != 0);
                }
            }
        }
        r.put("posted", posted);
        if (p.contains("lastError")) r.put("lastError", p.getString("lastError", ""));
        if (p.contains("lastShown")) r.put("lastShown", p.getLong("lastShown", 0));
        android.content.SharedPreferences le = LoadErrors.prefs(c);
        if (le.contains("at")) {
            JSObject e = new JSObject();
            e.put("at", le.getLong("at", 0));
            e.put("code", le.getInt("code", 0));
            e.put("description", le.getString("description", ""));
            e.put("url", le.getString("url", ""));
            r.put("lastLoadError", e);
        }
        r.put("longPressTimeout", android.view.ViewConfiguration.getLongPressTimeout());
        call.resolve(r);
    }

    /**
     * {kind: tick | long | confirm}: system haptic feedback (no VIBRATE permission
     * needed, follows the phone's haptic settings). The WebView's own haptics are off
     * (MainActivity), so this is the only buzz the web app's gestures produce.
     */
    @PluginMethod
    public void haptic(PluginCall call) {
        String kind = call.getString("kind", "tick");
        int effect;
        if ("long".equals(kind)) effect = android.view.HapticFeedbackConstants.LONG_PRESS;
        else if ("confirm".equals(kind)) effect = Build.VERSION.SDK_INT >= 30 ? android.view.HapticFeedbackConstants.CONFIRM : android.view.HapticFeedbackConstants.VIRTUAL_KEY;
        else effect = android.view.HapticFeedbackConstants.CLOCK_TICK;
        android.webkit.WebView wv = getBridge().getWebView();
        // Our view has haptics disabled (no stray long-press buzz), so override that setting here.
        getActivity().runOnUiThread(() -> wv.performHapticFeedback(effect, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING));
        call.resolve();
    }

    /** Open the system page for this app's notifications (incl. the Live updates switch). */
    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        Intent i;
        if (Build.VERSION.SDK_INT >= 26) {
            i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getContext().getPackageName());
        } else {
            i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        }
        getActivity().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void hideTimer(PluginCall call) {
        TimerNotification.hide(getContext());
        call.resolve();
    }

    /** A Stop pressed on the notification that hasn't been applied yet: {start, end}, or {} if none. */
    @PluginMethod
    public void takePendingStop(PluginCall call) {
        long[] s = TimerNotification.takePendingStop(getContext());
        JSObject r = new JSObject();
        if (s != null) {
            r.put("start", s[0]);
            r.put("end", s[1]);
        }
        call.resolve(r);
    }

    /**
     * The pending Stop from the notification, {start, end} or {}; it stays until
     * clearPendingStop, so a reload between reading and saving can't lose it.
     */
    @PluginMethod
    public void peekPendingStop(PluginCall call) {
        long[] s = TimerNotification.peekPendingStop(getContext());
        JSObject r = new JSObject();
        if (s != null) {
            r.put("start", s[0]);
            r.put("end", s[1]);
        }
        call.resolve(r);
    }

    /** {start}: the web app has applied the stop for this timer (or found it no longer applies). */
    @PluginMethod
    public void clearPendingStop(PluginCall call) {
        Object raw = call.getData().opt("start");
        if (!(raw instanceof Number)) {
            call.reject("clearPendingStop: start (number) is required");
            return;
        }
        TimerNotification.clearPendingStop(getContext(), ((Number) raw).longValue());
        call.resolve();
    }

    /** {name, text, mimeType?}: share a text file (backup export) through the Android share sheet. */
    @PluginMethod
    public void shareFile(PluginCall call) {
        String name = call.getString("name");
        String text = call.getString("text");
        if (name == null || text == null || name.contains("/")) {
            call.reject("name and text are required");
            return;
        }
        try {
            File dir = new File(getContext().getCacheDir(), "exports");
            dir.mkdirs();
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) f.delete();
            File file = new File(dir, name);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND)
                .setType(call.getString("mimeType", "application/json"))
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            getActivity().startActivity(Intent.createChooser(send, name));
            call.resolve();
        } catch (Exception e) {
            call.reject("Couldn't share the file", e);
        }
    }
}
