package dev.glinesb.openmacros

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.glinesb.openmacros.ui.OpenMacrosRoot
import dev.glinesb.openmacros.ui.theme.OpenMacrosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { OpenMacrosTheme { OpenMacrosRoot() } }
    }
}
