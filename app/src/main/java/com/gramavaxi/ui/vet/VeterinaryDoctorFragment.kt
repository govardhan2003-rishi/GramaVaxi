package com.gramavaxi.ui.vet

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.firebase.VetSickReport
import com.gramavaxi.databinding.FragmentVeterinaryDoctorBinding
import com.gramavaxi.util.DateUtils
import com.gramavaxi.util.LiveLocationClient
import com.gramavaxi.util.SessionManager
import com.gramavaxi.worker.NotificationHelper
import kotlinx.coroutines.launch

class VeterinaryDoctorFragment : Fragment() {
    private var _binding: FragmentVeterinaryDoctorBinding? = null
    private val binding get() = _binding!!
    private var lastLocation: Location? = null
    private var liveLocationClient: LiveLocationClient? = null
    private var listenerRegistration: ListenerRegistration? = null
    private val seenReportIds = mutableSetOf<String>()
    private var firstSnapshot = true
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            startLiveLocation()
        } else {
            binding.doctorLocationPreview.text = getString(R.string.location_unavailable)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVeterinaryDoctorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.doctorNameInput.setText(SessionManager.currentFarmerName(requireContext()))
        binding.doctorPhoneInput.setText(SessionManager.currentFarmerPhone(requireContext()))
        binding.registerDoctorButton.setOnClickListener { registerDoctor() }
        binding.loadDoctorProfileButton.setOnClickListener { loadDoctorProfile() }
        binding.refreshReportsButton.setOnClickListener { startReportListener() }
        startLiveLocation()
        loadDoctorProfile()
        startReportListener()
    }

    private fun startReportListener() {
        listenerRegistration?.remove()
        firstSnapshot = true
        binding.reportList.removeAllViews()
        binding.reportList.addView(TextView(requireContext()).apply {
            text = getString(R.string.loading_reports)
            textSize = 15f
        })
        val doctorPhone = binding.doctorPhoneInput.text.toString().trim()
        listenerRegistration = FirebaseSyncService(requireContext()).listenVetSickReports(doctorPhone) { reports ->
            if (_binding == null) return@listenVetSickReports
            renderReports(reports)
            notifyNewReports(reports)
        }
    }

    private fun registerDoctor() {
        val name = binding.doctorNameInput.text.toString().trim()
        val phone = binding.doctorPhoneInput.text.toString().trim()
        val location = lastLocation
        if (name.isBlank() || phone.isBlank()) {
            Toast.makeText(requireContext(), R.string.doctor_registration_required, Toast.LENGTH_SHORT).show()
            startLiveLocation()
            return
        }
        lifecycleScope.launch {
            val result = FirebaseSyncService(requireContext()).updateVetDoctorProfile(
                name = name,
                phone = phone,
                gender = binding.doctorGenderInput.text.toString().trim(),
                dateOfBirth = binding.doctorDobInput.text.toString().trim(),
                email = binding.doctorEmailInput.text.toString().trim(),
                latitude = location?.latitude,
                longitude = location?.longitude,
                upiId = binding.doctorUpiInput.text.toString().trim()
            )
            if (result.success) {
                binding.doctorProfileStatusText.text = getString(R.string.doctor_profile_saved)
                Toast.makeText(requireContext(), R.string.doctor_profile_saved, Toast.LENGTH_LONG).show()
                startReportListener()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun loadDoctorProfile() {
        val phone = binding.doctorPhoneInput.text.toString().trim()
        if (phone.isBlank()) return
        lifecycleScope.launch {
            val doctor = FirebaseSyncService(requireContext()).findVetDoctorByPhone(phone)
            if (doctor == null) return@launch
            binding.doctorNameInput.setText(doctor.name)
            binding.doctorPhoneInput.setText(doctor.phone)
            binding.doctorGenderInput.setText(doctor.gender)
            binding.doctorDobInput.setText(doctor.dateOfBirth)
            binding.doctorEmailInput.setText(doctor.email)
            binding.doctorUpiInput.setText(doctor.upiId)
            if (doctor.latitude != 0.0 || doctor.longitude != 0.0) {
                lastLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
                    latitude = doctor.latitude
                    longitude = doctor.longitude
                    time = System.currentTimeMillis()
                }
                binding.doctorLocationPreview.text = getString(R.string.location_ready, doctor.latitude, doctor.longitude)
            }
            binding.doctorProfileStatusText.text = getString(R.string.doctor_profile_loaded)
        }
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
                    binding.doctorLocationPreview.text = getString(R.string.location_ready, location.latitude, location.longitude)
                }
            },
            onUnavailable = { issue ->
                if (_binding != null) {
                    binding.doctorLocationPreview.text = when (issue) {
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

    private fun renderReports(reports: List<VetSickReport>) {
        binding.reportList.removeAllViews()
        if (reports.isEmpty()) {
            binding.reportList.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_vet_reports)
                textSize = 15f
            })
        } else {
            reports.forEach { report ->
                binding.reportList.addView(reportView(report).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, 0, 12) }
                })
            }
        }
    }

    private fun notifyNewReports(reports: List<VetSickReport>) {
        val newReports = reports.filter { it.id !in seenReportIds }
        reports.forEach { seenReportIds.add(it.id) }
        if (firstSnapshot) {
            firstSnapshot = false
            return
        }
        newReports.forEach { report ->
            NotificationHelper.show(
                requireContext(),
                getString(R.string.new_sick_report_title),
                getString(R.string.new_sick_report_body, report.animalName, report.farmerName),
                report.id.hashCode()
            )
        }
    }

    private fun reportView(report: VetSickReport): View {
        val context = requireContext()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            background = ContextCompat.getDrawable(context, R.drawable.bg_card)

            addView(TextView(context).apply {
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 15f
                text = buildString {
                appendLine(getString(R.string.vet_report_farmer, report.farmerName, report.farmerPhone))
                appendLine(getString(R.string.vet_report_animal, report.animalName))
                appendLine(getString(R.string.vet_report_symptoms, report.symptoms))
                if (report.notes.isNotBlank()) appendLine(getString(R.string.vet_report_notes, report.notes))
                appendLine(getString(R.string.vet_report_status, report.status))
                appendLine(getString(R.string.vet_report_payment, report.paymentStatus, report.consultationFee))
                appendLine(getString(R.string.vet_report_assigned, report.assignedDoctorName.ifBlank { getString(R.string.nearest_doctor_pending) }))
                appendLine(getString(R.string.vet_report_time, DateUtils.formatDate(report.reportedAt)))
                append(locationText(report))
                }
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).apply {
                    text = getString(R.string.accept_case)
                    setOnClickListener { updateStatus(report, "accepted") }
                })
                addView(Button(context).apply {
                    text = getString(R.string.complete_case)
                    setOnClickListener { updateStatus(report, "completed") }
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(Button(context).apply {
                    text = getString(R.string.request_payment)
                    setOnClickListener { requestPayment(report) }
                })
                addView(Button(context).apply {
                    text = getString(R.string.contact_farmer)
                    setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${report.farmerPhone}"))) }
                })
                addView(Button(context).apply {
                    text = getString(R.string.navigate)
                    setOnClickListener { openNavigation(report) }
                })
            })
        }
    }

    private fun updateStatus(report: VetSickReport, status: String) {
        lifecycleScope.launch {
            val result = FirebaseSyncService(requireContext()).updateVetReportStatus(report.id, status)
            Toast.makeText(
                requireContext(),
                if (result.success) getString(R.string.case_status_updated) else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestPayment(report: VetSickReport) {
        val amountInput = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(if (report.consultationFee > 0) report.consultationFee.toString() else "99")
            hint = getString(R.string.consultation_amount)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.request_payment)
            .setView(amountInput)
            .setPositiveButton(R.string.request_payment) { _, _ ->
                val amount = amountInput.text.toString().toIntOrNull() ?: 0
                if (amount <= 0) {
                    Toast.makeText(requireContext(), R.string.invalid_amount, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val result = FirebaseSyncService(requireContext()).createPaymentRequest(report, amount)
                    Toast.makeText(
                        requireContext(),
                        if (result.success) getString(R.string.payment_request_created) else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openNavigation(report: VetSickReport) {
        val latitude = report.latitude ?: return
        val longitude = report.longitude ?: return
        if (!isValidCoordinate(latitude, longitude)) {
            Toast.makeText(requireContext(), R.string.invalid_report_location, Toast.LENGTH_SHORT).show()
            return
        }
        val geoUri = Uri.parse("geo:0,0?q=$latitude,$longitude")
        val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).setPackage("com.google.android.apps.maps")
        val fallbackIntent = Intent(Intent.ACTION_VIEW, webUri)
        try {
            startActivity(mapsIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(fallbackIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.map_app_missing, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean {
        return latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
    }

    private fun locationText(report: VetSickReport): String {
        val latitude = report.latitude
        val longitude = report.longitude
        return if (latitude == null || longitude == null) {
            getString(R.string.vet_report_no_location)
        } else {
            getString(R.string.vet_report_location, latitude, longitude)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        liveLocationClient?.stop()
        liveLocationClient = null
        listenerRegistration?.remove()
        listenerRegistration = null
        _binding = null
    }
}
