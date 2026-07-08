package com.nawaqes.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.annotation.SuppressLint;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;
    private static final int ALL_PERMISSIONS_CODE = 200;
    private static final int FILE_CHOOSER_CODE = 201;
    private static final String START_URL = "https://safwatkhokha-nawaqes.hf.space/";
    private boolean permissionsResolved = false;

    // Holds the file-picker callback so we can deliver the chosen Uri[]
    // back to the WebView when onActivityResult fires.
    private ValueCallback<Uri[]> fileUploadCallback;
    // Last captured photo Uri (for the camera-take-photo flow).
    private Uri cameraPhotoUri;

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
        settings.setUserAgentString(settings.getUserAgentString() + " NawaqesApp/1.0.4");

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

            // ════════════════════════════════════════════════════════════════
            // FILE CHOOSER — required for <input type="file"> to work in WebView.
            // Without this override, clicking "add image" in the web app does
            // NOTHING on Android because the WebView doesn't know how to open
            // the Android file picker.
            // ════════════════════════════════════════════════════════════════
            @Override
            public boolean onShowFileChooser(WebView webView,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                // Cancel any previous callback (in case user double-tapped)
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                // Build the file picker intent — supports images AND videos.
                // We pass EXTRA_MIME_TYPES to restrict to image/video so the
                // picker shows a meaningful file browser.
                Intent contentIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentIntent.setType("*/*");
                contentIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                    "image/*", "video/*"
                });
                contentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);

                // Wrap in a chooser so the user sees "Files / Gallery / Camera"
                Intent chooserIntent = Intent.createChooser(contentIntent, "اختر صورة أو فيديو");
                try {
                    startActivityForResult(chooserIntent, FILE_CHOOSER_CODE);
                } catch (ActivityNotFoundException e) {
                    // No file picker available — cancel the callback so the
                    // web page's input gets an empty response (instead of
                    // hanging forever waiting for onActivityResult).
                    fileUploadCallback = null;
                    Toast.makeText(MainActivity.this,
                        "لا يوجد تطبيق لاختيار الملفات",
                        Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
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
            // Build permission list based on Android version:
            // - Android 13+ (TIRAMISU / API 33+): use granular READ_MEDIA_* permissions
            // - Android < 13: use legacy READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE
            java.util.List<String> perms = new java.util.ArrayList<>();
            perms.add(Manifest.permission.CAMERA);
            perms.add(Manifest.permission.RECORD_AUDIO);
            perms.add(Manifest.permission.MODIFY_AUDIO_SETTINGS);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ — granular media permissions (replace READ_EXTERNAL_STORAGE)
                perms.add(Manifest.permission.READ_MEDIA_IMAGES);
                perms.add(Manifest.permission.READ_MEDIA_VIDEO);
                perms.add(Manifest.permission.READ_MEDIA_AUDIO);
                // POST_NOTIFICATIONS was added in 13 too — request it now
                perms.add(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Android < 13 — legacy storage permissions
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    // WRITE_EXTERNAL_STORAGE is useless on Android 10+ (scoped storage)
                    perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }

            String[] permissions = perms.toArray(new String[0]);
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

    // ═══════════════════════════════════════════════════════════════════
    // FILE CHOOSER RESULT — delivers the picked files back to the WebView
    // so the JavaScript <input type="file">.onchange handler fires.
    // ═══════════════════════════════════════════════════════════════════
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != FILE_CHOOSER_CODE) return;
        if (fileUploadCallback == null) return;

        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK && data != null) {
            // Multiple files selected (ClipData)
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            // Single file selected (getData)
            else if (data.getData() != null) {
                results = new Uri[]{ data.getData() };
            }
        }

        // Deliver the result (or null if user cancelled) to the WebView.
        // CRITICAL: always call onReceiveValue — otherwise the input is
        // "stuck" and won't fire onChange on subsequent selections.
        fileUploadCallback.onReceiveValue(results);
        fileUploadCallback = null;
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
