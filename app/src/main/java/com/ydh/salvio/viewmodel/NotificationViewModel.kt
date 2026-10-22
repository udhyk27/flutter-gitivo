package com.ydh.salvio.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ydh.salvio.SalvioApplication
import com.ydh.salvio.data.model.GitHubNotification
import com.ydh.salvio.data.repository.GitHubRepository
import com.ydh.salvio.util.Logger
import com.ydh.salvio.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val isLoading: Boolean = false,
    val notifications: List<GitHubNotification> = emptyList(),
    val error: String? = null,
    val actionError: String? = null
)

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalvioApplication
    private val dataStore = app.tokenDataStore

    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state.asStateFlow()

    private suspend fun getRepo(): GitHubRepository? {
        val token = dataStore.token.first() ?: return null
        return app.githubRepository(token)
    }

    fun load() {
        viewModelScope.launch {
            // 로딩 중에도 기존 목록을 유지해 새로고침 시 화면이 비지 않게 한다.
            _state.value = _state.value.copy(isLoading = true, error = null)
            val repo = getRepo() ?: run {
                _state.value = _state.value.copy(isLoading = false, error = "로그인이 필요합니다. 다시 로그인해 주세요.")
                return@launch
            }
            repo.getNotifications().fold(
                onSuccess = { _state.value = NotificationUiState(notifications = it) },
                onFailure = { _state.value = _state.value.copy(isLoading = false, error = it.toUserMessage("알림을 불러오지 못했습니다.")) }
            )
        }
    }

    fun consumeActionError() {
        _state.update { it.copy(actionError = null) }
    }

    fun markRead(threadId: String) {
        viewModelScope.launch {
            val repo = getRepo() ?: return@launch
            // API 성공 시에만 UI를 읽음으로 반영 (실패 시 다음 새로고침에서 다시 나타나는 혼란 방지)
            repo.markNotificationRead(threadId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        notifications = _state.value.notifications.map {
                            if (it.id == threadId) it.copy(unread = false) else it
                        }
                    )
                }
                .onFailure { e ->
                    Logger.e("markRead $threadId failed", e)
                    _state.update { it.copy(actionError = e.toUserMessage("읽음 처리에 실패했습니다.")) }
                }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val repo = getRepo() ?: return@launch
            val succeeded = mutableSetOf<String>()
            var failed = 0
            _state.value.notifications.filter { it.unread }.forEach { n ->
                repo.markNotificationRead(n.id)
                    .onSuccess { succeeded.add(n.id) }
                    .onFailure { failed++ }
            }
            if (succeeded.isNotEmpty()) {
                _state.value = _state.value.copy(
                    notifications = _state.value.notifications.map {
                        if (it.id in succeeded) it.copy(unread = false) else it
                    }
                )
            }
            if (failed > 0) {
                _state.update { it.copy(actionError = "${failed}개의 알림을 읽음 처리하지 못했습니다.") }
            }
        }
    }
}
