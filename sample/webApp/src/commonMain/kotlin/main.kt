import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import sample.app.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() = ComposeViewport { App() }
