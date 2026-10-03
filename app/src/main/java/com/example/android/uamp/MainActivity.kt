package com.example.android.uamp

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
    }
}
