package com.example.voiceecho.ui.effects

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.voiceecho.R
import com.example.voiceecho.data.VoiceEffect

class EffectsAdapter(
    private val effects: List<VoiceEffect>,
    private val onEffectSelected: (VoiceEffect) -> Unit
) : RecyclerView.Adapter<EffectsAdapter.EffectViewHolder>() {

    private var selectedPosition = 0

    inner class EffectViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: View = view.findViewById(R.id.cardRoot)
        val ivAvatar: ImageView = view.findViewById(R.id.ivAvatar)
        val ivCrown: ImageView = view.findViewById(R.id.ivCrown)
        val ivSelectedCheck: ImageView = view.findViewById(R.id.ivSelectedCheck)
        val tvName: TextView = view.findViewById(R.id.tvEffectName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EffectViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_effect_card, parent, false)
        return EffectViewHolder(view)
    }

    override fun onBindViewHolder(holder: EffectViewHolder, position: Int) {
        val effect = effects[position]
        val context = holder.itemView.context

        holder.tvName.text = effect.displayName
        holder.ivAvatar.setImageResource(effect.iconRes)
        holder.ivAvatar.background.setTint(ContextCompat.getColor(context, effect.circleColorRes))
        holder.ivCrown.visibility = if (effect.isPremium) View.VISIBLE else View.GONE

        val isSelected = position == selectedPosition
        holder.ivSelectedCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.cardRoot.setBackgroundResource(
            if (isSelected) R.drawable.bg_effect_card_selected else R.drawable.bg_effect_card
        )
        holder.tvName.setTextColor(
            ContextCompat.getColor(context, if (isSelected) R.color.white else R.color.text_dark)
        )

        holder.cardRoot.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            onEffectSelected(effect)
        }
    }

    override fun getItemCount(): Int = effects.size

    fun getSelectedEffect(): VoiceEffect = effects[selectedPosition]
}