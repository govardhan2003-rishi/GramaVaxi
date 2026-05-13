package com.gramavaxi.ui.disease

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.gramavaxi.data.model.Animal
import com.gramavaxi.data.repository.AnimalRepository
import kotlinx.coroutines.launch

class DiseaseReportViewModel(private val repository: AnimalRepository) : ViewModel() {
    val animals: LiveData<List<Animal>> = repository.animals.asLiveData()
    private val _reportSubmitted = MutableLiveData(false)
    val reportSubmitted: LiveData<Boolean> = _reportSubmitted

    init {
        viewModelScope.launch {
            repository.syncFromFirebase()
        }
    }

    fun submitReport(
        animalId: Int,
        symptoms: List<String>,
        notes: String,
        farmerName: String,
        latitude: Double?,
        longitude: Double?,
        paymentMethod: String,
        paymentStatus: String,
        consultationFee: Int,
        assignedDoctorId: String,
        assignedDoctorName: String,
        assignedDoctorPhone: String
    ) {
        viewModelScope.launch {
            repository.reportDisease(
                animalId,
                symptoms,
                notes,
                farmerName,
                latitude,
                longitude,
                paymentMethod,
                paymentStatus,
                consultationFee,
                assignedDoctorId,
                assignedDoctorName,
                assignedDoctorPhone
            )
            _reportSubmitted.value = true
        }
    }

    fun clearSubmitted() {
        _reportSubmitted.value = false
    }
}
