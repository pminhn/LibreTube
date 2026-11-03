package com.github.libretube.ui.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.databinding.QueueRowBinding
import com.github.libretube.extensions.toID
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.ThemeHelper
import com.github.libretube.ui.viewholders.PlayingQueueViewHolder
import com.github.libretube.util.PlayingQueue

class PlayingQueueAdapter(
    private val onQueueItemSelected: (String) -> Unit
) : RecyclerView.Adapter<PlayingQueueViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayingQueueViewHolder {
        val binding = QueueRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PlayingQueueViewHolder(binding)
    }

    override fun getItemCount() = PlayingQueue.size()

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: PlayingQueueViewHolder, position: Int) {
        val streamItem = PlayingQueue.getStreams()[position]
        holder.binding.apply {
            ImageHelper.loadImage(streamItem.thumbnail, thumbnail)
            title.text = streamItem.title
            videoInfo.text = streamItem.uploaderName + "  •  " +
                DateUtils.formatElapsedTime(streamItem.duration ?: 0)

            val currentIndex = PlayingQueue.currentIndex()
            root.isSelected = currentIndex == position

            root.setOnClickListener {
                val newVideoId = streamItem.url?.toID() ?: return@setOnClickListener

                // Let the player service update the current item after navigation begins. This
                // preserves the previous index so forward jumps can schedule audio cleanup.
                onQueueItemSelected(newVideoId)
            }
        }
    }
}
