package io.github.rfrancesco.timetracker;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The last time the web app's page failed to load (e.g. offline before the service
 * worker answered): what the WebView reported, kept for the error page and Settings.
 */
final class LoadErrors {
    private static final String PREFS = "loadErrors";

    private LoadErrors() {}

    static void record(Context c, String url, int code, String description) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", url)
            .putInt("code", code)
            .putString("description", description)
            .putLong("at", System.currentTimeMillis())
            .apply();
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
