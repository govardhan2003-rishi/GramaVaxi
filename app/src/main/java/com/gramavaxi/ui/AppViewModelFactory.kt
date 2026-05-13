package com.gramavaxi.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gramavaxi.data.db.AppDatabase
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.repository.AnimalRepository
import com.gramavaxi.ui.calendar.VaccineCalendarViewModel
import com.gramavaxi.ui.dashboard.DashboardViewModel
import com.gramavaxi.ui.disease.DiseaseReportViewModel
import com.gramavaxi.ui.ledger.AnimalLedgerViewModel
import com.gramavaxi.ui.payments.PaymentHistoryViewModel
import com.gramavaxi.util.SessionManager

class AppViewModelFactory private constructor(
    private val repository: AnimalRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) ->
                DashboardViewModel(repository) as T
            modelClass.isAssignableFrom(AnimalLedgerViewModel::class.java) ->
                AnimalLedgerViewModel(repository) as T
            modelClass.isAssignableFrom(VaccineCalendarViewModel::class.java) ->
                VaccineCalendarViewModel(repository) as T
            modelClass.isAssignableFrom(DiseaseReportViewModel::class.java) ->
                DiseaseReportViewModel(repository) as T
            modelClass.isAssignableFrom(PaymentHistoryViewModel::class.java) ->
                PaymentHistoryViewModel(repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }

    companion object {
        fun from(context: Context): AppViewModelFactory {
            val db = AppDatabase.getInstance(context)
            return AppViewModelFactory(
                AnimalRepository(
                    SessionManager.currentFarmerId(context),
                    SessionManager.currentFarmerPhone(context),
                    db.animalDao(),
                    db.vaccineDao(),
                    db.diseaseReportDao(),
                    FirebaseSyncService(context)
                )
            )
        }
    }
}
