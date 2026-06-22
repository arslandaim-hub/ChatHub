package com.example.myapplication

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "whatsapp_messages",
    indices = [Index(value = ["senderName", "messageText", "timestamp", "isGroupMessage"], unique = true)]
)
data class WhatsappMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderName: String,
    val messageText: String,
    val timestamp: Long,
    val isGroupMessage: Boolean = false,
    val groupName: String? = null
)
