package com.gramavaxi.ui.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.gramavaxi.R
import com.gramavaxi.data.model.Animal
import com.gramavaxi.databinding.FragmentVaccineCalendarBinding
import com.gramavaxi.ui.AppViewModelFactory
import com.gramavaxi.ui.common.statusColor
import com.gramavaxi.ui.common.statusLabel
import com.gramavaxi.util.DateUtils

class VaccineCalendarFragment : Fragment() {
    private var _binding: FragmentVaccineCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VaccineCalendarViewModel by viewModels {
        AppViewModelFactory.from(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVaccineCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.animals.observe(viewLifecycleOwner) { animals ->
            binding.calendarList.removeAllViews()
            if (animals.isEmpty()) {
                binding.calendarList.addView(TextView(requireContext()).apply {
                    text = getString(R.string.empty_calendar)
                    textSize = 15f
                })
                return@observe
            }
            animals.sortedBy { it.nextShotDate }.forEach { addCalendarRow(it) }
        }
    }

    private fun addCalendarRow(animal: Animal) {
        val context = requireContext()
        binding.calendarList.addView(TextView(context).apply {
            text = getString(
                R.string.calendar_row,
                animal.name,
                DateUtils.formatDate(animal.nextShotDate),
                context.statusLabel(animal)
            )
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(24, 20, 24, 20)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f
                setColor(ContextCompat.getColor(context, R.color.surface))
                setStroke(5, context.statusColor(animal))
            }
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 12) }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
