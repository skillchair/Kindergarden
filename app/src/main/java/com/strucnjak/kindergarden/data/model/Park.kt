package com.strucnjak.kindergarden.data.model

data class Park(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val desc: String = "",
    val photoUrl: String = "",
    val type: String = "",
    val authorId: String = "",
    val timestamp: Long = 0L
)
