package io.github.rfrancesco.timetracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Stop pressed on the notification (after unlocking, if the phone was locked). */
public class StopReceiver extends BroadcastReceiver {
    static final String EXTRA_START = "start";

    @Override
    public void onReceive(Context context, Intent intent) {
        long start = intent.getLongExtra(EXTRA_START, -1);
        if (TimerNotification.recordStop(context, start, System.currentTimeMillis())) {
            TimeTrackerPlugin.notifyStopped(); // if the app is alive, it applies the stop right away
        }
    }
}
