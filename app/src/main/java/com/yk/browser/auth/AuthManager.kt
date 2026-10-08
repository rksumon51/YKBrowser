package com.yk.browser.auth

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.yk.browser.R

/**
 * Browser account: Firebase Authentication via Google Sign-In.
 * The Firebase uid scopes the user's cloud vault in Firestore.
 */
object AuthManager {
    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null

    fun currentEmail(): String? = FirebaseAuth.getInstance().currentUser?.email

    fun googleSignInOptions(context: Context): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

    fun signInWithGoogle(context: Context, idToken: String) =
        FirebaseAuth.getInstance().signInWithCredential(
            GoogleAuthProvider.getCredential(idToken, null)
        )

    fun signOut(context: Context) {
        FirebaseAuth.getInstance().signOut()
        GoogleSignIn.getClient(context, googleSignInOptions(context)).signOut()
    }
}
