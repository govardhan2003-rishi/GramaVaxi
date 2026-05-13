package com.gramavaxi.ui.ledger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.setMargins
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.gramavaxi.R
import com.gramavaxi.databinding.FragmentAnimalLedgerBinding
import com.gramavaxi.ui.AppViewModelFactory
import com.gramavaxi.ui.common.animalRow

class AnimalLedgerFragment : Fragment() {
    private var _binding: FragmentAnimalLedgerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnimalLedgerViewModel by viewModels {
        AppViewModelFactory.from(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnimalLedgerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.addAnimalButton.setOnClickListener {
            findNavController().navigate(R.id.action_ledger_to_registerAnimal)
        }
        viewModel.animals.observe(viewLifecycleOwner) { animals ->
            binding.animalList.removeAllViews()
            if (animals.isEmpty()) {
                binding.animalList.addView(TextView(requireContext()).apply {
                    text = getString(R.string.empty_animals)
                    textSize = 15f
                })
            } else {
                animals.forEach { animal ->
                    binding.animalList.addView(requireContext().animalRow(animal) { selectedAnimal ->
                        confirmDelete(selectedAnimal)
                    }.apply {
                        layoutParams = ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 12) }
                    })
                }
            }
        }
        viewModel.deleteMessage.observe(viewLifecycleOwner) { name ->
            if (name == null) return@observe
            Snackbar.make(
                binding.root,
                if (name.isBlank()) getString(R.string.delete_animal_failed) else getString(R.string.animal_deleted, name),
                Snackbar.LENGTH_LONG
            ).show()
            viewModel.clearDeleteMessage()
        }
    }

    private fun confirmDelete(animal: com.gramavaxi.data.model.Animal) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_animal)
            .setMessage(getString(R.string.delete_animal_confirm, animal.name))
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteAnimal(animal) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
