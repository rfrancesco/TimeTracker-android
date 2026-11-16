package io.github.rfrancesco.timetracker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.text.DateFormat;
import java.util.Date;

/**
 * The running-timer notification: ongoing, asks to be promoted to a Live Update
 * (Android 16+: status-bar chip with the system chronometer), one Stop action that
 * needs the device unlocked. The notification only mirrors the web app's state;
 * the data stays in the web app's IndexedDB.
 */
final class TimerNotification {
    static final String CHANNEL = "timer";
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
        // LOW: no sound or heads-up. (MIN would hide the chip; promoted needs more than MIN.)
        NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    /** Show (or update) the notification. Returns false if notifications are off. */
    static synchronized boolean show(Context c, long start, String title, String color) {
        // A Stop from the notification that the web app hasn't applied yet: don't bring it back.
        if (prefs(c).getLong(PENDING_START, -1) == start) return true;
        NotificationManagerCompat nm = NotificationManagerCompat.from(c);
        if (!nm.areNotificationsEnabled()) return false;
        ensureChannel(c);

        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent stop = new Intent(c, StopReceiver.class).putExtra(StopReceiver.EXTRA_START, start);
        PendingIntent stopPi = PendingIntent.getBroadcast(c, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action stopAction = new NotificationCompat.Action.Builder(R.drawable.ic_stop, c.getString(R.string.stop), stopPi)
            // On the lock screen the notification is visible, but Stop asks for PIN/fingerprint first.
            .setAuthenticationRequired(true)
            .build();

        String since = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(start));
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(title)
            .setContentText(c.getString(R.string.since, since))
            .setWhen(start)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPi)
            .addAction(stopAction)
            .setRequestPromotedOngoing(true);
        if (color != null) {
            try {
                b.setColor(Color.parseColor(color)); // accent only: promoted notifications can't be colorized
            } catch (IllegalArgumentException ignored) {}
        }
        try {
            nm.notify(ID, b.build());
        } catch (SecurityException e) {
            return false; // POST_NOTIFICATIONS not granted
        }
        prefs(c).edit().putLong(SHOWN_START, start).apply();
        return true;
    }

    static synchronized void hide(Context c) {
        NotificationManagerCompat.from(c).cancel(ID);
        prefs(c).edit().remove(SHOWN_START).apply();
    }

    /** Called by StopReceiver: remember when the timer was stopped, until the web app applies it. */
    static synchronized boolean recordStop(Context c, long start, long end) {
        SharedPreferences p = prefs(c);
        if (p.getLong(SHOWN_START, -1) != start) return false; // stale action from an old notification
        p.edit().putLong(PENDING_START, start).putLong(PENDING_END, end).remove(SHOWN_START).apply();
        NotificationManagerCompat.from(c).cancel(ID);
        return true;
    }

    /** Hand the pending stop to the web app (once). Returns {start, end} or null. */
    static synchronized long[] takePendingStop(Context c) {
        SharedPreferences p = prefs(c);
        long start = p.getLong(PENDING_START, -1);
        if (start < 0) return null;
        long end = p.getLong(PENDING_END, -1);
        p.edit().remove(PENDING_START).remove(PENDING_END).apply();
        return new long[] { start, end };
    }
}
