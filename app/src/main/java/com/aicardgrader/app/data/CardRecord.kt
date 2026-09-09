package com.aicardgrader.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "card_records")
data class CardRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val cardName: String,
    val frontImagePath: String,
    val backImagePath: String?,

    val centering: Double,
    val corners: Double,
    val edges: Double,
    val surface: Double,

    val psaOverall: Double,
    val psaLabel: String,
    val bgsOverall: Double,
    val bgsLabel: String,
    val cgcOverall: Double,
    val cgcLabel: String,
    val sgcOverall: Double,
    val sgcLabel: String,

    val confidenceLevel: String
)
