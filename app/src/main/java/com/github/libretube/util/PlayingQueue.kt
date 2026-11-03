package com.github.libretube.util

import androidx.media3.common.Player
import com.github.libretube.api.JsonHelper
import com.github.libretube.api.MediaServiceRepository
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.enums.PlaylistType
import com.github.libretube.extensions.move
import com.github.libretube.extensions.runCatchingIO
import com.github.libretube.extensions.toID
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.helpers.PlayerHelper
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.util.PlayingQueue.queueMode
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Collections

@Serializable
private data class PersistedQueue(
    val streams: List<StreamItem>,
    val currentVideoId: String?,
    val mode: PlayingQueueMode
)

object PlayingQueue {
    // queue is a synchronized list to be safely accessible from different coroutine threads
    private val queue = Collections.synchronizedList(mutableListOf<StreamItem>())
    private var currentStream: StreamItem? = null

    private val queueJobs = mutableListOf<Job>()
    private var restored = false
    private var storedQueueMode = PlayingQueueMode.ONLINE

    /**
     * Current use case of the queue. Do NOT add any offline videos while the [queueMode] is online
     * or vice versa.
     */
    var queueMode: PlayingQueueMode
        get() {
            restoreIfNeeded()
            return storedQueueMode
        }
        set(value) {
            restoreIfNeeded()
            storedQueueMode = value
            persist()
        }

    // wrapper around PlayerHelper#repeatMode for compatibility
    var repeatMode: Int
        get() = PlayerHelper.repeatMode
        set(value) {
            PlayerHelper.repeatMode = value
        }

    private fun clearJobs() {
        queueJobs.forEach {
            it.cancel()
        }
        queueJobs.clear()
    }

    fun clear(preservePersisted: Boolean = false) {
        restoreIfNeeded()
        clearJobs()
        if (!preservePersisted) {
            queue.clear()
            currentStream = null
            persist()
        }
    }

    /**
     * Remove all items after the current [StreamItem] from the queue
     *
     * I.e., the current and all previous streams are kept
     */
    fun clearAfterCurrent() {
        clearJobs()
        synchronized(queue) {
            val newQueue = queue.filterIndexed { index, item -> index <= currentIndex() }
            setStreams(newQueue)
        }
    }

    /**
     * @param skipExisting Whether to skip the [streamItem] if it's already part of the queue
     */
    fun add(vararg streamItem: StreamItem, skipExisting: Boolean = false) = synchronized(queue) {
        restoreIfNeeded()
        for (stream in streamItem) {
            if ((skipExisting && contains(stream)) || stream.title.isNullOrBlank()) continue

            queue.remove(stream)
            queue.add(stream)
        }
        persist()
    }

    fun addAsNext(streamItem: StreamItem) = synchronized(queue) {
        restoreIfNeeded()
        if (currentStream == streamItem) return
        if (queue.contains(streamItem)) queue.remove(streamItem)
        queue.add(currentIndex() + 1, streamItem)
        persist()
    }

    // return the next item, or if repeating enabled and no video left, the first one of the queue
    fun getNext(): String? = synchronized(queue) {
        restoreIfNeeded()
        val nextItem = queue.getOrNull(currentIndex() + 1)
        if (nextItem != null) return nextItem.url?.toID()

        if (repeatMode == Player.REPEAT_MODE_ALL) return queue.firstOrNull()?.url?.toID()

        return null
    }

    // return the previous item, or if repeating enabled and no video left, the last one of the queue
    fun getPrev(): String? = synchronized(queue) {
        restoreIfNeeded()
        val prevItem = queue.getOrNull(currentIndex() - 1)
        if (prevItem != null) return prevItem.url?.toID()

        if (repeatMode == Player.REPEAT_MODE_ALL) return queue.lastOrNull()?.url?.toID()

        return null
    }

    fun hasPrev() = getPrev() != null

    fun hasNext() = getNext() != null

    fun updateCurrent(streamItem: StreamItem) = synchronized(queue) {
        restoreIfNeeded()
        currentStream = streamItem

        if (!contains(streamItem)) add(streamItem)
        persist()
    }

    fun isNotEmpty(): Boolean { restoreIfNeeded(); return queue.isNotEmpty() }

    fun isEmpty(): Boolean { restoreIfNeeded(); return queue.isEmpty() }

    fun size(): Int { restoreIfNeeded(); return queue.size }

    fun currentIndex(): Int = synchronized(queue) {
        restoreIfNeeded()
        return queue.indexOfFirst {
            it.url?.toID() == currentStream?.url?.toID()
        }.takeIf { it >= 0 } ?: 0
    }

    fun getCurrent(): StreamItem? { restoreIfNeeded(); return currentStream }

    fun contains(streamItem: StreamItem) = synchronized(queue) {
        restoreIfNeeded()
        queue.any { it.url?.toID() == streamItem.url?.toID() }
    }

    // only returns a copy of the queue, no write access
    fun getStreams(): List<StreamItem> { restoreIfNeeded(); return queue.toList() }

    fun setStreams(streams: List<StreamItem>) = synchronized(queue) {
        restoreIfNeeded()
        queue.clear()

        queue.addAll(streams)
        if (currentStream?.url?.toID() !in streams.map { it.url?.toID() }) currentStream = null
        persist()
    }

    fun remove(index: Int) = synchronized(queue) {
        restoreIfNeeded()
        queue.removeAt(index)
        persist()
        return@synchronized
    }

    fun move(from: Int, to: Int) = synchronized(queue) {
        restoreIfNeeded()
        queue.move(from, to)
        persist()
    }

    fun indexOf(videoId: String): Int {
        restoreIfNeeded()
        return queue.indexOfFirst { it.url?.toID() == videoId.toID() }
    }

    fun isForwardJump(fromVideoId: String, toVideoId: String): Boolean {
        restoreIfNeeded()
        val fromIndex = indexOf(fromVideoId)
        val toIndex = indexOf(toVideoId)
        if (fromIndex < 0 || toIndex < 0) return false
        return toIndex > fromIndex ||
            (repeatMode == Player.REPEAT_MODE_ALL && toIndex == 0 && fromIndex == queue.lastIndex)
    }

    private fun restoreIfNeeded() {
        if (restored) return
        restored = true
        val savedState = runCatching {
            PreferenceHelper.getString(PreferenceKeys.PLAY_QUEUE_STATE, "")
        }.getOrDefault("")
        val state = runCatching { JsonHelper.json.decodeFromString<PersistedQueue>(savedState) }
            .getOrNull() ?: return
        queue.addAll(state.streams)
        currentStream = queue.firstOrNull { it.url?.toID() == state.currentVideoId?.toID() }
        storedQueueMode = state.mode
    }

    private fun persist() {
        val state = PersistedQueue(
            streams = queue.toList(),
            currentVideoId = currentStream?.url?.toID(),
            mode = storedQueueMode
        )
        runCatching {
            PreferenceHelper.putString(PreferenceKeys.PLAY_QUEUE_STATE, JsonHelper.json.encodeToString(state))
        }
    }

    /**
     * Adds a list of videos to the current queue while updating the position of the current stream
     * @param isMainList whether the videos are part of the list that initially has been used to
     * start the queue, either from a channel or playlist. If it's false, the current stream won't
     * be touched, since it's an independent list.
     */
    private fun addToQueueAsync(
        streams: List<StreamItem>, currentStreamItem: StreamItem? = null, isMainList: Boolean = true
    ) = synchronized(queue) {
        if (!isMainList) {
            add(*streams.toTypedArray())
            return
        }
        val currentStream = currentStreamItem ?: this.currentStream
        // if the stream already got added to the queue earlier, although it's not yet
        // been found in the playlist, remove it and re-add it later
        var reAddStream = true
        if (currentStream != null && streams.any { it.url?.toID() == currentStream.url?.toID() }) {
            queue.removeAll { it.url?.toID() == currentStream.url?.toID() }
            reAddStream = false
        }
        // add all new stream items to the queue
        add(*streams.toTypedArray())

        if (currentStream != null && reAddStream) {
            // re-add the stream to the end of the queue
            updateCurrent(currentStream)
        }
    }

    private suspend fun fetchMoreFromPlaylist(
        playlistId: String,
        nextPage: String?,
        isMainList: Boolean
    ) {
        var playlistNextPage = nextPage
        while (playlistNextPage != null) {
            MediaServiceRepository.instance.getPlaylistNextPage(playlistId, playlistNextPage).run {
                addToQueueAsync(relatedStreams, isMainList = isMainList)
                playlistNextPage = this.nextpage
            }
        }
    }

    fun insertPlaylist(playlistId: String, playlistType: PlaylistType, newCurrentStream: StreamItem?) = runCatchingIO {
        val playlist = PlaylistsHelper.getPlaylist(playlistId, playlistType)
        val isMainList = newCurrentStream != null
        addToQueueAsync(playlist.relatedStreams, newCurrentStream, isMainList)
        if (playlist.nextpage == null) return@runCatchingIO
        fetchMoreFromPlaylist(playlistId, playlist.nextpage, isMainList)
    }.let { queueJobs.add(it) }

    private suspend fun fetchMoreFromChannel(channelId: String, nextPage: String?) {
        var channelNextPage = nextPage
        var pageIndex = 1
        while (channelNextPage != null && pageIndex < 10) {
            MediaServiceRepository.instance.getChannelNextPage(channelId, channelNextPage).run {
                addToQueueAsync(relatedStreams)
                channelNextPage = this.nextpage
                pageIndex++
            }
        }
    }

    private fun insertChannel(channelId: String, newCurrentStream: StreamItem) = runCatchingIO {
        val channel = MediaServiceRepository.instance.getChannel(channelId)
        addToQueueAsync(channel.relatedStreams, newCurrentStream)
        if (channel.nextpage == null) return@runCatchingIO
        fetchMoreFromChannel(channelId, channel.nextpage)
    }.let { queueJobs.add(it) }

    fun insertByVideoId(videoId: String) = runCatchingIO {
        val streams = MediaServiceRepository.instance.getStreams(videoId.toID())
        add(streams.toStreamItem(videoId))
    }

    fun updateQueue(
        streamItem: StreamItem,
        playlistId: String?,
        channelId: String?,
    ) {
        updateCurrent(streamItem)

        if (playlistId != null) {
            val playlistType = PlaylistsHelper.getPlaylistType(playlistId)
            insertPlaylist(playlistId, playlistType, streamItem)
        } else if (channelId != null) {
            insertChannel(channelId, streamItem)
        }
    }
}

@Serializable
enum class PlayingQueueMode {
    ONLINE,
    OFFLINE
}
