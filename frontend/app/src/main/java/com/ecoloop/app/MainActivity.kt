package com.ecoloop.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ecoloop.app.ui.EcoLoopRoot
import com.ecoloop.app.ui.theme.EcoLoopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as EcoLoopApplication).container
        setContent {
            EcoLoopTheme {
                EcoLoopRoot(session = container.session)
            }
        }
    }
}
