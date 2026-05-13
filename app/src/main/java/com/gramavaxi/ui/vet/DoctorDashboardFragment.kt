package com.gramavaxi.ui.vet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.ListenerRegistration
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.firebase.VetSickReport
import com.gramavaxi.util.SessionManager

class DoctorDashboardFragment : Fragment() {
    private var listenerRegistration: ListenerRegistration? = null
    private lateinit var rootLayout: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 20.dp(), 20.dp(), 96.dp())
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background))
        }
        return rootLayout
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        render(emptyList(), loading = true)
        listenerRegistration = FirebaseSyncService(requireContext())
            .listenVetSickReports(SessionManager.currentFarmerPhone(requireContext())) { reports ->
                if (::rootLayout.isInitialized) render(reports)
            }
    }

    private fun render(reports: List<VetSickReport>, loading: Boolean = false) {
        rootLayout.removeAllViews()
        rootLayout.addView(title(getString(R.string.doctor_dashboard_title)))
        if (loading) {
            rootLayout.addView(body(getString(R.string.loading_reports)))
            return
        }
        val pending = reports.count { it.status == "pending" }
        val approved = reports.count { it.status == "accepted" || it.status == "approved" }
        val completed = reports.count { it.status == "completed" }
        rootLayout.addView(summaryCard(getString(R.string.pending_sick_reports), pending, R.color.status_amber))
        rootLayout.addView(summaryCard(getString(R.string.approved_visits), approved, R.color.brand_primary))
        rootLayout.addView(summaryCard(getString(R.string.completed_treatments), completed, R.color.status_green))
    }

    private fun summaryCard(label: String, count: Int, colorRes: Int): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 16.dp())
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 12.dp(), 0, 0)
            layoutParams = lp
            setOnClickListener { findNavController().navigate(R.id.doctorReportsFragment) }
            addView(TextView(requireContext()).apply {
                text = count.toString()
                textSize = 28f
                setTextColor(ContextCompat.getColor(requireContext(), colorRes))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(requireContext()).apply {
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            })
            addView(Button(requireContext()).apply {
                text = getString(R.string.view_reports)
                setOnClickListener { findNavController().navigate(R.id.doctorReportsFragment) }
            })
        }
    }

    private fun title(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 28f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun body(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 15f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        listenerRegistration?.remove()
        listenerRegistration = null
        super.onDestroyView()
    }
}
