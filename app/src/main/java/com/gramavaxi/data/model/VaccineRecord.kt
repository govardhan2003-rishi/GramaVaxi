package com.gramavaxi.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vaccine_records",
    foreignKeys = [
        ForeignKey(
            entity = Animal::class,
            parentColumns = ["id"],
            childColumns = ["animalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("animalId")]
)
data class VaccineRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val animalId: Int,
    val vaccineName: String,
    val dateGiven: Long,
    val nextDueDate: Long,
    val campLocation: String?
)
