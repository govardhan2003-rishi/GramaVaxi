package com.gramavaxi.ui.ledger

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.gramavaxi.R
import com.gramavaxi.data.model.Animal
import com.gramavaxi.databinding.FragmentRegisterAnimalBinding
import com.gramavaxi.ui.AppViewModelFactory
import com.gramavaxi.util.DateUtils
import com.gramavaxi.util.SessionManager
import com.gramavaxi.worker.ReminderScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class RegisterAnimalFragment : Fragment() {
    private var _binding: FragmentRegisterAnimalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnimalLedgerViewModel by viewModels {
        AppViewModelFactory.from(requireContext())
    }
    private var selectedPhotoUri: Uri? = null
    private var pendingCameraUri: Uri? = null
    private var selectedLastVaccinationDate: Long = System.currentTimeMillis()
    private val generatedAnimalId: String by lazy { generateAnimalId() }

    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult
        selectedPhotoUri = uri
        Glide.with(this).load(uri).centerCrop().into(binding.animalPhoto)
    }

    private val cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            selectedPhotoUri = uri
            Glide.with(this).load(uri).centerCrop().into(binding.animalPhoto)
        } else {
            Toast.makeText(requireContext(), R.string.camera_capture_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            Toast.makeText(requireContext(), R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterAnimalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.animal_types)
        )
        binding.photoButton.setOnClickListener { photoPicker.launch("image/*") }
        binding.cameraButton.setOnClickListener { takePhoto() }
        binding.lastVaccinationButton.setOnClickListener { showDatePicker() }
        binding.lastVaccinationButton.text = DateUtils.formatDate(selectedLastVaccinationDate)
        binding.uniqueAnimalIdText.text = getString(R.string.unique_animal_id_value, generatedAnimalId)

        binding.saveAnimalButton.setOnClickListener { saveAnimal() }
        viewModel.savedAnimal.observe(viewLifecycleOwner) { animal ->
            animal ?: return@observe
            ReminderScheduler.scheduleVaccineReminder(requireContext(), animal)
            Toast.makeText(requireContext(), R.string.animal_saved, Toast.LENGTH_SHORT).show()
            viewModel.clearSavedAnimal()
            findNavController().popBackStack()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { timeInMillis = selectedLastVaccinationDate }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                selectedLastVaccinationDate = Calendar.getInstance().apply {
                    set(year, month, day, 8, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                binding.lastVaccinationButton.text = DateUtils.formatDate(selectedLastVaccinationDate)
                binding.nextShotPreview.text = getString(
                    R.string.next_shot_preview,
                    DateUtils.formatDate(DateUtils.nextShotFrom(selectedLastVaccinationDate))
                )
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveAnimal() {
        val name = binding.nameInput.text.toString().trim()
        val breed = binding.breedInput.text.toString().trim()
        val ownerPhone = binding.ownerPhoneInput.text.toString().trim()
        val age = binding.ageInput.text.toString().toIntOrNull()
        if (name.isBlank() || breed.isBlank() || age == null || ownerPhone.isBlank()) {
            Toast.makeText(requireContext(), R.string.fill_required_fields, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addAnimal(
            Animal(
                uniqueAnimalId = generatedAnimalId,
                farmerId = SessionManager.currentFarmerId(requireContext()),
                name = name,
                type = binding.typeSpinner.selectedItem.toString(),
                breed = breed,
                ageMonths = age,
                ownerPhone = ownerPhone,
                photoUri = selectedPhotoUri?.toString(),
                lastVaccinationDate = selectedLastVaccinationDate,
                nextShotDate = DateUtils.nextShotFrom(selectedLastVaccinationDate)
            )
        )
    }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraCapture()
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        pendingCameraUri = createCameraPhotoUri()
        cameraCapture.launch(pendingCameraUri)
    }

    private fun createCameraPhotoUri(): Uri {
        val photoDirectory = File(requireContext().cacheDir, "animal_photos").apply { mkdirs() }
        val photoFile = File(photoDirectory, "${generatedAnimalId}-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )
    }

    private fun generateAnimalId(): String {
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(System.currentTimeMillis())
        val suffix = UUID.randomUUID().toString().take(6).uppercase(Locale.US)
        return "GVX-$date-$suffix"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
