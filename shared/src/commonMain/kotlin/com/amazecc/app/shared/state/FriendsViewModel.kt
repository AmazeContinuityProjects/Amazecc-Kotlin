package com.amazecc.app.shared.state

import com.amazecc.app.shared.utils.Friend
import com.amazecc.app.shared.utils.SocialUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object FriendsViewModel {
    private val _friends = MutableStateFlow<List<Friend>>(emptyList())
    val friends: StateFlow<List<Friend>> = _friends

    fun addFriendFromCode(code: String, nickname: String = ""): Boolean {
        return try {
            val friend = SocialUtils.importScheduleCode(code, nickname)
            val current = _friends.value.toMutableList()
            // prevent duplicates
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
    }
}
