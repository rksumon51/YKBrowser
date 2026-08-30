package com.yk.browser;

import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;

public class CloneBrowserActivity extends AppCompatActivity {

    private WebView webView;
    private EditText etUrl;
    private String profileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clone_browser);

        profileName = getIntent().getStringExtra("profile_name");
        setTitle("YK Browser - " + profileName);

        webView = findViewById(R.id.webView);
        etUrl = findViewById(R.id.etUrl);
        Button btnGo = findViewById(R.id.btnGo);

        setupIsolatedWebView();

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://www.google.com");
        etUrl.setText("https://www.google.com");

        btnGo.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            webView.loadUrl(url);
        });
    }

    private void setupIsolatedWebView() {
        // প্রতিটি প্রোফাইলের জন্য আলাদা ডাটা ডিরেক্টরি সেটআপ (Data & Cookie Isolation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String processName = getApplicationContext().getPackageName() + "_" + profileName;
            WebView.setDataDirectorySuffix(processName);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        // ক্লোন প্রোফাইল অনুযায়ী কুকিজ আলাদা রাখা
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
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
