package com.gramavaxi.ui.vet

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.ListenerRegistration
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.firebase.VetSickReport
import com.gramavaxi.util.DateUtils
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class DoctorReportsFragment : Fragment() {
    private var listenerRegistration: ListenerRegistration? = null
    private lateinit var rootLayout: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 20.dp(), 20.dp(), 96.dp())
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background))
        }
        return android.widget.ScrollView(requireContext()).apply { addView(rootLayout) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rootLayout.addView(title(getString(R.string.doctor_reports_title)))
        listenerRegistration = FirebaseSyncService(requireContext())
            .listenVetSickReports(SessionManager.currentFarmerPhone(requireContext())) { reports ->
                render(reports)
            }
    }

    private fun render(reports: List<VetSickReport>) {
        rootLayout.removeAllViews()
        rootLayout.addView(title(getString(R.string.doctor_reports_title)))
        if (reports.isEmpty()) {
            rootLayout.addView(body(getString(R.string.no_vet_reports)))
            return
        }
        listOf("pending", "accepted", "completed").forEach { status ->
            val matching = reports.filter { it.status == status || (status == "accepted" && it.status == "approved") }
            rootLayout.addView(sectionTitle(status.replaceFirstChar { it.uppercase() }))
            if (matching.isEmpty()) rootLayout.addView(body(getString(R.string.no_reports_in_category)))
            matching.forEach { rootLayout.addView(reportCard(it)) }
        }
    }

    private fun reportCard(report: VetSickReport): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 8.dp(), 0, 12.dp())
            }
            addView(body(getString(R.string.vet_report_farmer, report.farmerName, report.farmerPhone)))
            addView(body(getString(R.string.vet_report_animal, report.animalName)))
            addView(body(getString(R.string.vet_report_symptoms, report.symptoms)))
            addView(body(getString(R.string.vet_report_status, report.status)))
            addView(body(getString(R.string.vet_report_time, DateUtils.formatDate(report.reportedAt))))
            addView(body(locationText(report)))
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(actionButton(getString(R.string.approve_report)) { updateStatus(report, "accepted") })
                addView(actionButton(getString(R.string.complete_case)) { updateStatus(report, "completed") })
            })
            addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(actionButton(getString(R.string.navigate)) { openNavigation(report) })
                addView(actionButton(getString(R.string.contact_farmer)) {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${report.farmerPhone}")))
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
        return if (latitude == null || longitude == null) getString(R.string.vet_report_no_location)
        else getString(R.string.vet_report_location, latitude, longitude)
    }

    private fun actionButton(label: String, onClick: () -> Unit) = Button(requireContext()).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun title(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 28f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun sectionTitle(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 20f
        setPadding(0, 18.dp(), 0, 0)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun body(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 14f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        listenerRegistration?.remove()
        listenerRegistration = null
        super.onDestroyView()
    }
}
