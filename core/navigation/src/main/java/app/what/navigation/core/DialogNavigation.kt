package app.what.navigation.core

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.what.foundation.ui.controllers.DialogController
import app.what.foundation.ui.controllers.LocalDialogController
import app.what.foundation.ui.controllers.rememberDialogHostController

@Composable
fun ProvideGlobalDialog(
    controller: DialogController = rememberDialogHostController(),
    transitionSpec: AnimatedContentTransitionScope<@Composable () -> Unit>.() -> ContentTransform = {
        fadeIn() togetherWith fadeOut()
    },
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalDialogController provides controller,
) {
    content()

    if (controller.opened) {
        Dialog(
            onDismissRequest = controller::close,
            properties = DialogProperties(
                dismissOnBackPress = controller.cancellable,
                dismissOnClickOutside = controller.cancellable
            )
        ) {
            Surface(
                shape = shapes.large,
                color = colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                AnimatedContent(
                    modifier = Modifier.padding(16.dp),
                    targetState = controller.content,
                    transitionSpec = transitionSpec,
                    label = "AnimatedDialogContent"
                ) { dialogContent ->
                    dialogContent()
                }
            }
        }
    }
}
