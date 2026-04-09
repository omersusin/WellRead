package com.omersusin.wellread

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.omersusin.wellread.ui.navigation.WellReadNavHost
import com.omersusin.wellread.ui.theme.WellReadTheme
import com.omersusin.wellread.utils.DataStoreManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by dataStoreManager.userPreferences.collectAsState(
                initial = com.omersusin.wellread.domain.model.UserPreferences()
            )
            WellReadTheme(
                darkTheme = prefs.isDarkMode,
                dynamicColor = prefs.useDynamicColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WellReadNavHost()
                }
            }
        }
    }
}
