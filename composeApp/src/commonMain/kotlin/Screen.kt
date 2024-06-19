import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.ui.play.PlayPage

class HomeScreen : Screen {
    @Composable
    override fun Content() {
        HomePage()
    }
}


data class PlayScreen(val remoteApi: RemoteApi<Any>) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Box(modifier = Modifier.onKeyEvent {
            if (it.type == KeyEventType.KeyDown && (it.key in listOf(Key.Backspace, Key.Escape, Key.Back))) {
                navigator.popUntilRoot()
                true
            } else {
                false
            }
        }) {
            PlayPage(remoteApi)
        }
    }
}