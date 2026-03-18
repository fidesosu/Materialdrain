package com.example.materialdrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Import for enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Added import
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.materialdrain.ui.theme.MaterialdrainTheme
import com.example.materialdrain.ui.MaterialdrainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialdrainTheme {
                MaterialdrainScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialdrainTheme {
        MaterialdrainScreen()
    }
}
