package com.gramavaxi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gramavaxi.data.model.DiseaseReport
import kotlinx.coroutines.flow.Flow

@Dao
interface DiseaseReportDao {
    @Query("SELECT * FROM disease_reports ORDER BY reportedAt DESC")
    fun observeReports(): Flow<List<DiseaseReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: DiseaseReport): Long

    @Query("DELETE FROM disease_reports WHERE animalId = :animalId")
    suspend fun deleteForAnimal(animalId: Int)
}
