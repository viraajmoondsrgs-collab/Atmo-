package com.trio.atmo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {
    @Query("SELECT * FROM emails ORDER BY timestamp DESC")
    fun getAllEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id")
    suspend fun getEmailById(id: String): EmailEntity?

    @Query("SELECT * FROM emails WHERE subject LIKE '%' || :query || '%' OR sender LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchEmails(query: String): Flow<List<EmailEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Query("UPDATE emails SET isStarred = :isStarred WHERE id = :id")
    suspend fun updateStarredState(id: String, isStarred: Boolean)

    @Query("DELETE FROM emails WHERE id = :id")
    suspend fun deleteEmail(id: String)
}
