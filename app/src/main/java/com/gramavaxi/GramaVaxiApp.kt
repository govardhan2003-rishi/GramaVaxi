package com.gramavaxi

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class GramaVaxiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        val appCheckProvider = if (BuildConfig.DEBUG) {
            debugAppCheckProvider()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProvider)
    }

    private fun debugAppCheckProvider(): AppCheckProviderFactory {
        val debugFactory = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
        val getInstance = debugFactory.getMethod("getInstance")
        return getInstance.invoke(null) as AppCheckProviderFactory
    }
}
