package com.gramavaxi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gramavaxi.data.model.Animal
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimalDao {
    @Query("SELECT * FROM animals WHERE farmerId = :farmerId ORDER BY nextShotDate ASC")
    fun observeAnimals(farmerId: Int): Flow<List<Animal>>

    @Query("SELECT * FROM animals WHERE id = :id LIMIT 1")
    suspend fun getAnimal(id: Int): Animal?

    @Query("SELECT * FROM animals WHERE uniqueAnimalId = :uniqueAnimalId LIMIT 1")
    suspend fun getAnimalByUniqueId(uniqueAnimalId: String): Animal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(animal: Animal): Long

    @Query("UPDATE animals SET isSick = :isSick WHERE id = :animalId")
    suspend fun updateSickStatus(animalId: Int, isSick: Boolean)

    @Query("DELETE FROM animals WHERE id = :animalId")
    suspend fun deleteAnimal(animalId: Int)
}
