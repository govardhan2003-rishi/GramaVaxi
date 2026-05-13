package com.gramavaxi.data.repository

import com.gramavaxi.data.db.AnimalDao
import com.gramavaxi.data.db.DiseaseReportDao
import com.gramavaxi.data.db.VaccineDao
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.model.Animal
import com.gramavaxi.data.model.DiseaseReport
import com.gramavaxi.data.model.VaccineRecord
import kotlinx.coroutines.flow.Flow

class AnimalRepository(
    private val farmerId: Int,
    private val farmerPhone: String,
    private val animalDao: AnimalDao,
    private val vaccineDao: VaccineDao,
    private val diseaseReportDao: DiseaseReportDao,
    private val firebaseSyncService: FirebaseSyncService
) {
    val animals: Flow<List<Animal>> = animalDao.observeAnimals(farmerId)
    val vaccineRecords: Flow<List<VaccineRecord>> = vaccineDao.observeRecords()
    val diseaseReports: Flow<List<DiseaseReport>> = diseaseReportDao.observeReports()

    suspend fun syncFromFirebase() {
        if (farmerPhone.isBlank()) return
        firebaseSyncService.fetchAnimals(farmerPhone).forEach { remoteAnimal ->
            val existing = animalDao.getAnimalByUniqueId(remoteAnimal.uniqueAnimalId)
            animalDao.insert(
                remoteAnimal.copy(
                    id = existing?.id ?: 0,
                    farmerId = farmerId
                )
            )
        }
    }

    suspend fun addAnimal(animal: Animal): Long {
        val animalId = animalDao.insert(animal)
        val savedAnimal = animal.copy(id = animalId.toInt())
        val vaccineId = vaccineDao.insert(
            VaccineRecord(
                animalId = savedAnimal.id,
                vaccineName = "Routine livestock vaccine",
                dateGiven = savedAnimal.lastVaccinationDate,
                nextDueDate = savedAnimal.nextShotDate,
                campLocation = "Temple Square"
            )
        )
        firebaseSyncService.uploadAnimal(farmerPhone.ifBlank { savedAnimal.ownerPhone }, savedAnimal)
        firebaseSyncService.uploadVaccineRecord(
            farmerPhone.ifBlank { savedAnimal.ownerPhone },
            VaccineRecord(
                id = vaccineId.toInt(),
                animalId = savedAnimal.id,
                vaccineName = "Routine livestock vaccine",
                dateGiven = savedAnimal.lastVaccinationDate,
                nextDueDate = savedAnimal.nextShotDate,
                campLocation = "Temple Square"
            )
        )
        return animalId
    }

    suspend fun deleteAnimal(animal: Animal) {
        diseaseReportDao.deleteForAnimal(animal.id)
        vaccineDao.deleteForAnimal(animal.id)
        animalDao.deleteAnimal(animal.id)
        firebaseSyncService.deleteAnimal(farmerPhone.ifBlank { animal.ownerPhone }, animal.uniqueAnimalId, animal.id)
    }

    suspend fun reportDisease(
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
        val animal = animalDao.getAnimal(animalId) ?: return
        val report = DiseaseReport(
            animalId = animalId,
            symptoms = symptoms.joinToString(", "),
            notes = notes,
            farmerName = farmerName,
            farmerPhone = farmerPhone.ifBlank { animal.ownerPhone },
            animalName = animal.name,
            latitude = latitude,
            longitude = longitude,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            consultationFee = consultationFee,
            assignedDoctorId = assignedDoctorId,
            assignedDoctorName = assignedDoctorName,
            assignedDoctorPhone = assignedDoctorPhone
        )
        val reportId = diseaseReportDao.insert(report)
        animalDao.updateSickStatus(animalId, true)
        val accountPhone = farmerPhone.ifBlank { animal.ownerPhone }
        firebaseSyncService.uploadDiseaseReport(accountPhone, report.copy(id = reportId.toInt()))
        firebaseSyncService.updateAnimalSickStatus(accountPhone, animal.uniqueAnimalId, true)
    }
}
