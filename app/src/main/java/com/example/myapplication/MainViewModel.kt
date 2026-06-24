package com.example.myapplication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val messageDao = database.messageDao()
    private val themeManager = ThemeManager(application)

    val selectedMessageTheme: StateFlow<MessageTheme> = themeManager.selectedMessageTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MessageTheme.DEFAULT)

    val selectedAppTheme: StateFlow<AppBackgroundTheme> = themeManager.selectedAppTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppBackgroundTheme.NONE)

    val isChatLockEnabled: StateFlow<Boolean> = themeManager.isChatLockEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val chatLockPin: StateFlow<String?> = themeManager.chatLockPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isFingerprintEnabled: StateFlow<Boolean> = themeManager.isFingerprintEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val securityQuestion: StateFlow<String?> = themeManager.securityQuestion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val securityAnswer: StateFlow<String?> = themeManager.securityAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedDateFormat: StateFlow<DateFormatPreference> = themeManager.selectedDateFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DateFormatPreference.UK)

    fun setMessageTheme(theme: MessageTheme) {
        viewModelScope.launch {
            themeManager.setMessageTheme(theme)
        }
    }

    fun setAppTheme(theme: AppBackgroundTheme) {
        viewModelScope.launch {
            themeManager.setAppTheme(theme)
        }
    }

    fun setChatLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themeManager.setChatLockEnabled(enabled)
        }
    }

    fun setChatLockPin(pin: String) {
        viewModelScope.launch {
            themeManager.setChatLockPin(pin)
        }
    }

    fun setFingerprintEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themeManager.setFingerprintEnabled(enabled)
        }
    }

    fun setSecurityInfo(question: String, answer: String) {
        viewModelScope.launch {
            themeManager.setSecurityQuestion(question)
            themeManager.setSecurityAnswer(answer)
        }
    }

    fun setDateFormat(format: DateFormatPreference) {
        viewModelScope.launch {
            themeManager.setDateFormat(format)
        }
    }

    val allMessagesUnified: StateFlow<List<WhatsappMessage>> = messageDao.getAllMessagesUnified()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteMessage(message: WhatsappMessage) {
        viewModelScope.launch {
            messageDao.delete(message)
        }
    }

    fun deleteChat(name: String, isGroup: Boolean) {
        viewModelScope.launch {
            if (isGroup) {
                messageDao.deleteChatByGroup(name)
            } else {
                messageDao.deleteChatBySender(name)
            }
        }
    }

    /**
     * Sophisticated search system that filters and sorts messages.
     */
    fun searchMessages(
        query: String,
        date: String? = null,
        time: String? = null,
        filterGroups: Boolean? = null,
        sortByRecent: Boolean = true
    ): Flow<List<WhatsappMessage>> {
        return themeManager.selectedDateFormat.flatMapLatest { dateFormat ->
            messageDao.getAllMessagesUnified().map { messages ->
                val trimmedQuery = query.trim()
                val trimmedTime = time?.trim()?.lowercase()

                val filtered = messages.filter { msg ->
                    // 1. Filter by Group/Private if specified
                    if (filterGroups != null && msg.isGroupMessage != filterGroups) return@filter false

                    // 2. Filter by Search Query (Name or Text)
                    val matchesQuery = trimmedQuery.isBlank() || 
                        msg.senderName.contains(trimmedQuery, ignoreCase = true) || 
                        msg.messageText.contains(trimmedQuery, ignoreCase = true) || 
                        (msg.groupName?.contains(trimmedQuery, ignoreCase = true) ?: false)
                    
                    if (!matchesQuery) return@filter false

                    // 3. Filter by Date
                    if (!date.isNullOrBlank()) {
                        val sdfDate = SimpleDateFormat(dateFormat.pattern, Locale.getDefault())
                        val msgDate = sdfDate.format(Date(msg.timestamp))
                        if (msgDate != date) return@filter false
                    }

                    // 4. Filter by Time (HH:MM AM/PM)
                    if (!trimmedTime.isNullOrBlank()) {
                        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val msgTime = sdfTime.format(Date(msg.timestamp)).lowercase()
                        if (!msgTime.contains(trimmedTime)) return@filter false
                    }

                    true
                }

                if (sortByRecent) {
                    filtered.sortedByDescending { it.timestamp }
                } else {
                    filtered.sortedBy { msg ->
                        if (msg.isGroupMessage) (msg.groupName ?: msg.senderName) else msg.senderName
                    }
                }
            }
        }
    }
}
