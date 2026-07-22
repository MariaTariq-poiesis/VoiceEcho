package com.example.voiceecho.data

data class AmbientSound(
    val name: String,
    val assetFileName: String
)

object AmbientSounds {
    val ALL = listOf(
        AmbientSound("None", ""),
        AmbientSound("Fishing Village", "ambient_fishing_village.mp3"),
        AmbientSound("Forest Night", "ambient_forest_night.mp3"),
        AmbientSound("Heavy Rain", "ambient_heavy_rain.mp3"),
        AmbientSound("Meadow", "ambient_meadow.mp3"),
        AmbientSound("Meditation", "ambient_meditation.mp3"),
        AmbientSound("Rain", "ambient_rain.mp3"),
        AmbientSound("Sea", "ambient_sea.mp3"),
        AmbientSound("Snow Mountain", "ambient_snow_mountain.mp3"),
        AmbientSound("Summer Night", "ambient_summer_night.mp3"),
        AmbientSound("Thunder", "ambient_thunder.mp3"),
        AmbientSound("Village", "ambient_village.mp3")
    )
}