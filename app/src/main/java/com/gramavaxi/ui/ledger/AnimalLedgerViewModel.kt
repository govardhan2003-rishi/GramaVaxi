package com.gramavaxi.ui.ledger

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.gramavaxi.data.model.Animal
import com.gramavaxi.data.repository.AnimalRepository
import kotlinx.coroutines.launch

class AnimalLedgerViewModel(private val repository: AnimalRepository) : ViewModel() {
    val animals: LiveData<List<Animal>> = repository.animals.asLiveData()
    private val _savedAnimal = MutableLiveData<Animal?>()
    val savedAnimal: LiveData<Animal?> = _savedAnimal
    private val _deleteMessage = MutableLiveData<String?>()
    val deleteMessage: LiveData<String?> = _deleteMessage

    init {
        viewModelScope.launch {
            repository.syncFromFirebase()
        }
    }

    fun addAnimal(animal: Animal) {
        viewModelScope.launch {
            val id = repository.addAnimal(animal)
            _savedAnimal.value = animal.copy(id = id.toInt())
        }
    }

    fun deleteAnimal(animal: Animal) {
        viewModelScope.launch {
            runCatching {
                repository.deleteAnimal(animal)
            }.fold(
                onSuccess = { _deleteMessage.value = animal.name },
                onFailure = { _deleteMessage.value = "" }
            )
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

    fun clearSavedAnimal() {
        _savedAnimal.value = null
    }
}
