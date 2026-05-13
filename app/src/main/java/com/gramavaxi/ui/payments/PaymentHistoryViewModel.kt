package com.gramavaxi.ui.payments

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.gramavaxi.data.model.DiseaseReport
import com.gramavaxi.data.repository.AnimalRepository

class PaymentHistoryViewModel(repository: AnimalRepository) : ViewModel() {
    val reports: LiveData<List<DiseaseReport>> = repository.diseaseReports.asLiveData()
}
