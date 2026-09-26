package tools.senko.materialdrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Import for enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen // Added import
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import tools.senko.materialdrain.ui.theme.MaterialdrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            _root_ide_package_.tools.senko.materialdrain.ui.theme.MaterialdrainTheme {
                _root_ide_package_.tools.senko.materialdrain.MaterialdrainScreen()
            }
        }
    }

    // Finished transfers only need a notification when the user is not looking at the app
    override fun onStart() {
        super.onStart()
        _root_ide_package_.tools.senko.materialdrain.AppContainer.get(application).transferRegistry.appInForeground = true
    }

    override fun onStop() {
        _root_ide_package_.tools.senko.materialdrain.AppContainer.get(application).transferRegistry.appInForeground = false
        super.onStop()
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    _root_ide_package_.tools.senko.materialdrain.ui.theme.MaterialdrainTheme {
        _root_ide_package_.tools.senko.materialdrain.MaterialdrainScreen()
    }
}
