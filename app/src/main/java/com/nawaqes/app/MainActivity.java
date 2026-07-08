package com.nawaqes.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.annotation.SuppressLint;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int ALL_PERMISSIONS_CODE = 200;
    private static final String START_URL = "https://safwatkhokha-nawaqes.hf.space/";
    private boolean permissionsResolved = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 0);

        // Build the WebView first (no URL yet)
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setGeolocationEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " NawaqesApp/1.0.3");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                // Grant ALL requested permissions IMMEDIATELY without dialog
                runOnUiThread(() -> {
                    request.grant(request.getResources());
                });
            }
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        // Restore state if available (rotation), else request permissions then load
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            permissionsResolved = true;
        } else {
            // Request permissions FIRST, then load the URL after they are resolved.
            // This guarantees RECORD_AUDIO + CAMERA are granted at the Android system
            // level before any getUserMedia() call inside the WebView.
            requestAllPermissions();
        }
    }

    private void requestAllPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };

            java.util.List<String> needed = new java.util.ArrayList<>();
            for (String perm : permissions) {
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(perm);
                }
            }

            if (needed.isEmpty()) {
                // All already granted — load immediately
                onPermissionsReady();
            } else {
                requestPermissions(needed.toArray(new String[0]), ALL_PERMISSIONS_CODE);
            }
        } else {
            // Pre-M: permissions granted at install time
            onPermissionsReady();
        }
    }

    /** Called once Android-level permissions are resolved (granted or denied). */
    private void onPermissionsReady() {
        if (permissionsResolved) return;
        permissionsResolved = true;

        // Verify critical permissions — warn but still load (camera may still work)
        boolean micOk = true;
        boolean camOk = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            camOk = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        }

        if (!micOk || !camOk) {
            Toast.makeText(this,
                "يجب السماح بالكاميرا والميكروفون ليعمل البث المباشر بشكل كامل",
                Toast.LENGTH_LONG).show();
            // Open app settings so the user can grant permissions manually
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception ignored) {}
        }

        // Load the URL regardless — the page itself will handle the toast
        webView.loadUrl(START_URL);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != ALL_PERMISSIONS_CODE) return;

        boolean micGranted = false;
        boolean camGranted = false;
        for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])) {
                micGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            } else if (Manifest.permission.CAMERA.equals(permissions[i])) {
                camGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            }
        }

        if (micGranted && camGranted) {
            Toast.makeText(this, "تم منح جميع الأذونات ✓", Toast.LENGTH_SHORT).show();
            onPermissionsReady();
        } else {
            // Check if user selected "Don't ask again" — if so, send them to settings
            boolean shouldRationale = shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                                   || shouldShowRequestPermissionRationale(Manifest.permission.CAMERA);
            if (!shouldRationale) {
                // User denied permanently — open settings
                Toast.makeText(this,
                    "تم رفض الإذن نهائياً — يرجى السماح من إعدادات التطبيق",
                    Toast.LENGTH_LONG).show();
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
            }
            // Still load the page so the user sees the in-app message too
            onPermissionsReady();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
