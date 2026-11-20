package io.github.rfrancesco.timetracker;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    static final String EXTRA_OPEN_TIMER = "openTimer";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(TimeTrackerPlugin.class);
        super.onCreate(savedInstanceState);

        // Back = browser back, like the PWA: the web app's history guards close
        // dialogs and return to the previous tab. With no history left, leave the app.
        // The WebView's own long-press haptic fires on Android's schedule (~400 ms+), not
        // when the web app's hold gestures arm; the web app asks for haptics itself
        // (TimeTrackerPlugin.haptic), so the view's own are off.
        if (getBridge() != null) getBridge().getWebView().setHapticFeedbackEnabled(false);
        // (A cold start from the notification lands on the Timer anyway: it is the start page.)
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView wv = getBridge() != null ? getBridge().getWebView() : null;
                if (wv != null && wv.canGoBack()) {
                    wv.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // The app was already open: tapping the notification switches to the Timer tab.
        if (intent.getBooleanExtra(EXTRA_OPEN_TIMER, false)) TimeTrackerPlugin.notifyOpenTimer();
    }
}
