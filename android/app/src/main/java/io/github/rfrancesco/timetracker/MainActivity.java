package io.github.rfrancesco.timetracker;

import android.content.Intent;
import android.os.SystemClock;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import com.getcapacitor.BridgeWebViewClient;
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
        // A failed load of the web app (e.g. offline before the service worker answers):
        // record what the WebView reported, retry once right away (a cold start can race
        // the service worker), and only then show the error page (which keeps retrying).
        if (getBridge() != null) {
            getBridge().setWebViewClient(new BridgeWebViewClient(getBridge()) {
                private long lastRetry = 0;

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request.isForMainFrame()) {
                        String url = request.getUrl().toString();
                        LoadErrors.record(view.getContext(), url, error.getErrorCode(), String.valueOf(error.getDescription()));
                        String app = getBridge().getServerUrl();
                        long now = SystemClock.elapsedRealtime();
                        if (app != null && url.startsWith(app) && now - lastRetry > 15_000) {
                            lastRetry = now;
                            view.postDelayed(() -> view.loadUrl(url), 700);
                            return;
                        }
                    }
                    super.onReceivedError(view, request, error);
                }
            });
        }

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
