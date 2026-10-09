package com.trio.atmo.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val sender: String,
    val recipient: String,
    val subject: String,
    val body: String,
    val snippet: String,
    val timestamp: Long,
    val isUnread: Boolean,
    val isStarred: Boolean,
    val isSanitized: Boolean = true
)
