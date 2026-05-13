package com.gramavaxi.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "disease_reports",
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
data class DiseaseReport(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val animalId: Int,
    val symptoms: String,
    val notes: String,
    val farmerName: String = "",
    val farmerPhone: String = "",
    val animalName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: String = "",
    val paymentStatus: String = "",
    val consultationFee: Int = 0,
    val assignedDoctorId: String = "",
    val assignedDoctorName: String = "",
    val assignedDoctorPhone: String = "",
    val reportedAt: Long = System.currentTimeMillis()
)
