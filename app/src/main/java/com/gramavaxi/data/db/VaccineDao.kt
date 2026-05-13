package com.gramavaxi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gramavaxi.data.model.VaccineRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    @Query("SELECT * FROM vaccine_records ORDER BY nextDueDate ASC")
    fun observeRecords(): Flow<List<VaccineRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: VaccineRecord): Long

    @Query("DELETE FROM vaccine_records WHERE animalId = :animalId")
    suspend fun deleteForAnimal(animalId: Int)
}
