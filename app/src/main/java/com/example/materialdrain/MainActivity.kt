package com.example.materialdrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Import for enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Added import
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.ui.MaterialDrainScreen // Import the new screen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // Added this call
        enableEdgeToEdge() // Call before super.onCreate or setContent
        super.onCreate(savedInstanceState)
        setContent {
            MaterialdrainTheme {
                MaterialDrainScreen() // Use the new screen
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialdrainTheme {
        MaterialDrainScreen() // Preview the new screen
    }
}
