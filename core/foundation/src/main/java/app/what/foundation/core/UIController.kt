package app.what.foundation.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import app.what.foundation.utils.RE
import app.what.foundation.utils.SE
import app.what.foundation.utils.SE1
import app.what.foundation.utils.safeExecute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

abstract class UIController<State : Any, Action, Event>(initialState: State) : ViewModel() {
    private val _viewStates = MutableStateFlow(initialState)
    private val _viewActions =
        MutableSharedFlow<Action?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    protected val viewState: State
        get() = _viewStates.value

    private var viewAction: Action?
        get() = _viewActions.replayCache.last()
        set(value) {
            _viewActions.tryEmit(value)
        }

    @Composable
    fun collectStates(): androidx.compose.runtime.State<State> =
        _viewStates.asStateFlow().collectAsState()

    @Composable
    fun collectActions(): androidx.compose.runtime.State<Action?> =
        _viewActions.asSharedFlow().collectAsState(null)

    abstract fun obtainEvent(viewEvent: Event)

    protected fun updateState(state: State) {
        _viewStates.value = state
    }

    protected fun updateState(block: RE<State, State>) = updateState(viewState.block())
    protected suspend fun safeUpdateState(block: RE<State, State>) =
        withContext(Main) { updateState(block) }

    protected fun setAction(action: Action? = null) {
        viewAction = action
    }

    fun clearAction() {
        viewAction = null
    }
}