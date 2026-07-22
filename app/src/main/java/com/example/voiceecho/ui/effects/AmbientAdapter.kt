package com.example.voiceecho.ui.effects

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceecho.R
import com.example.voiceecho.data.AmbientSound

class AmbientAdapter(
    private val sounds: List<AmbientSound>,
    private val onSoundSelected: (AmbientSound) -> Unit
) : RecyclerView.Adapter<AmbientAdapter.AmbientViewHolder>() {

    private var selectedPosition = 0

    inner class AmbientViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: View = view.findViewById(R.id.cardRoot)
        val tvName: TextView = view.findViewById(R.id.tvAmbientName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AmbientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ambient_card, parent, false)
        return AmbientViewHolder(view)
    }

    override fun onBindViewHolder(holder: AmbientViewHolder, position: Int) {
        val sound = sounds[position]
        holder.tvName.text = sound.name

        val isSelected = position == selectedPosition
        holder.cardRoot.setBackgroundResource(
            if (isSelected) R.drawable.bg_effect_card_selected else R.drawable.bg_effect_card
        )

        holder.cardRoot.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onSoundSelected(sound)
        }
    }

    override fun getItemCount(): Int = sounds.size
}