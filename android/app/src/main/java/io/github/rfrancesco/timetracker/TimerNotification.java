package io.github.rfrancesco.timetracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import androidx.core.graphics.drawable.IconCompat;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.text.DateFormat;
import java.util.Date;

/**
 * The running-timer notification: ongoing, always asking to be a live update
 * (Android 16+: status-bar chip at the far left, top of the lock screen); tapping it
 * opens the Timer. Per area the web app chooses whether the running time ticks (system
 * chronometer in the chip and notification) or not (icon-only chip, "Since …").
 * No actions. It only mirrors the web app's state; the data stays in IndexedDB.
 */
final class TimerNotification {
    // v2: default importance (v1 was LOW, which Android treats as "silent" and hides
    // from the lock screen). Importance can't change on an existing channel.
    static final String CHANNEL = "running_timer";
    private static final String OLD_CHANNEL = "timer";
    static final int ID = 1;
    private static final String PREFS = "timer";
    private static final String SHOWN_START = "shownStart";
    private static final String PENDING_START = "pendingStart";
    private static final String PENDING_END = "pendingEnd";

    private TimerNotification() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.deleteNotificationChannel(OLD_CHANNEL);
        // DEFAULT so it counts as an alerting notification (shown on the lock screen),
        // but with no sound and no vibration; heads-up needs HIGH, so none either.
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    /** Show (or update) the notification. Returns false if notifications are off. */
    static synchronized boolean show(Context c, long start, String title, String color, String inkColor, Bitmap glyph, boolean promote, boolean showTime) {
        // A Stop from the notification that the web app hasn't applied yet: don't bring it back.
        if (prefs(c).getLong(PENDING_START, -1) == start) return true;
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) {
            prefs(c).edit().putString("lastError", "Notifications are off for the app").apply();
            return false;
        }
        ensureChannel(c);

        // Tapping opens the app on the Timer tab.
        Intent open = new Intent(c, MainActivity.class)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TIMER, true);
        PendingIntent openPi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String since = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(start));
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(c.getString(R.string.since, since))
            .setWhen(start)
            // Running time: the system chronometer ticks; otherwise no time at all, so the
            // chip shows just the icon (no short text set either).
            .setShowWhen(showTime)
            .setUsesChronometer(showTime)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .setRequestPromotedOngoing(promote);
        Integer bg = parse(color);
        if (bg != null) b.setColor(bg); // accent only: promoted notifications can't be colorized
        if (glyph != null) {
            // The area's own icon: in the status-bar chip (small icon, white glyph) and as
            // the large icon (glyph on the area colour), as in the app.
            b.setSmallIcon(IconCompat.createWithBitmap(glyph));
            if (bg != null) b.setLargeIcon(badge(glyph, bg, parse(inkColor)));
        }
        android.app.Notification n = b.build();
        SharedPreferences.Editor diag = prefs(c).edit();
        if (Build.VERSION.SDK_INT >= 36) diag.putBoolean("promotable", n.hasPromotableCharacteristics());
        try {
            nm.notify(ID, n);
        } catch (RuntimeException e) {
            diag.putString("lastError", e.toString()).apply();
            return false; // e.g. POST_NOTIFICATIONS not granted
        }
        diag.putLong(SHOWN_START, start).remove("lastError").putLong("lastShown", System.currentTimeMillis()).apply();
        return true;
    }

    private static Integer parse(String color) {
        if (color == null) return null;
        try {
            return Color.parseColor(color);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The glyph (white on transparent), recoloured with `ink`, centred on a circle of `bg`. */
    private static Bitmap badge(Bitmap glyph, int bg, Integer ink) {
        int size = 192;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setColor(bg);
        cv.drawCircle(size / 2f, size / 2f, size / 2f, p);
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        gp.setColorFilter(new PorterDuffColorFilter(ink != null ? ink : Color.WHITE, PorterDuff.Mode.SRC_IN));
        float g = size * 0.56f, o = (size - g) / 2f;
        cv.drawBitmap(glyph, null, new RectF(o, o, o + g, o + g), gp);
        return out;
    }

    static synchronized void hide(Context c) {
        NotificationManagerCompat.from(c).cancel(ID);
        prefs(c).edit().remove(SHOWN_START).apply();
    }

    // A pending Stop from the notification (older versions had a Stop action). Kept so
    // the plugin API stays the same for every web version; nothing records one now.

    /** The pending stop, left in place until the web app confirms it was applied. */
    static synchronized long[] peekPendingStop(Context c) {
        SharedPreferences p = prefs(c);
        long start = p.getLong(PENDING_START, -1);
        if (start < 0) return null;
        return new long[] { start, p.getLong(PENDING_END, -1) };
    }

    /** The web app applied (or deliberately ignored) the stop for `start`: forget it. */
    static synchronized void clearPendingStop(Context c, long start) {
        SharedPreferences p = prefs(c);
        if (p.getLong(PENDING_START, -1) == start) p.edit().remove(PENDING_START).remove(PENDING_END).apply();
    }

    /** Old one-step handover (kept for older web versions): returns and forgets it. */
    static synchronized long[] takePendingStop(Context c) {
        SharedPreferences p = prefs(c);
        long start = p.getLong(PENDING_START, -1);
        if (start < 0) return null;
        long end = p.getLong(PENDING_END, -1);
        p.edit().remove(PENDING_START).remove(PENDING_END).apply();
        return new long[] { start, end };
    }
}
