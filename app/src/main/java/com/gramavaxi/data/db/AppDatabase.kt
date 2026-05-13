package com.gramavaxi.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gramavaxi.data.model.Animal
import com.gramavaxi.data.model.DiseaseReport
import com.gramavaxi.data.model.Farmer
import com.gramavaxi.data.model.VaccineRecord

@Database(
    entities = [Animal::class, VaccineRecord::class, DiseaseReport::class, Farmer::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animalDao(): AnimalDao
    abstract fun vaccineDao(): VaccineDao
    abstract fun diseaseReportDao(): DiseaseReportDao
    abstract fun farmerDao(): FarmerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "grama_vaxi.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE animals ADD COLUMN ownerPhone TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS farmers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, village TEXT NOT NULL, phone TEXT NOT NULL, password TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_farmers_phone ON farmers (phone)")
                db.execSQL("ALTER TABLE animals ADD COLUMN uniqueAnimalId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE animals ADD COLUMN farmerId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE animals SET uniqueAnimalId = 'GVX-' || id WHERE uniqueAnimalId = ''")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE animals_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uniqueAnimalId TEXT NOT NULL,
                        farmerId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        breed TEXT NOT NULL,
                        ageMonths INTEGER NOT NULL,
                        ownerPhone TEXT NOT NULL,
                        photoUri TEXT,
                        lastVaccinationDate INTEGER NOT NULL,
                        nextShotDate INTEGER NOT NULL,
                        isSick INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO animals_new (
                        id,
                        uniqueAnimalId,
                        farmerId,
                        name,
                        type,
                        breed,
                        ageMonths,
                        ownerPhone,
                        photoUri,
                        lastVaccinationDate,
                        nextShotDate,
                        isSick
                    )
                    SELECT
                        id,
                        uniqueAnimalId,
                        farmerId,
                        name,
                        type,
                        breed,
                        ageYears * 12,
                        ownerPhone,
                        photoUri,
                        lastVaccinationDate,
                        nextShotDate,
                        isSick
                    FROM animals
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE animals")
                db.execSQL("ALTER TABLE animals_new RENAME TO animals")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN farmerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN farmerPhone TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN animalName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN paymentStatus TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN consultationFee INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN assignedDoctorId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN assignedDoctorName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE disease_reports ADD COLUMN assignedDoctorPhone TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farmers ADD COLUMN gender TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farmers ADD COLUMN basicDetails TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE farmers ADD COLUMN dateOfBirth TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farmers ADD COLUMN profilePhotoUri TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE farmers ADD COLUMN email TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                val cursor = db.query("PRAGMA table_info(farmers)")
                val columns = mutableSetOf<String>()
                cursor.use {
                    val nameIndex = it.getColumnIndex("name")
                    while (it.moveToNext()) {
                        columns.add(it.getString(nameIndex))
                    }
                }
                if ("basicDetails" !in columns) {
                    db.execSQL("ALTER TABLE farmers ADD COLUMN basicDetails TEXT NOT NULL DEFAULT ''")
                }
            }
        }
    }
}
