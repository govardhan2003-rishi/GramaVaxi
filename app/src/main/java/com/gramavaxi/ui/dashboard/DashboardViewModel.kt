package com.gramavaxi.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.gramavaxi.data.model.Animal
import com.gramavaxi.data.repository.AnimalRepository
import kotlinx.coroutines.launch

class DashboardViewModel(private val repository: AnimalRepository) : ViewModel() {
    val animals: LiveData<List<Animal>> = repository.animals.asLiveData()

    init {
        viewModelScope.launch {
            repository.syncFromFirebase()
        }
    }
}
