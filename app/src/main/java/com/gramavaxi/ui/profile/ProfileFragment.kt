package com.gramavaxi.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.gramavaxi.R
import com.gramavaxi.data.db.AppDatabase
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.model.Farmer
import com.gramavaxi.databinding.FragmentProfileBinding
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentFarmer: Farmer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.saveProfileButton.setOnClickListener { saveProfile() }
        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val farmerId = SessionManager.currentFarmerId(requireContext())
            val phone = SessionManager.currentFarmerPhone(requireContext())
            val dao = AppDatabase.getInstance(requireContext()).farmerDao()
            currentFarmer = dao.getById(farmerId)
                ?: FirebaseSyncService(requireContext()).findFarmerByPhone(phone)

            val farmer = currentFarmer
            if (farmer == null) {
                binding.profileSummary.text = getString(R.string.profile_missing)
                return@launch
            }

            binding.profileSummary.text = getString(R.string.profile_summary, farmer.name, farmer.village)
            binding.profileNameInput.setText(farmer.name)
            binding.profileVillageInput.setText(farmer.village)
            binding.profilePhoneInput.setText(farmer.phone)
            binding.profilePasswordInput.setText(farmer.password)
        }
    }

    private fun saveProfile() {
        val existing = currentFarmer ?: return
        val name = binding.profileNameInput.text.toString().trim()
        val village = binding.profileVillageInput.text.toString().trim()
        val phone = binding.profilePhoneInput.text.toString().trim()
        val password = binding.profilePasswordInput.text.toString().trim()

        if (name.isBlank() || village.isBlank() || phone.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), R.string.register_required, Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val updatedFarmer = existing.copy(
                name = name,
                village = village,
                phone = phone,
                password = password
            )
            AppDatabase.getInstance(requireContext()).farmerDao().update(updatedFarmer)
            val firebaseResult = FirebaseSyncService(requireContext()).uploadFarmer(updatedFarmer)
            SessionManager.saveSession(requireContext(), updatedFarmer.id, updatedFarmer.name, updatedFarmer.phone)
            currentFarmer = updatedFarmer
            binding.profileSummary.text = getString(R.string.profile_summary, updatedFarmer.name, updatedFarmer.village)
            Toast.makeText(
                requireContext(),
                if (firebaseResult.success) {
                    getString(R.string.profile_saved)
                } else {
                    getString(R.string.firebase_sync_failed_detail, firebaseResult.errorMessage)
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
