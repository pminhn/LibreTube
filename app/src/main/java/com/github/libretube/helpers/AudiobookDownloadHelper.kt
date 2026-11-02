package com.github.libretube.helpers

import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.github.libretube.api.obj.Streams
import com.github.libretube.db.DatabaseHolder.Database
import com.github.libretube.db.obj.DownloadWithItems
import com.github.libretube.enums.FileType
import com.github.libretube.parcelable.DownloadData
import com.github.libretube.services.DownloadService
import com.github.libretube.workers.AudiobookCleanupWorker
import java.util.concurrent.TimeUnit

object AudiobookDownloadHelper {
    const val ACTION_AUDIO_DOWNLOAD_COMPLETED = "com.github.libretube.AUDIOBOOK_AUDIO_DOWNLOAD_COMPLETED"
    const val EXTRA_VIDEO_ID = "video_id"
    private const val CLEANUP_PREFIX = "audiobook-cleanup-"
    private const val RETENTION_MS = 10 * 60 * 1000L

    /** Pick the smallest known audio file; if sizes are unavailable, use the lowest bitrate. */
    fun createDownloadData(videoId: String, streams: Streams): DownloadData? {
        val available = streams.audioStreams.filter {
            !it.url.isNullOrBlank() && !it.format.isNullOrBlank() && !it.quality.isNullOrBlank()
        }
        val selected = available.filter { it.contentLength > 0L }
            .minByOrNull { it.contentLength }
            ?: available.minByOrNull { it.bitrate ?: Int.MAX_VALUE }
            ?: return null

        return DownloadData(
            videoId = videoId,
            videoFormat = null,
            videoQuality = null,
            audioFormat = selected.format,
            audioQuality = selected.quality,
            audioLanguage = selected.audioTrackLocale,
            subtitleCode = null,
            audiobook = true
        )
    }

    suspend fun startDownload(context: Context, videoId: String, streams: Streams) {
        val existing = Database.downloadDao().findById(videoId)
        val audioItem = existing?.downloadItems.orEmpty().firstOrNull { it.type == FileType.AUDIO }
        if (audioItem != null) {
            Database.downloadDao().markAsAudiobook(videoId)
            if (!audioItem.isFinished) {
                DownloadHelper.resumeDownload(context, audioItem.id)
            } else {
                context.sendBroadcast(
                    Intent(ACTION_AUDIO_DOWNLOAD_COMPLETED)
                        .setPackage(context.packageName)
                        .putExtra(EXTRA_VIDEO_ID, videoId)
                )
            }
            return
        }

        val data = createDownloadData(videoId, streams) ?: return
        cancelCleanup(videoId)
        Database.downloadDao().setDeleteAfter(videoId, null)
        DownloadHelper.startDownloadService(context, data)
    }

    suspend fun cancelCleanup(videoId: String) {
        WorkManager.getInstance(com.github.libretube.LibreTubeApp.instance)
            .cancelUniqueWork(CLEANUP_PREFIX + videoId)
        Database.downloadDao().setDeleteAfter(videoId, null)
    }

    suspend fun scheduleCleanup(videoId: String) {
        val downloaded = Database.downloadDao().findById(videoId) ?: return
        if (!downloaded.download.audiobook) return
        if (downloaded.downloadItems.none { it.type == FileType.AUDIO && it.isFinished }) {
            WorkManager.getInstance(com.github.libretube.LibreTubeApp.instance)
                .cancelUniqueWork(CLEANUP_PREFIX + videoId)
            Database.downloadDao().setDeleteAfter(videoId, -1L)
            return
        }
        val cleanupAt = System.currentTimeMillis() + RETENTION_MS
        Database.downloadDao().setDeleteAfter(videoId, cleanupAt)

        val request = OneTimeWorkRequestBuilder<AudiobookCleanupWorker>()
            .setInitialDelay(RETENTION_MS, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(AudiobookCleanupWorker.VIDEO_ID, videoId).build())
            .build()
        WorkManager.getInstance(com.github.libretube.LibreTubeApp.instance)
            .enqueueUniqueWork(CLEANUP_PREFIX + videoId, androidx.work.ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun deleteIfDue(videoId: String) {
        val downloaded: DownloadWithItems = Database.downloadDao().findById(videoId) ?: return
        val due = downloaded.download.deleteAfterTimestamp ?: return
        if (due > System.currentTimeMillis()) return
        if (!downloaded.download.audiobook) return
        DownloadHelper.deleteDownloadIncludingFiles(downloaded)
    }
}
