package com.gramavaxi.ui.vet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ListenerRegistration
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.firebase.VetSickReport
import com.gramavaxi.util.DateUtils
import com.gramavaxi.util.SessionManager

class DoctorPaymentHistoryFragment : Fragment() {
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
        listenerRegistration = FirebaseSyncService(requireContext())
            .listenVetSickReports(SessionManager.currentFarmerPhone(requireContext())) { reports ->
                render(reports)
            }
    }

    private fun render(reports: List<VetSickReport>) {
        rootLayout.removeAllViews()
        rootLayout.addView(title(getString(R.string.payment_history_title)))
        val paid = reports.filter { it.paymentStatus.contains("paid", ignoreCase = true) }
        val pending = reports.filter { !it.paymentStatus.contains("paid", ignoreCase = true) && it.consultationFee > 0 }
        rootLayout.addView(metric(getString(R.string.total_earnings), paid.sumOf { it.consultationFee }))
        rootLayout.addView(metric(getString(R.string.pending_payments), pending.sumOf { it.consultationFee }))
        rootLayout.addView(metric(getString(R.string.completed_payments), paid.size))
        if (reports.none { it.consultationFee > 0 }) {
            rootLayout.addView(body(getString(R.string.no_payment_history)))
            return
        }
        reports.filter { it.consultationFee > 0 }.forEach { rootLayout.addView(paymentCard(it)) }
    }

    private fun metric(label: String, value: Int): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 10.dp(), 0, 0)
        }
        addView(TextView(requireContext()).apply {
            text = value.toString()
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        })
        addView(body(label))
    }

    private fun paymentCard(report: VetSickReport): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 12.dp(), 0, 0)
        }
        addView(body(getString(R.string.vet_report_farmer, report.farmerName, report.farmerPhone)))
        addView(body(getString(R.string.payment_history_amount, report.consultationFee)))
        addView(body(getString(R.string.payment_history_status, report.paymentStatus.ifBlank { getString(R.string.payment_pending_visit) })))
        addView(body(getString(R.string.transaction_id_pending)))
        addView(body(getString(R.string.payment_history_date, DateUtils.formatDate(report.reportedAt))))
    }

    private fun title(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 28f
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
