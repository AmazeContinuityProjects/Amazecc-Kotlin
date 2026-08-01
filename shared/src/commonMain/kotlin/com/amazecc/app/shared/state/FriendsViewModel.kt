package com.amazecc.app.shared.state

import com.amazecc.app.shared.repository.SettingsManager
import com.amazecc.app.shared.utils.Friend
import com.amazecc.app.shared.utils.SocialUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock

@Serializable
data class FriendGroup(
    val id: String,
    val name: String,
    val memberRegNumbers: List<String>,
    val createdAt: String
)

object FriendsViewModel {
    private val json = Json { ignoreUnknownKeys = true }

    private const val CACHE_KEY_FRIENDS = "friends_viewmodel_friends"
    private const val CACHE_KEY_GROUPS = "friends_viewmodel_groups"

    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends.asStateFlow()

    private val _groups = MutableStateFlow<List<FriendGroup>>(emptyList())
    val groups: StateFlow<List<FriendGroup>> = _groups.asStateFlow()

    init {
        loadCached()
    }

    private fun loadCached() {
        val friendsJson = SettingsManager.getString(CACHE_KEY_FRIENDS)
        if (friendsJson.isNotBlank()) {
            try {
                _friends.value = json.decodeFromString<List<Friend>>(friendsJson)
            } catch (_: Exception) { }
        }
        val groupsJson = SettingsManager.getString(CACHE_KEY_GROUPS)
        if (groupsJson.isNotBlank()) {
            try {
                _groups.value = json.decodeFromString<List<FriendGroup>>(groupsJson)
            } catch (_: Exception) { }
        }
    }

    private fun saveFriends() {
        try {
            SettingsManager.setString(CACHE_KEY_FRIENDS, json.encodeToString(_friends.value))
        } catch (_: Exception) { }
    }

    private fun saveGroups() {
        try {
            SettingsManager.setString(CACHE_KEY_GROUPS, json.encodeToString(_groups.value))
        } catch (_: Exception) { }
    }

    fun addFriendFromCode(code: String, nickname: String = ""): Boolean {
        return try {
            val friend = SocialUtils.importScheduleCode(code, nickname)
            val current = _friends.value.toMutableList()
            if (current.none { it.regNumber == friend.regNumber }) {
                current.add(friend)
                _friends.value = current
                saveFriends()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun removeFriend(regNumber: String) {
        val current = _friends.value.toMutableList()
        current.removeAll { it.regNumber == regNumber }
        _friends.value = current
        saveFriends()
        _groups.value = _groups.value.map { g ->
            g.copy(memberRegNumbers = g.memberRegNumbers.filter { it != regNumber })
        }
        saveGroups()
    }

    fun createGroup(name: String, memberRegNumbers: List<String>) {
        val id = "group_${Clock.System.now().toEpochMilliseconds()}"
        val current = _groups.value.toMutableList()
        current.add(FriendGroup(id, name, memberRegNumbers, "Now"))
        _groups.value = current
        saveGroups()
    }

    fun deleteGroup(groupId: String) {
        _groups.value = _groups.value.filter { it.id != groupId }
        saveGroups()
    }

    fun addFriendToGroup(groupId: String, regNumber: String) {
        _groups.value = _groups.value.map { g ->
            if (g.id == groupId && regNumber !in g.memberRegNumbers) {
                g.copy(memberRegNumbers = g.memberRegNumbers + regNumber)
            } else g
        }
        saveGroups()
    }

    fun removeFriendFromGroup(groupId: String, regNumber: String) {
        _groups.value = _groups.value.map { g ->
            if (g.id == groupId) {
                g.copy(memberRegNumbers = g.memberRegNumbers.filter { it != regNumber })
            } else g
        }
        saveGroups()
    }
}
