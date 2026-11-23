package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DropLinkDao {
    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun observeAllTransfers(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentTransfers(limit: Int): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    fun observeTransferById(id: Long): Flow<TransferEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity): Long

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteTransferById(id: Long)

    @Query("DELETE FROM transfers")
    suspend fun clearAllTransfers()

    @Query("SELECT * FROM trusted_devices ORDER BY addedAt DESC")
    fun observeTrustedDevices(): Flow<List<TrustedDeviceEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM trusted_devices WHERE deviceId = :deviceId)")
    suspend fun isDeviceTrusted(deviceId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrustedDevice(device: TrustedDeviceEntity)

    @Query("DELETE FROM trusted_devices WHERE deviceId = :deviceId")
    suspend fun removeTrustedDevice(deviceId: String)
}
