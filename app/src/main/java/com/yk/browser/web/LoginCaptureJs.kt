package com.yk.browser.web

import android.webkit.JavascriptInterface

/**
 * Detects password-form submissions inside the WebView and reports them to
 * the app, which asks the user whether to save the login.
 */
class LoginCaptureJs(
    private val onCapture: (host: String, username: String, password: String) -> Unit
) {
    @JavascriptInterface
    fun onLoginForm(host: String, username: String, password: String) {
        if (password.isNotEmpty()) onCapture(host, username, password)
    }
}

/** Injected on every page load; listens for form submits containing a password field. */
const val LOGIN_CAPTURE_JS = """(function(){
  if (window.__ykCaptureInstalled) return;
  window.__ykCaptureInstalled = true;
  document.addEventListener('submit', function(e){
    try {
      var f = e.target;
      if (!f || !f.querySelector) return;
      var p = f.querySelector('input[type="password"]');
      if (!p || !p.value) return;
      var u = f.querySelector('input[type="email"]')
           || f.querySelector('input[type="text"]')
           || f.querySelector('input:not([type])');
      AndroidLoginCapture.onLoginForm(window.location.hostname, u ? (u.value||'') : '', p.value);
    } catch(err){}
  }, true);
})();"""
