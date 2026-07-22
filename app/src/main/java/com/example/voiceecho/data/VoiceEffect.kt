package com.example.voiceecho.data

import com.example.voiceecho.R

enum class VoiceEffectType {
    NONE, YOUNG_GIRL, LADY, WOMAN, CHILD_GIRL, CHILD_BOY,
    MAN, MONSTER, FAST_SOUND, DRUNK, HELIUM, GIANT
}

data class VoiceEffect(
    val type: VoiceEffectType,
    val displayName: String,
    val pitch: Float,
    val speed: Float,
    val isPremium: Boolean = false,
    val circleColorRes: Int,
    val iconRes: Int
)

object VoiceEffects {
    val ALL = listOf(
        VoiceEffect(VoiceEffectType.NONE, "Default", 1.0f, 1.0f, false,
            R.color.effect_lightblue, android.R.drawable.ic_btn_speak_now),
        VoiceEffect(VoiceEffectType.YOUNG_GIRL, "Young Girl", 1.6f, 1.05f, true,
            R.color.effect_pink, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.LADY, "Lady", 1.3f, 1.0f, true,
            R.color.effect_green, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.WOMAN, "Woman", 1.2f, 1.0f, true,
            R.color.effect_orange, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.CHILD_GIRL, "Child Girl", 1.8f, 1.1f, true,
            R.color.effect_lightgreen, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.CHILD_BOY, "Child Boy", 1.7f, 1.1f, true,
            R.color.effect_teal, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.MAN, "Man", 0.75f, 1.0f, true,
            R.color.effect_lightgreen, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.MONSTER, "Monster", 0.5f, 0.85f, true,
            R.color.effect_yellowgreen, android.R.drawable.ic_menu_help),
        VoiceEffect(VoiceEffectType.FAST_SOUND, "Fast Sound", 1.0f, 1.6f, true,
            R.color.effect_lightgreen, android.R.drawable.ic_menu_directions),
        VoiceEffect(VoiceEffectType.DRUNK, "Drunk", 0.9f, 0.8f, true,
            R.color.effect_brown, android.R.drawable.ic_menu_myplaces),
        VoiceEffect(VoiceEffectType.HELIUM, "Helium", 2.0f, 1.15f, true,
            R.color.effect_yellow, android.R.drawable.ic_menu_compass),
        VoiceEffect(VoiceEffectType.GIANT, "Giant", 0.6f, 0.9f, true,
            R.color.effect_maroon, android.R.drawable.ic_menu_help)
    )
}