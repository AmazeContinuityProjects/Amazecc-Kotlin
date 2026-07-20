package com.amazecc.app.shared.state

import com.amazecc.app.shared.utils.Friend
import com.amazecc.app.shared.utils.SocialUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

data class FriendGroup(
    val id: String,
    val name: String,
    val memberRegNumbers: List<String>,
    val createdAt: String
)

object FriendsViewModel {
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends

    private val _groups = MutableStateFlow<List<FriendGroup>>(emptyList())
    val groups: StateFlow<List<FriendGroup>> = _groups

    fun addFriendFromCode(code: String, nickname: String = ""): Boolean {
        return try {
            val friend = SocialUtils.importScheduleCode(code, nickname)
            val current = _friends.value.toMutableList()
            if (current.none { it.regNumber == friend.regNumber }) {
                current.add(friend)
                _friends.value = current
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
        _groups.value = _groups.value.map { g ->
            g.copy(memberRegNumbers = g.memberRegNumbers.filter { it != regNumber })
        }
    }

    fun createGroup(name: String, memberRegNumbers: List<String>) {
        val id = "group_${Clock.System.now().toEpochMilliseconds()}"
        val current = _groups.value.toMutableList()
        current.add(FriendGroup(id, name, memberRegNumbers, "Now"))
        _groups.value = current
    }

    fun deleteGroup(groupId: String) {
        _groups.value = _groups.value.filter { it.id != groupId }
    }

    fun addFriendToGroup(groupId: String, regNumber: String) {
        _groups.value = _groups.value.map { g ->
            if (g.id == groupId && regNumber !in g.memberRegNumbers) {
                g.copy(memberRegNumbers = g.memberRegNumbers + regNumber)
            } else g
        }
    }

    fun removeFriendFromGroup(groupId: String, regNumber: String) {
        _groups.value = _groups.value.map { g ->
            if (g.id == groupId) {
                g.copy(memberRegNumbers = g.memberRegNumbers.filter { it != regNumber })
            } else g
        }
    }
}
