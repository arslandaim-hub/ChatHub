package com.example.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: WhatsappMessage)

    @androidx.room.Delete
    suspend fun delete(message: WhatsappMessage)

    @Query("SELECT * FROM whatsapp_messages WHERE isGroupMessage = 0 ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<WhatsappMessage>>

    @Query("SELECT * FROM whatsapp_messages WHERE isGroupMessage = 1 ORDER BY timestamp DESC")
    fun getAllGroupMessages(): Flow<List<WhatsappMessage>>

    @Query("SELECT * FROM whatsapp_messages ORDER BY timestamp DESC")
    fun getAllMessagesUnified(): Flow<List<WhatsappMessage>>

    @Query("SELECT * FROM whatsapp_messages WHERE senderName = :sender ORDER BY timestamp DESC")
    fun getMessagesBySender(sender: String): Flow<List<WhatsappMessage>>

    @Query("SELECT DISTINCT senderName FROM whatsapp_messages")
    fun getAllSenders(): Flow<List<String>>

    @Query("DELETE FROM whatsapp_messages WHERE senderName = :sender AND isGroupMessage = 0")
    suspend fun deleteChatBySender(sender: String)

    @Query("DELETE FROM whatsapp_messages WHERE groupName = :groupName AND isGroupMessage = 1")
    suspend fun deleteChatByGroup(groupName: String)

    @Query("SELECT * FROM whatsapp_messages WHERE (senderName LIKE '%' || :query || '%' OR messageText LIKE '%' || :query || '%' OR groupName LIKE '%' || :query || '%') ORDER BY timestamp DESC")
    fun searchMessages(query: String): Flow<List<WhatsappMessage>>
}
