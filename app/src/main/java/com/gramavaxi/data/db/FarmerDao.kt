package com.gramavaxi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.gramavaxi.data.model.Farmer

@Dao
interface FarmerDao {
    @Query("SELECT * FROM farmers WHERE phone = :phone AND password = :password LIMIT 1")
    suspend fun login(phone: String, password: String): Farmer?

    @Query("SELECT * FROM farmers WHERE name = :name AND password = :password LIMIT 1")
    suspend fun loginByName(name: String, password: String): Farmer?

    @Query("SELECT * FROM farmers WHERE phone = :phone LIMIT 1")
    suspend fun findByPhone(phone: String): Farmer?

    @Query("SELECT * FROM farmers WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Farmer?

    @Query("SELECT * FROM farmers WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Farmer?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(farmer: Farmer): Long

    @Update
    suspend fun update(farmer: Farmer)
}
