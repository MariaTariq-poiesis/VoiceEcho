package com.example.voiceecho.ui.saved

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceecho.R
import com.example.voiceecho.data.SavedRecording
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedFilesAdapter(
    private val items: MutableList<SavedRecording>,
    private val onPlayClicked: (SavedRecording) -> Unit,
    private val onRenameClicked: (SavedRecording) -> Unit,
    private val onDeleteClicked: (SavedRecording) -> Unit,
    private val onShareClicked: (SavedRecording) -> Unit
) : RecyclerView.Adapter<SavedFilesAdapter.SavedViewHolder>() {

    private var currentlyPlayingPath: String? = null

    inner class SavedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: View = view.findViewById(R.id.itemRoot)
        val tvName: TextView = view.findViewById(R.id.tvItemName)
        val tvMeta: TextView = view.findViewById(R.id.tvItemMeta)
        val btnPlay: ImageButton = view.findViewById(R.id.btnItemPlay)
        val btnMore: ImageButton = view.findViewById(R.id.btnItemMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_recording, parent, false)
        return SavedViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvMeta.text = "${formatSize(item.sizeBytes)} • ${formatDate(item.lastModified)}"

        val isThisPlaying = item.file.absolutePath == currentlyPlayingPath

        holder.root.setBackgroundResource(
            if (isThisPlaying) R.drawable.bg_saved_item_playing else R.drawable.bg_saved_item
        )
        holder.btnPlay.setImageResource(
            if (isThisPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        holder.tvName.setTextColor(
            holder.itemView.context.getColor(
                if (isThisPlaying) R.color.primary_blue else R.color.text_dark
            )
        )

        holder.btnPlay.setOnClickListener { onPlayClicked(item) }

        holder.btnMore.setOnClickListener { anchor ->
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.add(0, 1, 0, anchor.context.getString(R.string.menu_share))
            popup.menu.add(0, 2, 1, anchor.context.getString(R.string.menu_rename))
            popup.menu.add(0, 3, 2, anchor.context.getString(R.string.menu_delete))
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onShareClicked(item)
                    2 -> onRenameClicked(item)
                    3 -> onDeleteClicked(item)
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<SavedRecording>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun setPlayingPath(path: String?) {
        currentlyPlayingPath = path
        notifyDataSetChanged()
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        return if (kb < 1024) String.format(Locale.US, "%.1f KB", kb)
        else String.format(Locale.US, "%.1f MB", kb / 1024.0)
    }

    private fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.US)
        return sdf.format(Date(millis))
    }
}