package com.gramavaxi.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "animals")
data class Animal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uniqueAnimalId: String,
    val farmerId: Int,
    val name: String,
    val type: String,
    val breed: String,
    val ageMonths: Int,
    val ownerPhone: String,
    val photoUri: String?,
    val lastVaccinationDate: Long,
    val nextShotDate: Long,
    val isSick: Boolean = false
)
