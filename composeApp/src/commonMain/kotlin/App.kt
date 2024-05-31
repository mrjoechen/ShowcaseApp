import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_name
import showcaseapp.composeapp.generated.resources.compose_multiplatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun MainApp() {
    AppTheme {
        var showContent by remember { mutableStateOf(false) }

        Scaffold(topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Text(stringResource(Res.string.app_name))
                    },
                )
            }
        }) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = {
                    showContent = !showContent
                }) {
                    Text("Click me!")
                }
                AnimatedVisibility(showContent) {
                    val greeting = remember { Greeting().greet() }
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(Res.drawable.compose_multiplatform), null)
                        Text("Compose: $greeting")
                    }
                }
            }
        }


    }
}