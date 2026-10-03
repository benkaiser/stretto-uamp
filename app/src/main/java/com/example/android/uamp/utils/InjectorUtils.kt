package com.example.android.uamp.utils

import android.app.Application
import android.content.ComponentName
import android.content.Context
import com.example.android.uamp.common.MusicServiceConnection
import com.example.android.uamp.media.MusicService
import com.example.android.uamp.viewmodels.StrettoViewModel

/** Static methods used to inject classes needed by the activity. */
object InjectorUtils {
    private fun provideMusicServiceConnection(context: Context): MusicServiceConnection {
        return MusicServiceConnection.getInstance(
            context.applicationContext,
            ComponentName(context, MusicService::class.java)
        )
    }

    fun provideStrettoViewModel(context: Context): StrettoViewModel.Factory {
        val applicationContext = context.applicationContext
        return StrettoViewModel.Factory(
            applicationContext as Application,
            provideMusicServiceConnection(applicationContext)
        )
    }
}
