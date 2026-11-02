package com.github.libretube.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.libretube.helpers.AudiobookDownloadHelper

class AudiobookCleanupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val videoId = inputData.getString(VIDEO_ID) ?: return Result.failure()
        return runCatching { AudiobookDownloadHelper.deleteIfDue(videoId) }
            .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        const val VIDEO_ID = "video_id"
    }
}
