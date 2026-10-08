package com.yk.browser.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LoginDao {
    @Query("SELECT * FROM saved_logins ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SavedLogin>

    @Query("SELECT * FROM saved_logins WHERE host = :host LIMIT 1")
    suspend fun findByHost(host: String): SavedLogin?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(login: SavedLogin)

    @Delete
    suspend fun delete(login: SavedLogin)

    @Query("DELETE FROM saved_logins")
    suspend fun clear()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks")
    suspend fun clear()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    suspend fun getRecent(): List<HistoryEntry>

    @Query("SELECT DISTINCT url FROM history ORDER BY visitedAt DESC LIMIT 200")
    suspend fun getRecentUrls(): List<String>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface CookieDao {
    @Query("SELECT * FROM session_cookies")
    suspend fun getAll(): List<SessionCookie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cookie: SessionCookie)

    @Query("DELETE FROM session_cookies")
    suspend fun clear()
}
