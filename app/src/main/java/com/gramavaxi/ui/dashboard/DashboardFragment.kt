package com.gramavaxi.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.gramavaxi.R
import com.gramavaxi.data.model.Animal
import com.gramavaxi.databinding.FragmentDashboardBinding
import com.gramavaxi.ui.AppViewModelFactory
import com.gramavaxi.ui.common.animalRow
import com.gramavaxi.util.DateUtils

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels {
        AppViewModelFactory.from(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.totalAnimalsTile.tileLabel.setText(R.string.total_animals)
        binding.dueSoonTile.tileLabel.setText(R.string.due_soon)
        binding.sickCountTile.tileLabel.setText(R.string.sick_count)

        binding.addAnimalButton.setOnClickListener {
            findNavController().navigate(R.id.action_dashboard_to_registerAnimal)
        }
        binding.reportSickButton.setOnClickListener {
            findNavController().navigate(R.id.diseaseReportFragment)
        }

        viewModel.animals.observe(viewLifecycleOwner) { animals ->
            renderSummary(animals)
            renderRecentAnimals(animals)
        }
    }

    private fun renderSummary(animals: List<Animal>) {
        val dueSoon = animals.count { DateUtils.daysUntil(it.nextShotDate) <= 14 }
        binding.totalAnimalsTile.tileValue.text = animals.size.toString()
        binding.dueSoonTile.tileValue.text = dueSoon.toString()
        binding.sickCountTile.tileValue.text = animals.count { it.isSick }.toString()
    }

    private fun renderRecentAnimals(animals: List<Animal>) {
        binding.recentAnimals.removeAllViews()
        if (animals.isEmpty()) {
            binding.recentAnimals.addView(TextView(requireContext()).apply {
                text = getString(R.string.empty_animals)
                textSize = 15f
            })
            return
        }
        animals.take(5).forEach { animal ->
            binding.recentAnimals.addView(requireContext().animalRow(animal).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
            })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
