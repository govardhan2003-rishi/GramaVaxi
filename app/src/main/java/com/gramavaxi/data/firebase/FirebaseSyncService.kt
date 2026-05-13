package com.gramavaxi.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.gramavaxi.data.model.Animal
import com.gramavaxi.data.model.DiseaseReport
import com.gramavaxi.data.model.Farmer
import com.gramavaxi.data.model.VaccineRecord
import kotlinx.coroutines.tasks.await

class FirebaseSyncService(context: Context) {
    private val appContext = context.applicationContext

    init {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
    }

    private val firestore: FirebaseFirestore?
        get() = if (FirebaseApp.getApps(appContext).isEmpty()) {
            null
        } else {
            FirebaseFirestore.getInstance()
        }

    private val auth: FirebaseAuth?
        get() = if (FirebaseApp.getApps(appContext).isEmpty()) {
            null
        } else {
            FirebaseAuth.getInstance()
        }

    fun isConfigured(): Boolean = FirebaseApp.getApps(appContext).isNotEmpty()

    suspend fun findFarmerByPhone(phone: String): Farmer? {
        val db = firestore ?: return null
        return runCatching {
            val snapshot = db.collection(FARMERS)
                .whereEqualTo("phone", phone)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toFarmer()
        }.getOrNull()
    }

    suspend fun findFarmerByName(name: String): Farmer? {
        val db = firestore ?: return null
        return runCatching {
            val snapshot = db.collection(FARMERS)
                .whereEqualTo("name", name)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toFarmer()
        }.getOrNull()
    }

    suspend fun login(phone: String, password: String): Farmer? {
        val firebaseAuth = auth ?: return null
        val authEmail = authEmailForPhone(phone) ?: return null
        firebaseAuth.signInWithEmailAndPassword(authEmail, password).await()
        return findFarmerByPhone(phone)
    }

    suspend fun loginDoctor(phone: String, password: String): VetDoctor? {
        val firebaseAuth = auth ?: return null
        val authEmail = authEmailForPhone(phone) ?: return null
        firebaseAuth.signInWithEmailAndPassword(authEmail, password).await()
        return findVetDoctorByPhone(phone)
    }

    suspend fun registerFarmer(farmer: Farmer): FirebaseWriteResult {
        val firebaseAuth = auth ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            firebaseAuth.createUserWithEmailAndPassword(authEmailForFarmer(farmer), farmer.password).await()
        }.fold(
            onSuccess = { uploadFarmer(farmer, it.user?.uid) },
            onFailure = { error ->
                val existingUser = error.message?.contains("email address is already in use", ignoreCase = true) == true
                if (existingUser) {
                    uploadFarmer(farmer)
                } else {
                    FirebaseWriteResult(false, error.message ?: error.javaClass.simpleName)
                }
            }
        )
    }

    suspend fun uploadFarmer(farmer: Farmer, authUid: String? = auth?.currentUser?.uid): FirebaseWriteResult {
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            db.collection(FARMERS)
                .document(documentId(farmer.phone))
                .set(farmer.toMap(authUid))
                .await()
            if (!authUid.isNullOrBlank()) {
                db.collection(USERS)
                    .document(authUid)
                    .set(farmer.toUserMap(authUid, "farmer"), com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }
            upsertAuthLookup(farmer.phone, authEmailForFarmer(farmer), "farmer", authUid)
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    suspend fun uploadAnimal(accountPhone: String, animal: Animal) {
        val db = firestore ?: return
        runCatching {
            db.collection(FARMERS)
                .document(documentId(accountPhone))
                .collection(ANIMALS)
                .document(animal.uniqueAnimalId)
                .set(animal.toMap())
                .await()
        }
    }

    suspend fun fetchAnimals(farmerPhone: String): List<Animal> {
        val db = firestore ?: return emptyList()
        return runCatching {
            db.collection(FARMERS)
                .document(documentId(farmerPhone))
                .collection(ANIMALS)
                .get()
                .await()
                .documents
                .mapNotNull { it.toAnimal() }
        }.getOrDefault(emptyList())
    }

    suspend fun uploadVaccineRecord(ownerPhone: String, record: VaccineRecord) {
        val db = firestore ?: return
        runCatching {
            db.collection(FARMERS)
                .document(documentId(ownerPhone))
                .collection(VACCINES)
                .document(record.id.toString())
                .set(record.toMap())
                .await()
        }
    }

    suspend fun uploadDiseaseReport(ownerPhone: String, report: DiseaseReport) {
        val db = firestore ?: return
        val assignedDoctor = if (report.assignedDoctorPhone.isBlank()) {
            findNearestDoctor(report.latitude, report.longitude)
        } else {
            null
        }
        val assignedReport = report.copy(
            assignedDoctorId = report.assignedDoctorId.ifBlank { assignedDoctor?.id.orEmpty() },
            assignedDoctorName = report.assignedDoctorName.ifBlank { assignedDoctor?.name.orEmpty() },
            assignedDoctorPhone = report.assignedDoctorPhone.ifBlank { assignedDoctor?.phone.orEmpty() }
        )
        runCatching {
            db.collection(FARMERS)
                .document(documentId(ownerPhone))
                .collection(DISEASE_REPORTS)
                .document(assignedReport.id.toString())
                .set(assignedReport.toMap())
                .await()
        }
        uploadVetSickReport(ownerPhone, assignedReport)
    }

    private suspend fun uploadVetSickReport(ownerPhone: String, report: DiseaseReport) {
        val db = firestore ?: return
        runCatching {
            db.collection(VET_SICK_REPORTS)
                .document("${documentId(ownerPhone)}_${report.id}")
                .set(report.toMap() + ("ownerPhone" to ownerPhone))
                .await()
        }
    }

    suspend fun fetchVetSickReports(): List<VetSickReport> {
        val db = firestore ?: return emptyList()
        return runCatching {
            db.collection(VET_SICK_REPORTS)
                .orderBy("reportedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toVetSickReport() }
        }.getOrDefault(emptyList())
    }

    fun listenVetSickReports(doctorPhone: String, onReports: (List<VetSickReport>) -> Unit): ListenerRegistration? {
        val db = firestore ?: return null
        val query = if (doctorPhone.isBlank()) {
            db.collection(VET_SICK_REPORTS)
        } else {
            db.collection(VET_SICK_REPORTS).whereEqualTo("assignedDoctorPhone", doctorPhone)
        }
        return query.addSnapshotListener { snapshot, _ ->
            val reports = snapshot?.documents
                ?.mapNotNull { it.toVetSickReport() }
                ?.sortedByDescending { it.reportedAt }
                .orEmpty()
            onReports(reports)
        }
    }

    suspend fun registerVetDoctor(name: String, phone: String, latitude: Double, longitude: Double): FirebaseWriteResult {
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            db.collection(VETERINARY_DOCTORS)
                .document(documentId(phone))
                .set(
                    mapOf(
                        "name" to name,
                        "phone" to phone,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    suspend fun createVetDoctorAccount(
        name: String,
        phone: String,
        password: String,
        gender: String,
        dateOfBirth: String,
        profilePhotoUri: String,
        email: String
    ): FirebaseWriteResult {
        val firebaseAuth = auth ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        val authUid = runCatching {
            firebaseAuth.createUserWithEmailAndPassword(authEmailForProfile(email, phone), password).await().user?.uid
        }.getOrNull()
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            db.collection(VETERINARY_DOCTORS)
                .document(documentId(phone))
                .set(
                    mapOf(
                        "authUid" to authUid,
                        "name" to name,
                        "phone" to phone,
                        "role" to "doctor",
                        "gender" to gender,
                        "dateOfBirth" to dateOfBirth,
                        "profilePhotoUri" to profilePhotoUri,
                        "email" to email,
                        "createdAt" to System.currentTimeMillis(),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            if (!authUid.isNullOrBlank()) {
                db.collection(USERS)
                    .document(authUid)
                    .set(
                        mapOf(
                            "uid" to authUid,
                            "name" to name,
                            "email" to email,
                            "phone" to phone,
                            "role" to "doctor",
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }
            upsertAuthLookup(phone, authEmailForProfile(email, phone), "doctor", authUid)
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    suspend fun findVetDoctorByPhone(phone: String): VetDoctor? {
        val db = firestore ?: return null
        return runCatching {
            db.collection(VETERINARY_DOCTORS)
                .document(documentId(phone))
                .get()
                .await()
                .toVetDoctorAccount()
        }.getOrNull()
    }

    suspend fun fetchVetDoctors(): List<VetDoctor> {
        val db = firestore ?: return emptyList()
        return runCatching {
            db.collection(VETERINARY_DOCTORS)
                .get()
                .await()
                .documents
                .mapNotNull { it.toVetDoctorAccount() }
                .filter { it.phone.isNotBlank() }
                .sortedBy { it.name.ifBlank { it.phone } }
        }.getOrDefault(emptyList())
    }

    suspend fun updateVetDoctorProfile(
        name: String,
        phone: String,
        gender: String,
        dateOfBirth: String,
        email: String,
        latitude: Double?,
        longitude: Double?,
        upiId: String? = null,
        consultationFee: Int? = null,
        availabilityStatus: String? = null
    ): FirebaseWriteResult {
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            val data = mutableMapOf<String, Any?>(
                "name" to name,
                "phone" to phone,
                "role" to "doctor",
                "gender" to gender,
                "dateOfBirth" to dateOfBirth,
                "email" to email,
                "upiId" to upiId.orEmpty(),
                "updatedAt" to System.currentTimeMillis()
            )
            if (latitude != null && longitude != null) {
                data["latitude"] = latitude
                data["longitude"] = longitude
            }
            if (consultationFee != null) data["consultationFee"] = consultationFee
            if (!availabilityStatus.isNullOrBlank()) data["availabilityStatus"] = availabilityStatus
            db.collection(VETERINARY_DOCTORS)
                .document(documentId(phone))
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .await()
            val uid = auth?.currentUser?.uid
            if (!uid.isNullOrBlank()) {
                db.collection(USERS)
                    .document(uid)
                    .set(
                        mapOf(
                            "uid" to uid,
                            "name" to name,
                            "email" to email,
                            "phone" to phone,
                            "role" to "doctor",
                            "updatedAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    .await()
            }
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    suspend fun uploadFeedback(
        farmerName: String,
        farmerPhone: String,
        rating: Int,
        message: String
    ): FirebaseWriteResult {
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            db.collection(FEEDBACK)
                .add(
                    mapOf(
                        "farmerName" to farmerName,
                        "farmerPhone" to farmerPhone,
                        "rating" to rating,
                        "message" to message,
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    suspend fun updateAnimalSickStatus(ownerPhone: String, uniqueAnimalId: String, isSick: Boolean) {
        val db = firestore ?: return
        runCatching {
            db.collection(FARMERS)
                .document(documentId(ownerPhone))
                .collection(ANIMALS)
                .document(uniqueAnimalId)
                .update("isSick", isSick)
                .await()
        }
    }

    suspend fun deleteAnimal(ownerPhone: String, uniqueAnimalId: String, localAnimalId: Int) {
        val db = firestore ?: return
        runCatching {
            val farmerDoc = db.collection(FARMERS).document(documentId(ownerPhone))
            farmerDoc.collection(ANIMALS)
                .document(uniqueAnimalId)
                .delete()
                .await()
            farmerDoc.collection(DISEASE_REPORTS)
                .whereEqualTo("animalId", localAnimalId)
                .get()
                .await()
                .documents
                .forEach { it.reference.delete().await() }
            farmerDoc.collection(VACCINES)
                .whereEqualTo("animalId", localAnimalId)
                .get()
                .await()
                .documents
                .forEach { it.reference.delete().await() }
        }
    }

    suspend fun sendPasswordResetForFarmer(farmer: Farmer): FirebaseWriteResult {
        val firebaseAuth = auth ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        val email = authEmailForFarmer(farmer)
        if (!isRealEmail(email)) return FirebaseWriteResult(false, "Add a valid email address before using password reset.")
        return runCatching {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, friendlyAuthError(it)) }
        )
    }

    suspend fun sendPasswordResetForIdentifier(identifier: String): FirebaseWriteResult {
        val firebaseAuth = auth ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        val value = identifier.trim()
        val email = if (android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            value
        } else {
            authEmailForPhone(value)
                ?: return FirebaseWriteResult(false, "No account email found for this phone number.")
        }
        if (!isRealEmail(email)) return FirebaseWriteResult(false, "This account does not have a valid reset email.")
        return runCatching {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, friendlyAuthError(it)) }
        )
    }

    suspend fun changeCurrentUserPassword(phone: String, currentPassword: String, newPassword: String): FirebaseWriteResult {
        val firebaseAuth = auth ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        val user = firebaseAuth.currentUser ?: return FirebaseWriteResult(false, "Please log in again before changing password.")
        val email = user.email ?: authEmailForPhone(phone) ?: return FirebaseWriteResult(false, "No Firebase Auth email found for this account.")
        return runCatching {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
            user.updatePassword(newPassword).await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, friendlyAuthError(it)) }
        )
    }

    suspend fun updateVetReportStatus(reportId: String, status: String): FirebaseWriteResult {
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            db.collection(VET_SICK_REPORTS)
                .document(reportId)
                .update(
                    mapOf(
                        "status" to status,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    suspend fun createPaymentRequest(report: VetSickReport, amount: Int): FirebaseWriteResult {
        val db = firestore ?: return FirebaseWriteResult(false, "Firebase is not configured in this APK.")
        return runCatching {
            db.collection(PAYMENTS)
                .document(report.id)
                .set(
                    mapOf(
                        "farmerId" to report.farmerPhone,
                        "doctorId" to report.assignedDoctorPhone,
                        "reportId" to report.id,
                        "amount" to amount,
                        "paymentStatus" to "requested",
                        "transactionId" to "",
                        "gateway" to "razorpay_server_required",
                        "createdAt" to System.currentTimeMillis()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                .await()
            db.collection(VET_SICK_REPORTS)
                .document(report.id)
                .update(
                    mapOf(
                        "consultationFee" to amount,
                        "paymentStatus" to "Payment requested",
                        "paymentId" to report.id
                    )
                )
                .await()
        }.fold(
            onSuccess = { FirebaseWriteResult(true) },
            onFailure = { FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName) }
        )
    }

    private fun documentId(value: String): String {
        return value.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "unknown" }
    }

    private fun authEmail(phone: String): String {
        val normalizedPhone = documentId(phone).lowercase()
        return "$normalizedPhone@gramavaxi.local"
    }

    private fun authEmailForFarmer(farmer: Farmer): String = authEmailForProfile(farmer.email, farmer.phone)

    private fun authEmailForProfile(email: String, phone: String): String {
        val trimmedEmail = email.trim()
        return if (android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            trimmedEmail
        } else {
            authEmail(phone)
        }
    }

    private suspend fun authEmailForPhone(phone: String): String? {
        val db = firestore ?: return null
        val lookupEmail = runCatching {
            db.collection(AUTH_LOOKUP)
                .document(documentId(phone))
                .get()
                .await()
                .getString("email")
        }.getOrNull()
        if (!lookupEmail.isNullOrBlank()) return lookupEmail

        return findFarmerByPhone(phone)?.let(::authEmailForFarmer)
            ?: findVetDoctorByPhone(phone)?.let { authEmailForProfile(it.email, it.phone) }
    }

    private suspend fun upsertAuthLookup(phone: String, email: String, role: String, authUid: String?) {
        val db = firestore ?: return
        db.collection(AUTH_LOOKUP)
            .document(documentId(phone))
            .set(
                mapOf(
                    "email" to email,
                    "phone" to phone,
                    "role" to role,
                    "authUid" to authUid,
                    "updatedAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    private fun isRealEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
            !email.endsWith("@gramavaxi.local", ignoreCase = true)
    }

    private fun friendlyAuthError(error: Throwable): String {
        return when (error) {
            is FirebaseNetworkException -> "Network error. Check your internet connection and try again."
            is FirebaseAuthInvalidUserException -> "No Firebase account exists for this email."
            is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
            else -> error.message ?: error.javaClass.simpleName
        }
    }

    private fun Farmer.toMap(authUid: String?): Map<String, Any?> = mapOf(
        "id" to id,
        "authUid" to authUid,
        "name" to name,
        "village" to village,
        "phone" to phone,
        "role" to "farmer",
        "gender" to gender,
        "dateOfBirth" to dateOfBirth,
        "profilePhotoUri" to profilePhotoUri,
        "email" to email
    )

    private fun Farmer.toUserMap(authUid: String, role: String): Map<String, Any?> = mapOf(
        "uid" to authUid,
        "name" to name,
        "email" to email,
        "phone" to phone,
        "role" to role,
        "updatedAt" to System.currentTimeMillis()
    )

    private fun Animal.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "uniqueAnimalId" to uniqueAnimalId,
        "farmerId" to farmerId,
        "name" to name,
        "type" to type,
        "breed" to breed,
        "ageMonths" to ageMonths,
        "ownerPhone" to ownerPhone,
        "photoUri" to photoUri,
        "lastVaccinationDate" to lastVaccinationDate,
        "nextShotDate" to nextShotDate,
        "isSick" to isSick
    )

    private fun VaccineRecord.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "animalId" to animalId,
        "vaccineName" to vaccineName,
        "dateGiven" to dateGiven,
        "nextDueDate" to nextDueDate,
        "campLocation" to campLocation
    )

    private fun DiseaseReport.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "animalId" to animalId,
        "symptoms" to symptoms,
        "notes" to notes,
        "farmerName" to farmerName,
        "farmerPhone" to farmerPhone,
        "animalName" to animalName,
        "latitude" to latitude,
        "longitude" to longitude,
        "paymentMethod" to paymentMethod,
        "paymentStatus" to paymentStatus,
        "consultationFee" to consultationFee,
        "assignedDoctorId" to assignedDoctorId,
        "assignedDoctorName" to assignedDoctorName,
        "assignedDoctorPhone" to assignedDoctorPhone,
        "status" to "pending",
        "reportedAt" to reportedAt
    )

    suspend fun findNearestDoctor(latitude: Double?, longitude: Double?): VetDoctor? {
        val db = firestore ?: return null
        if (latitude == null || longitude == null) return null
        return runCatching {
            db.collection(VETERINARY_DOCTORS)
                .get()
                .await()
                .documents
                .mapNotNull { it.toVetDoctor() }
                .minByOrNull { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
        }.getOrNull()
    }

    private fun distanceMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(fromLat, fromLon, toLat, toLon, results)
        return results.first().toDouble()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toFarmer(): Farmer? {
        val name = getString("name") ?: return null
        val village = getString("village") ?: return null
        val phone = getString("phone") ?: return null
        val password = getString("password").orEmpty()
        return Farmer(
            id = getLong("id")?.toInt() ?: 0,
            name = name,
            village = village,
            phone = phone,
            password = password,
            gender = getString("gender").orEmpty(),
            dateOfBirth = getString("dateOfBirth").orEmpty(),
            profilePhotoUri = getString("profilePhotoUri").orEmpty(),
            email = getString("email").orEmpty()
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toAnimal(): Animal? {
        val uniqueAnimalId = getString("uniqueAnimalId") ?: return null
        val name = getString("name") ?: return null
        val type = getString("type") ?: return null
        val breed = getString("breed") ?: return null
        val ownerPhone = getString("ownerPhone") ?: return null
        return Animal(
            id = getLong("id")?.toInt() ?: 0,
            uniqueAnimalId = uniqueAnimalId,
            farmerId = getLong("farmerId")?.toInt() ?: 0,
            name = name,
            type = type,
            breed = breed,
            ageMonths = getLong("ageMonths")?.toInt()
                ?: getLong("ageYears")?.toInt()?.times(12)
                ?: 0,
            ownerPhone = ownerPhone,
            photoUri = getString("photoUri"),
            lastVaccinationDate = getLong("lastVaccinationDate") ?: 0L,
            nextShotDate = getLong("nextShotDate") ?: 0L,
            isSick = getBoolean("isSick") ?: false
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toVetSickReport(): VetSickReport? {
        return VetSickReport(
            id = id,
            farmerName = getString("farmerName").orEmpty(),
            farmerPhone = getString("farmerPhone") ?: getString("ownerPhone").orEmpty(),
            animalName = getString("animalName").orEmpty(),
            symptoms = getString("symptoms").orEmpty(),
            notes = getString("notes").orEmpty(),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            paymentMethod = getString("paymentMethod").orEmpty(),
            paymentStatus = getString("paymentStatus").orEmpty(),
            status = getString("status").orEmpty().ifBlank { "pending" },
            consultationFee = getLong("consultationFee")?.toInt() ?: 0,
            assignedDoctorId = getString("assignedDoctorId").orEmpty(),
            assignedDoctorName = getString("assignedDoctorName").orEmpty(),
            assignedDoctorPhone = getString("assignedDoctorPhone").orEmpty(),
            reportedAt = getLong("reportedAt") ?: 0L
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toVetDoctor(): VetDoctor? {
        val latitude = getDouble("latitude") ?: return null
        val longitude = getDouble("longitude") ?: return null
        return VetDoctor(
            id = id,
            name = getString("name").orEmpty(),
            phone = getString("phone").orEmpty(),
            password = getString("password").orEmpty(),
            gender = getString("gender").orEmpty(),
            dateOfBirth = getString("dateOfBirth").orEmpty(),
            profilePhotoUri = getString("profilePhotoUri").orEmpty(),
            email = getString("email").orEmpty(),
            upiId = getString("upiId").orEmpty(),
            consultationFee = getLong("consultationFee")?.toInt() ?: 99,
            availabilityStatus = getString("availabilityStatus").orEmpty().ifBlank { "Available" },
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toVetDoctorAccount(): VetDoctor? {
        if (!exists()) return null
        return VetDoctor(
            id = id,
            name = getString("name").orEmpty(),
            phone = getString("phone").orEmpty(),
            password = getString("password").orEmpty(),
            gender = getString("gender").orEmpty(),
            dateOfBirth = getString("dateOfBirth").orEmpty(),
            profilePhotoUri = getString("profilePhotoUri").orEmpty(),
            email = getString("email").orEmpty(),
            upiId = getString("upiId").orEmpty(),
            consultationFee = getLong("consultationFee")?.toInt() ?: 99,
            availabilityStatus = getString("availabilityStatus").orEmpty().ifBlank { "Available" },
            latitude = getDouble("latitude") ?: 0.0,
            longitude = getDouble("longitude") ?: 0.0
        )
    }

    companion object {
        private const val FARMERS = "farmers"
        private const val USERS = "users"
        private const val AUTH_LOOKUP = "auth_lookup"
        private const val ANIMALS = "animals"
        private const val VACCINES = "vaccine_records"
        private const val DISEASE_REPORTS = "disease_reports"
        private const val VET_SICK_REPORTS = "vet_sick_reports"
        private const val VETERINARY_DOCTORS = "veterinary_doctors"
        private const val FEEDBACK = "customer_feedback"
        private const val PAYMENTS = "payments"
    }
}

data class FirebaseWriteResult(
    val success: Boolean,
    val errorMessage: String? = null
)

data class VetSickReport(
    val id: String,
    val farmerName: String,
    val farmerPhone: String,
    val animalName: String,
    val symptoms: String,
    val notes: String,
    val latitude: Double?,
    val longitude: Double?,
    val paymentMethod: String,
    val paymentStatus: String,
    val status: String,
    val consultationFee: Int,
    val assignedDoctorId: String,
    val assignedDoctorName: String,
    val assignedDoctorPhone: String,
    val reportedAt: Long
)

data class VetDoctor(
    val id: String,
    val name: String,
    val phone: String,
    val password: String = "",
    val gender: String = "",
    val dateOfBirth: String = "",
    val profilePhotoUri: String = "",
    val email: String = "",
    val upiId: String = "",
    val consultationFee: Int = 99,
    val availabilityStatus: String = "Available",
    val latitude: Double,
    val longitude: Double
)
