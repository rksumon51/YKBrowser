package com.yk.browser.web

import android.webkit.JavascriptInterface

/**
 * Detects login attempts inside the WebView.
 * - onLoginForm: fired when a login attempt starts (button click / submit / Enter
 *   with a filled password field). The app holds it as *pending*.
 * - onLoginSuccess: fired when the page confirms the login actually succeeded
 *   (URL changed, or the login form was removed/hidden). Only then is it saved.
 */
class LoginCaptureJs(
    private val onCapture: (host: String, username: String, password: String) -> Unit,
    private val onSuccess: (host: String, username: String, password: String) -> Unit
) {
    @JavascriptInterface
    fun onLoginForm(host: String, username: String, password: String) {
        if (password.isNotEmpty()) onCapture(host, username, password)
    }

    @JavascriptInterface
    fun onLoginSuccess(host: String, username: String, password: String) {
        if (password.isNotEmpty()) onSuccess(host, username, password)
    }
}

/**
 * Injected on every page load. Captures credentials from:
 * 1. Traditional HTML form submits
 * 2. Button clicks on AJAX/JS-driven login forms (most modern sites)
 * 3. Enter key pressed inside a password field
 *
 * Success is confirmed by the page itself: after an attempt, timed checks look
 * for a URL change (full navigation or SPA route change) or for the login form
 * being removed/hidden. Wrong-password attempts keep the form visible, so they
 * are never reported as successful.
 */
const val LOGIN_CAPTURE_JS = """(function(){
  if (window.__ykCaptureInstalled) return;
  window.__ykCaptureInstalled = true;

  function findPasswordField(){
    var fields = document.querySelectorAll('input[type="password"]');
    for (var i = 0; i < fields.length; i++) {
      if (fields[i].value) return fields[i];
    }
    return null;
  }

  function findUsernameField(pwField){
    var scope = pwField.form || pwField.closest('div') || document;
    var u = scope.querySelector('input[type="email"]')
         || scope.querySelector('input[type="text"]')
         || scope.querySelector('input:not([type])');
    if (!u || u === pwField) {
      u = document.querySelector('input[type="email"]')
       || document.querySelector('input[type="text"]');
    }
    return u;
  }

  function loginDone(a){
    if (window.location.href !== a.url) return true;   // navigated / SPA route changed
    var p = document.querySelector('input[type="password"]');
    if (!p) return true;                                // login form removed
    try { if (p.offsetParent === null) return true; } catch(e){}  // form hidden
    return false;
  }

  function checkSuccess(){
    var a = window.__ykAttempt;
    if (!a) return;
    var done = false;
    try { done = loginDone(a); } catch(e){}
    if (done) {
      window.__ykAttempt = null;
      try { AndroidLoginCapture.onLoginSuccess(a.host, a.user, a.pass); } catch(e){}
    }
  }

  function armChecks(){
    [2500, 5000, 9000, 15000].forEach(function(d){ setTimeout(checkSuccess, d); });
  }

  function captureAttempt(){
    var p = findPasswordField();
    if (!p || !p.value) return;
    var u = findUsernameField(p);
    var user = u ? (u.value || '') : '';
    var cur = window.__ykAttempt;
    if (cur && cur.user === user && cur.pass === p.value) { armChecks(); return; }
    var a = {host: window.location.hostname, user: user, pass: p.value, url: window.location.href};
    window.__ykAttempt = a;
    try { AndroidLoginCapture.onLoginForm(a.host, a.user, a.pass); } catch(e){}
    armChecks();
  }

  // 1. Classic form submit
  document.addEventListener('submit', function(){ setTimeout(captureAttempt, 300); }, true);

  // 2. Clicks on login buttons (AJAX logins)
  document.addEventListener('click', function(e){
    var t = e.target;
    if (!t || !t.closest) return;
    var btn = t.closest('button') || t.closest('input[type="submit"]')
           || t.closest('input[type="button"]') || t.closest('[role="button"]');
    if (btn) setTimeout(captureAttempt, 400);
  }, true);

  // 3. Enter key inside password field
  document.addEventListener('keydown', function(e){
    if (e.key === 'Enter' && e.target && e.target.type === 'password') {
      setTimeout(captureAttempt, 400);
    }
  }, true);
})();"""

/** Builds JS that fills saved credentials into a login form. Values are JS-escaped by the caller. */
fun buildAutofillJs(username: String, password: String): String {
    val u = username.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    val p = password.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
    return """(function(){
      var p = document.querySelector('input[type="password"]');
      if (!p) return;
      var scope = p.form || p.closest('div') || document;
      var u = scope.querySelector('input[type="email"]')
           || scope.querySelector('input[type="text"]')
           || document.querySelector('input[type="email"]')
           || document.querySelector('input[type="text"]');
      if (u && '$u') {
        u.focus(); u.value = '$u';
        u.dispatchEvent(new Event('input', {bubbles:true}));
        u.dispatchEvent(new Event('change', {bubbles:true}));
      }
      p.focus(); p.value = '$p';
      p.dispatchEvent(new Event('input', {bubbles:true}));
      p.dispatchEvent(new Event('change', {bubbles:true}));
    })();"""
}
