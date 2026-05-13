# 🐐 Grama-Vaxi
### Android App Development using GenAI — MindMatrix VTU Internship Program (Project #23)

> A **Digital Health Card** for livestock in rural villages — tracking vaccinations, sending camp alerts, and preventing animal loss for farmers.

---

## 📋 Table of Contents

- [Problem Statement](#problem-statement)
- [App Overview](#app-overview)
- [Features](#features)
- [Screens & User Flow](#screens--user-flow)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Setup & Installation](#setup--installation)
- [Room DB Schema](#room-db-schema)
- [WorkManager — Notification Scheduling](#workmanager--notification-scheduling)
- [Kannada Language Support](#kannada-language-support)
- [Success Criteria](#success-criteria)
- [Impact Goals](#impact-goals)
- [Team](#team)

---

## 🔴 Problem Statement

Livestock in villages often die from disease outbreaks because farmers **miss "Vaccination Camp" dates** announced only via local loudspeakers. This app replaces that unreliable system with automated, structured digital reminders.

---

## 📱 App Overview

**Grama-Vaxi** is a Livestock Health Alert Android application that:

- Acts as a **Digital Health Card** for every sheep/goat in the village
- Tracks the full **vaccination cycle** of each animal
- Sends **loud push notifications 3 days before** a government camp arrives
- Works **fully offline** using local Room DB storage
- Supports **Kannada and English** UI

---

## ✨ Features

| Feature | Description |
|---|---|
| 🐐 Animal Ledger | Register animals with photo, breed, and age |
| 💉 Vaccine Calendar | Auto-generates "Next Shot" dates per animal |
| 🔔 Camp Alert | Notifies: "Doctor arriving at Temple Square tomorrow" |
| 🏥 Disease Report | Report a sick animal to the local Vet (Simulated) |
| 📴 Offline First | Full history available without internet (Room DB) |
| 🌐 Bilingual UI | Complete Kannada + English support |

---

## 🗺️ Screens & User Flow

```
Dashboard (Hub)
│
├── Animal Ledger (Register/View Animals)
│       └── Photo + Breed + Age + Last Vaccination Date
│
├── Vaccine Calendar
│       └── Auto-calculated "Next Shot" per animal
│
├── Disease Report
│       └── Symptom selector → Simulated Vet alert
│
└── Notification Center
        └── Camp alerts + vaccination reminders
```

### Dashboard
- Summary tiles: Total Animals / Due Soon / Sick count
- Upcoming camp alert banner
- Quick action buttons: **Add Animal** and **Report Sick**
- Scrollable recent animal list with status badges (OK / Due / Sick)

### Animal Ledger
- Photo upload (camera or gallery)
- Fields: Name, Type (Goat/Sheep/Cow), Breed, Age, Last Vaccination Date
- Next shot date is **auto-calculated** on save
- Bilingual type labels: `Goat / ಮೇಕೆ`

### Vaccine Calendar
- Sorted list of upcoming vaccinations per animal
- Government camp banner at top
- Color-coded urgency: Red (overdue) / Amber (due soon) / Green (OK)

### Disease Report
- Animal selector dropdown
- Visual symptom grid with emoji icons (supports low-literacy users)
- Notes field
- Simulated vet report submission

---

## 🛠️ Tech Stack

```
Language        : Kotlin
Min SDK         : 21 (Android 5.0)
Target SDK      : 34
Architecture    : MVVM (Model-View-ViewModel)
```

| Component | Library |
|---|---|
| Local Database | Room DB (androidx.room) |
| Background Tasks | WorkManager (androidx.work) |
| UI Navigation | Navigation Component |
| Image Handling | Glide |
| Dependency Injection | Hilt (optional) |
| Localization | strings.xml (en / kn) |
| Notifications | NotificationManager + NotificationChannel |

---

## 📁 Project Structure

```
app/
├── src/main/
│   ├── java/com/gramavaxi/
│   │   ├── data/
│   │   │   ├── db/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── AnimalDao.kt
│   │   │   │   └── VaccineDao.kt
│   │   │   ├── model/
│   │   │   │   ├── Animal.kt
│   │   │   │   └── VaccineRecord.kt
│   │   │   └── repository/
│   │   │       └── AnimalRepository.kt
│   │   ├── ui/
│   │   │   ├── dashboard/
│   │   │   │   ├── DashboardFragment.kt
│   │   │   │   └── DashboardViewModel.kt
│   │   │   ├── ledger/
│   │   │   │   ├── AnimalLedgerFragment.kt
│   │   │   │   └── RegisterAnimalFragment.kt
│   │   │   ├── calendar/
│   │   │   │   └── VaccineCalendarFragment.kt
│   │   │   └── disease/
│   │   │       └── DiseaseReportFragment.kt
│   │   ├── worker/
│   │   │   └── VaccineReminderWorker.kt
│   │   └── MainActivity.kt
│   ├── res/
│   │   ├── values/strings.xml          ← English strings
│   │   ├── values-kn/strings.xml       ← Kannada strings
│   │   ├── layout/
│   │   └── drawable/
│   └── AndroidManifest.xml
```

---

## ⚙️ Setup & Installation

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android device or emulator (API 21+)

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/your-username/grama-vaxi.git

# 2. Open in Android Studio
File → Open → select the cloned folder

# 3. Sync Gradle
Build → Sync Project with Gradle Files

# 4. Run on device or emulator
Run → Run 'app'
```

### Gradle Dependencies

Add to `app/build.gradle`:

```groovy
dependencies {
    // Room DB
    implementation "androidx.room:room-runtime:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"

    // WorkManager
    implementation "androidx.work:work-runtime-ktx:2.9.0"

    // Glide (image loading)
    implementation "com.github.bumptech.glide:glide:4.16.0"

    // Navigation
    implementation "androidx.navigation:navigation-fragment-ktx:2.7.6"
    implementation "androidx.navigation:navigation-ui-ktx:2.7.6"
}
```

---

## 🗃️ Room DB Schema

### Animal Entity

```kotlin
@Entity(tableName = "animals")
data class Animal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,           // "Goat", "Sheep", "Cow"
    val breed: String,
    val ageYears: Int,
    val photoPath: String?,
    val lastVaccinationDate: Long,   // epoch ms
    val nextShotDate: Long,          // auto-calculated
    val isSick: Boolean = false
)
```

### VaccineRecord Entity

```kotlin
@Entity(tableName = "vaccine_records")
data class VaccineRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val animalId: Int,
    val vaccineName: String,
    val dateGiven: Long,
    val nextDueDate: Long,
    val campLocation: String?
)
```

---

## ⏰ WorkManager — Notification Scheduling

When an animal is registered, schedule a reminder 3 days before the next shot:

```kotlin
class VaccineReminderWorker(ctx: Context, params: WorkerParameters)
    : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val animalName = inputData.getString("animal_name") ?: return Result.failure()
        val vaccineDate = inputData.getString("vaccine_date") ?: return Result.failure()

        showNotification(
            title = "💉 Vaccination Due in 3 Days",
            message = "$animalName needs a shot on $vaccineDate. Camp at Temple Square."
        )
        return Result.success()
    }
}

// Schedule it on animal save:
fun scheduleVaccineReminder(animal: Animal) {
    val delay = animal.nextShotDate - System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
    if (delay <= 0) return

    val data = workDataOf(
        "animal_name" to animal.name,
        "vaccine_date" to formatDate(animal.nextShotDate)
    )

    val request = OneTimeWorkRequestBuilder<VaccineReminderWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .build()

    WorkManager.getInstance(context).enqueue(request)
}
```

---

## 🌐 Kannada Language Support

Create `res/values-kn/strings.xml`:

```xml
<resources>
    <string name="app_name">ಗ್ರಾಮ-ವ್ಯಾಕ್ಸಿ</string>
    <string name="dashboard_title">ಡ್ಯಾಶ್‌ಬೋರ್ಡ್</string>
    <string name="add_animal">ಪ್ರಾಣಿ ಸೇರಿಸಿ</string>
    <string name="animal_name">ಪ್ರಾಣಿಯ ಹೆಸರು</string>
    <string name="breed">ತಳಿ</string>
    <string name="age">ವಯಸ್ಸು</string>
    <string name="last_vaccination">ಕೊನೆಯ ಲಸಿಕೆ ದಿನಾಂಕ</string>
    <string name="next_shot">ಮುಂದಿನ ಡೋಸ್</string>
    <string name="report_sick">ಅನಾರೋಗ್ಯ ವರದಿ</string>
    <string name="camp_alert">ಶಿಬಿರ ಎಚ್ಚರಿಕೆ</string>
    <string name="save_animal">ಪ್ರಾಣಿ ಉಳಿಸಿ</string>
    <string name="fever">ಜ್ವರ</string>
    <string name="not_eating">ತಿನ್ನದಿರುವ</string>
    <string name="limping">ಕುಂಟುವ</string>
    <string name="diarrhea">ಭೇದಿ</string>
    <string name="wound">ಗಾಯ</string>
    <string name="swelling">ಊತ</string>
</resources>
```

Android automatically loads Kannada strings when the device language is set to Kannada.

---

## ✅ Success Criteria

- [ ] Reminders trigger even if the app hasn't been opened for days (WorkManager)
- [ ] "Register Animal" form is simple and visual (photo + icons + dropdowns)
- [ ] Full UI support for Kannada (`values-kn/strings.xml`)
- [ ] Offline history works without internet (Room DB)
- [ ] Notifications sent 3 days before government camp arrival
- [ ] Disease report screen functional (simulated vet contact)

---

## 🌍 Impact Goals

| Goal | Description |
|---|---|
| 💰 Livestock Wealth | Prevent animal loss — livestock is the farmer's "savings account" |
| 🐄 Animal Welfare | Ensure timely medical care for rural cattle population |
| 🗄️ Health Digitization | Build a database of village animal health over time |

---

## 👥 Team

| Role | Name |
|---|---|
| Intern(s) | _(Your Name)_ |
| Mentor | MindMatrix VTU Program |
| Institution | VTU (Visvesvaraya Technological University) |

---

## 📄 License

This project is developed as part of the **MindMatrix VTU Internship Program**.  
For academic and educational use only.

---

> *"A vaccinated animal is a saved investment."* — Grama-Vaxi Mission
