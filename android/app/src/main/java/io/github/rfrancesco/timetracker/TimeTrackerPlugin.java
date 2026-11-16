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

    static void notifyStopped() {
        TimeTrackerPlugin p = instance.get();
        if (p != null) p.notifyListeners("stop", new JSObject());
    }

    /** {start: epoch ms, title, color?}: show or update the running-timer notification. */
    @PluginMethod
    public void showTimer(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notifications") != PermissionState.GRANTED
            && !getContext().getSharedPreferences("timer", 0).getBoolean("askedNotifications", false)) {
            // Ask once; after that the user decides in system settings.
            getContext().getSharedPreferences("timer", 0).edit().putBoolean("askedNotifications", true).apply();
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
        Double start = call.getDouble("start");
        String title = call.getString("title");
        if (start == null || title == null) {
            call.reject("start and title are required");
            return;
        }
        boolean shown = TimerNotification.show(getContext(), start.longValue(), title, call.getString("color"));
        JSObject r = new JSObject();
        r.put("shown", shown);
        call.resolve(r);
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
