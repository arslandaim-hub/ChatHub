package com.example.myapplication

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WhatsappNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == "com.whatsapp") {
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE)
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val timestamp = sbn.postTime

            if (!title.isNullOrBlank() && !text.isNullOrBlank()) {
                // Filter out summary/grouped notifications
                val isSummary = text.matches(Regex("\\d+ new messages?")) || 
                                text.contains("new message from", ignoreCase = true) ||
                                title.equals("WhatsApp", ignoreCase = true) ||
                                (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)
                
                if (!isSummary) {
                    val rawGroupName = extras.getString(Notification.EXTRA_CONVERSATION_TITLE)
                    val isGroupExtra = !rawGroupName.isNullOrBlank()
                    
                    var sender = title.trim()
                    val messageText = text.trim()
                    var groupName: String? = rawGroupName?.trim()
                    var isGroup = isGroupExtra

                    // Refined logic for group detection and name extraction
                    if (isGroupExtra) {
                        // Definitively a group
                        isGroup = true
                        if (title.contains("@")) {
                            // Pattern: "Sender @ Group Name"
                            val parts = title.split("@").map { it.trim() }
                            if (parts.size >= 2) {
                                sender = parts[0]
                                // Use the title's version of group name if it's cleaner
                                if (parts[1].isNotBlank()) groupName = parts[1]
                            }
                        } else if (title.contains(":")) {
                            // Pattern: "Group Name: Sender" or "Sender: Group Name"
                            val parts = title.split(":").map { it.trim() }
                            if (parts.size >= 2) {
                                if (parts[0].equals(groupName, ignoreCase = true)) {
                                    sender = parts[1]
                                } else if (parts[1].equals(groupName, ignoreCase = true)) {
                                    sender = parts[0]
                                }
                            }
                        }
                    } else {
                        // Fallback detection for groups without EXTRA_CONVERSATION_TITLE
                        if (title.contains("@")) {
                            val parts = title.split("@").map { it.trim() }
                            if (parts.size >= 2) {
                                isGroup = true
                                sender = parts[0]
                                groupName = parts[1]
                            }
                        } else if (title.contains(":") && title.contains(Regex("\\w+:\\s+\\w+"))) {
                            // Heuristic: common "Group: Sender" pattern in notifications
                            val parts = title.split(":").map { it.trim() }
                            if (parts.size >= 2) {
                                isGroup = true
                                groupName = parts[0]
                                sender = parts[1]
                            }
                        }
                    }

                    // Final safety check: if it's personal but somehow has a groupName, clear it
                    if (!isGroup) {
                        groupName = null
                    }

                    Log.d("WhatsappListener", "Robust -> Sender: $sender, Group: $groupName, IsGroup: $isGroup")
                    saveMessage(sender, messageText, timestamp, isGroup, groupName)
                }
            }
        }
    }

    private fun saveMessage(sender: String, message: String, timestamp: Long, isGroup: Boolean, groupName: String?) {
        serviceScope.launch {
            val database = AppDatabase.getDatabase(applicationContext)
            val messageEntity = WhatsappMessage(
                senderName = sender,
                messageText = message,
                timestamp = timestamp,
                isGroupMessage = isGroup,
                groupName = groupName
            )
            database.messageDao().insert(messageEntity)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Handle if needed
    }
}
