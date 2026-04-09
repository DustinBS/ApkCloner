package com.example.appcloner

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clone_history")
data class CloneHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalPackageName: String,
    val clonedPackageName: String,
    val appName: String,
    val cloneDate: Long,
    val version: String,
    val outputPath: String
)