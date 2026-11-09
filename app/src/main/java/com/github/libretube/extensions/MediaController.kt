package com.github.libretube.extensions

import androidx.annotation.OptIn
import androidx.core.os.bundleOf
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.github.libretube.enums.PlayerCommand
import com.github.libretube.services.AbstractPlayerService

@OptIn(UnstableApi::class)
fun MediaController.navigateVideo(videoId: String, playlistId: String? = null, replaceQueue: Boolean = false) {
    sendCustomCommand(
        AbstractPlayerService.runPlayerActionCommand,
        bundleOf(
            PlayerCommand.PLAY_VIDEO_BY_ID.name to videoId,
            com.github.libretube.constants.IntentData.playlistId to playlistId,
            com.github.libretube.constants.IntentData.replaceQueue to replaceQueue
        )
    )
}
