package com.gramavaxi.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "farmers",
    indices = [Index(value = ["phone"], unique = true)]
)
data class Farmer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val village: String,
    val phone: String,
    val password: String,
    val gender: String = "",
    val basicDetails: String = "",
    val dateOfBirth: String = "",
    val profilePhotoUri: String = "",
    val email: String = ""
)
