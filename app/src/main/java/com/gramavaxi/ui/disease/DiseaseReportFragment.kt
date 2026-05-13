package com.gramavaxi.ui.disease

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.firebase.VetDoctor
import com.gramavaxi.data.model.Animal
import com.gramavaxi.databinding.FragmentDiseaseReportBinding
import com.gramavaxi.ui.AppViewModelFactory
import com.gramavaxi.util.LiveLocationClient
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class DiseaseReportFragment : Fragment() {
    private var _binding: FragmentDiseaseReportBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DiseaseReportViewModel by viewModels {
        AppViewModelFactory.from(requireContext())
    }
    private var animals: List<Animal> = emptyList()
    private var doctors: List<VetDoctor> = emptyList()
    private var lastLocation: Location? = null
    private var liveLocationClient: LiveLocationClient? = null
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            startLiveLocation()
        } else {
            binding.locationPreview.text = getString(R.string.location_unavailable)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDiseaseReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.animals.observe(viewLifecycleOwner) { list ->
            animals = list
            binding.animalSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                list.map { it.name }
            )
        }
        binding.submitReportButton.setOnClickListener { submitReport() }
        binding.doctorSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.loading_doctors))
        )
        binding.doctorSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateConsultationFee()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        loadDoctors()
        startLiveLocation()
        viewModel.reportSubmitted.observe(viewLifecycleOwner) { submitted ->
            if (!submitted) return@observe
            Toast.makeText(requireContext(), R.string.report_sent, Toast.LENGTH_LONG).show()
            clearForm()
            viewModel.clearSubmitted()
        }
    }

    private fun submitReport() {
        if (animals.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_animals_for_report, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedDoctor = selectedDoctor()
        if (selectedDoctor == null) {
            Toast.makeText(requireContext(), R.string.no_doctors_available, Toast.LENGTH_LONG).show()
            return
        }
        val symptoms = symptomBoxes().filter { it.isChecked }.map { it.text.toString() }
        if (symptoms.isEmpty()) {
            Toast.makeText(requireContext(), R.string.choose_symptom, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedAnimal = animals[binding.animalSpinner.selectedItemPosition]
        val report = PendingDiseaseReport(
            animalId = selectedAnimal.id,
            symptoms = symptoms,
            notes = binding.notesInput.text.toString().trim(),
            farmerName = SessionManager.currentFarmerName(requireContext()),
            latitude = lastLocation?.latitude,
            longitude = lastLocation?.longitude,
            assignedDoctorId = selectedDoctor.id,
            assignedDoctorName = selectedDoctor.name,
            assignedDoctorPhone = selectedDoctor.phone,
            paymentMethod = getString(R.string.pay_at_visit),
            consultationFee = selectedDoctor.consultationFee
        )

        submitDiseaseReport(report, getString(R.string.payment_pending_visit))
    }

    private fun submitDiseaseReport(report: PendingDiseaseReport, paymentStatus: String) {
        viewModel.submitReport(
            report.animalId,
            report.symptoms,
            report.notes,
            report.farmerName,
            report.latitude,
            report.longitude,
            report.paymentMethod,
            paymentStatus,
            report.consultationFee,
            report.assignedDoctorId,
            report.assignedDoctorName,
            report.assignedDoctorPhone
        )
    }

    private fun loadDoctors() {
        lifecycleScope.launch {
            doctors = FirebaseSyncService(requireContext()).fetchVetDoctors()
            if (_binding == null) return@launch
            binding.doctorSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                if (doctors.isEmpty()) {
                    listOf(getString(R.string.no_doctors_available))
                } else {
                    doctors.map { doctor ->
                        buildString {
                            append(doctor.name.ifBlank { doctor.phone })
                            if (doctor.consultationFee > 0) append(" - INR ${doctor.consultationFee}")
                        }
                    }
                }
            )
            updateConsultationFee()
        }
    }

    private fun selectedDoctor(): VetDoctor? {
        val position = binding.doctorSpinner.selectedItemPosition
        return doctors.getOrNull(position)
    }

    private fun updateConsultationFee() {
        val fee = selectedDoctor()?.consultationFee ?: CONSULTATION_FEE
        binding.consultationFeeText.text = getString(R.string.consultation_fee_dynamic, fee)
    }

    private fun startLiveLocation() {
        if (!hasLocationPermission()) {
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        liveLocationClient?.stop()
        liveLocationClient = LiveLocationClient(
            requireContext(),
            onLocation = { location ->
                lastLocation = location
                if (_binding != null) {
                    binding.locationPreview.text = getString(R.string.location_ready, location.latitude, location.longitude)
                }
            },
            onUnavailable = { issue ->
                if (_binding != null) {
                    binding.locationPreview.text = when (issue) {
                        LiveLocationClient.LocationIssue.LocationDisabled -> getString(R.string.location_services_disabled)
                        else -> getString(R.string.location_unavailable)
                    }
                }
            }
        )
        liveLocationClient?.start()
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun clearForm() {
        symptomBoxes().forEach { it.isChecked = false }
        binding.notesInput.text?.clear()
    }

    private fun symptomBoxes(): List<CheckBox> = listOf(
        binding.feverCheck,
        binding.notEatingCheck,
        binding.limpingCheck,
        binding.diarrheaCheck,
        binding.woundCheck,
        binding.swellingCheck
    )

    override fun onDestroyView() {
        super.onDestroyView()
        liveLocationClient?.stop()
        liveLocationClient = null
        _binding = null
    }

    companion object {
        private const val CONSULTATION_FEE = 99
    }
}

private data class PendingDiseaseReport(
    val animalId: Int,
    val symptoms: List<String>,
    val notes: String,
    val farmerName: String,
    val latitude: Double?,
    val longitude: Double?,
    val assignedDoctorId: String,
    val assignedDoctorName: String,
    val assignedDoctorPhone: String,
    val paymentMethod: String,
    val consultationFee: Int = 99
)
