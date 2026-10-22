package com.example.android.uamp

import android.media.AudioManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.android.uamp.common.auth.StrettoAccount
import com.example.android.uamp.ui.StrettoApp
import com.example.android.uamp.ui.theme.StrettoTheme
import com.example.android.uamp.utils.InjectorUtils
import com.example.android.uamp.viewmodels.StrettoViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<StrettoViewModel> {
        InjectorUtils.provideStrettoViewModel(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Volume keys should control music while the app is in the foreground.
        volumeControlStream = AudioManager.STREAM_MUSIC

        setContent {
            StrettoTheme {
                StrettoApp(viewModel)
            }
        }
        handleSignInCallback(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSignInCallback(intent)
    }

    override fun onStart() {
        super.onStart()
        // Pick up library changes made elsewhere, e.g. on the website.
        StrettoAccount.getInstance(this).let { if (it.isSignedIn) it.requestRefresh() }
    }

    /** Completes a browser sign-in returning through `stretto-native://auth`. */
    private fun handleSignInCallback(intent: Intent?) {
        val data = intent?.data ?: return
        if (!viewModel.isLoginCallback(data)) return
        intent.data = null
        viewModel.completeBrowserSignIn(data)
    }
}
