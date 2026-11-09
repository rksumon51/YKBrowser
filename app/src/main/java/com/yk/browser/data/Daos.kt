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

    @Query("SELECT * FROM saved_logins WHERE host = :host ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByHost(host: String): SavedLogin?

    @Query("SELECT * FROM saved_logins WHERE host = :host AND username = :username LIMIT 1")
    suspend fun findByHostAndUsername(host: String, username: String): SavedLogin?

    @Query("DELETE FROM saved_logins WHERE host = :host AND username = :username")
    suspend fun deleteByHostAndUsername(host: String, username: String)

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

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

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

    @Query("SELECT url FROM history WHERE url LIKE 'http%' GROUP BY url ORDER BY COUNT(*) DESC LIMIT 8")
    suspend fun getTopUrls(): List<String>

    @Query("SELECT title FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun getTitleFor(url: String): String?

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
