package com.ydh.salvio.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.ydh.salvio.util.TokenCipher

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "salvio_prefs")

class TokenDataStore(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("github_token")
        private val ENCRYPTED_TOKEN_KEY = stringPreferencesKey("github_token_enc")
        private val SELECTED_REPOS_KEY = stringPreferencesKey("selected_repos")
        private val FAVORITE_REPOS_KEY = stringPreferencesKey("favorite_repos")
        private val WATCHED_REPOS_KEY = stringPreferencesKey("watched_repos")
        private val LAST_PR_IDS_KEY = stringPreferencesKey("last_pr_ids")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    // 암호화된 값이 있으면 그것을 복호화한다. 없으면 이전 버전의 평문 값을 사용하고,
    // 다음 로그인 성공 시 saveToken이 암호화 형식으로 옮긴다.
    val token: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[ENCRYPTED_TOKEN_KEY]?.let { TokenCipher.decrypt(it) } ?: prefs[TOKEN_KEY]
    }

    // 테마 모드 (SYSTEM / LIGHT / DARK). 미설정 시 null → SYSTEM으로 해석
    val themeMode: Flow<String?> = context.dataStore.data.map { it[THEME_MODE_KEY] }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE_KEY] = mode }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit {
            it[ENCRYPTED_TOKEN_KEY] = TokenCipher.encrypt(token)
            it.remove(TOKEN_KEY)
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit {
            it.remove(ENCRYPTED_TOKEN_KEY)
            it.remove(TOKEN_KEY)
        }
    }

    val selectedRepos: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_REPOS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun saveSelectedRepos(repos: List<String>) {
        context.dataStore.edit { it[SELECTED_REPOS_KEY] = repos.joinToString(",") }
    }

    // 즐겨찾기
    val favoriteRepos: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[FAVORITE_REPOS_KEY]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    suspend fun toggleFavorite(repoFullName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[FAVORITE_REPOS_KEY]
                ?.split(",")?.filter { it.isNotBlank() }?.toMutableSet() ?: mutableSetOf()
            if (current.contains(repoFullName)) current.remove(repoFullName)
            else current.add(repoFullName)
            prefs[FAVORITE_REPOS_KEY] = current.joinToString(",")
        }
    }

    // 알림 감시 대상 repo
    val watchedRepos: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[WATCHED_REPOS_KEY]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun setWatchedRepos(repos: List<String>) {
        context.dataStore.edit { it[WATCHED_REPOS_KEY] = repos.joinToString(",") }
    }

    // PR 알림: repoFullName -> 마지막으로 확인한 open PR 번호 집합.
    // 저장 형식: "owner/repo=1,2,3;other/repo=4,5" (repo는 ';', 번호는 ',' 구분)
    // 카운트가 아닌 번호 집합을 저장해, PR 하나가 열리고 하나가 닫혀 총 개수가
    // 같아도 새 PR을 정확히 감지한다.
    suspend fun getLastPrNumbers(repoFullName: String): Set<Int> {
        val raw = context.dataStore.data.first()[LAST_PR_IDS_KEY] ?: return emptySet()
        return raw.split(";")
            .firstOrNull { it.substringBefore("=") == repoFullName }
            ?.substringAfter("=", "")
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun saveLastPrNumbers(repoFullName: String, numbers: Set<Int>) {
        context.dataStore.edit { prefs ->
            val entries = (prefs[LAST_PR_IDS_KEY] ?: "").split(";")
                .filter { it.isNotBlank() && it.substringBefore("=") != repoFullName }
                .toMutableList()
            entries.add("$repoFullName=${numbers.sorted().joinToString(",")}")
            prefs[LAST_PR_IDS_KEY] = entries.joinToString(";")
        }
    }
}
