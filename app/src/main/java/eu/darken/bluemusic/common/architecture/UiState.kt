package eu.darken.bluemusic.common.architecture

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val throwable: Throwable) : UiState<Nothing>
}

fun <T> UiState<T>.successOrNull(): T? = (this as? UiState.Success)?.data

fun <T> UiState<T>.errorOrNull(): Throwable? = (this as? UiState.Error)?.throwable