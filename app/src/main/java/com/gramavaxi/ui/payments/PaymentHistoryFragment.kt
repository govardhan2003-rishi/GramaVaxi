package com.gramavaxi.ui.payments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.gramavaxi.R
import com.gramavaxi.data.model.DiseaseReport
import com.gramavaxi.databinding.FragmentPaymentHistoryBinding
import com.gramavaxi.ui.AppViewModelFactory
import com.gramavaxi.util.DateUtils

class PaymentHistoryFragment : Fragment() {
    private var _binding: FragmentPaymentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PaymentHistoryViewModel by viewModels {
        AppViewModelFactory.from(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPaymentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.reports.observe(viewLifecycleOwner) { reports ->
            renderPayments(reports)
        }
    }

    private fun renderPayments(reports: List<DiseaseReport>) {
        binding.paymentList.removeAllViews()
        val paymentReports = reports.filter { it.paymentMethod.isNotBlank() || it.consultationFee > 0 }
        if (paymentReports.isEmpty()) {
            binding.paymentList.addView(TextView(requireContext()).apply {
                text = getString(R.string.no_payment_history)
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })
            return
        }
        paymentReports.forEach { report ->
            binding.paymentList.addView(paymentRow(report).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
            })
        }
    }

    private fun paymentRow(report: DiseaseReport): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)

            addView(TextView(requireContext()).apply {
                text = getString(R.string.payment_history_animal, report.animalName.ifBlank { getString(R.string.report_sick) })
                textSize = 18f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            })
            addView(TextView(requireContext()).apply {
                text = getString(R.string.payment_history_amount, report.consultationFee)
                textSize = 15f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
            })
            addView(TextView(requireContext()).apply {
                text = getString(R.string.payment_history_status, report.paymentStatus.ifBlank { getString(R.string.payment_pending_visit) })
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })
            addView(TextView(requireContext()).apply {
                text = getString(R.string.payment_history_date, DateUtils.formatDate(report.reportedAt))
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
