package com.trio.atmo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trio.atmo.data.AtmoDatabase
import com.trio.atmo.data.EmailEntity
import com.trio.atmo.engine.TrackerStripper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmailViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AtmoDatabase.getDatabase(application).emailDao()

    private val _emails = MutableStateFlow<List<EmailEntity>>(emptyList())
    val emails: StateFlow<List<EmailEntity>> = _emails.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // Load initial mock data if database is empty
        viewModelScope.launch {
            dao.getAllEmails().collect { list ->
                if (list.isEmpty()) {
                    seedMockData()
                } else {
                    _emails.value = list
                }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
        viewModelScope.launch {
            if (newQuery.isBlank()) {
                dao.getAllEmails().collect { _emails.value = it }
            } else {
                dao.searchEmails(newQuery).collect { _emails.value = it }
            }
        }
    }

    fun toggleStar(email: EmailEntity) {
        viewModelScope.launch {
            dao.updateStarredState(email.id, !email.isStarred)
        }
    }

    fun deleteEmail(emailId: String) {
        viewModelScope.launch {
            dao.deleteEmail(emailId)
        }
    }

    private suspend fun seedMockData() {
        val rawBody1 = "<div>Hello Viraaj,<br>Here is your update.<img src='https://list-manage.com/track/open.gif?id=123' width='1' height='1'></div>"
        val rawBody2 = "<div>Your monthly security report is attached.<script>alert('test')</script></div>"

        val sampleList = listOf(
            EmailEntity(
                id = "1",
                threadId = "t1",
                sender = "GitHub Security",
                recipient = "me@atmo.app",
                subject = "New sign-in detected on Safari",
                body = TrackerStripper.sanitizeEmailHtml(rawBody2),
                snippet = "Your monthly security report is attached.",
                timestamp = System.currentTimeMillis() - 3600000,
                isUnread = true,
                isStarred = false
            ),
            EmailEntity(
                id = "2",
                threadId = "t2",
                sender = "Atmo Newsletter",
                recipient = "me@atmo.app",
                subject = "Privacy-First Email Client Launch",
                body = TrackerStripper.sanitizeEmailHtml(rawBody1),
                snippet = "Hello Viraaj, Here is your update.",
                timestamp = System.currentTimeMillis() - 86400000,
                isUnread = false,
                isStarred = true
            )
        )
        dao.insertEmails(sampleList)
    }
}
