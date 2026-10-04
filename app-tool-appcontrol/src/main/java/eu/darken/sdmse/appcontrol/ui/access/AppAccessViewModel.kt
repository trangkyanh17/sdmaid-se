package eu.darken.sdmse.appcontrol.ui.access

import dagger.hilt.android.lifecycle.HiltViewModel
import eu.darken.sdmse.appcontrol.core.access.AppPermissionInspector
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.coroutine.DispatcherProvider
import eu.darken.sdmse.common.uix.ViewModel4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppAccessViewModel @Inject constructor(
    dispatcherProvider: DispatcherProvider,
    private val inspector: AppPermissionInspector,
) : ViewModel4(dispatcherProvider, tag = TAG) {

    private var boundRoute: AppAccessRoute? = null
    private val statePub = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = statePub

    fun bindRoute(route: AppAccessRoute) {
        if (boundRoute != null) return
        boundRoute = route

        launch {
            statePub.value = State.Loading
            try {
                statePub.value = inspector.inspect(route.installId)
                    ?.let(State::Ready)
                    ?: State.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                statePub.value = State.Error(e)
                errorEvents.emit(e)
            }
        }
    }

    sealed interface State {
        data object Loading : State
        data class Ready(val snapshot: AppPermissionSnapshot) : State
        data class Error(val cause: Throwable) : State
        data object NotFound : State
    }

    companion object {
        private const val TAG = "AppControl:Access"
    }
}
