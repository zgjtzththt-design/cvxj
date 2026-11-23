package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfers")
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val taskId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val direction: String,
    val peerName: String,
    val peerAddress: String,
    val connectionMethod: String,
    val status: String,
    val sha256: String?,
    val integrityVerified: Boolean,
    val localUri: String?,
    val bytesTransferred: Long,
    val durationMs: Long,
    val averageSpeedBps: Long,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis()
)
