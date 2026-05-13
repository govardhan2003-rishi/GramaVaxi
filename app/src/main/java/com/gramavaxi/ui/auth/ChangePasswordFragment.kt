package com.gramavaxi.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gramavaxi.R
import com.gramavaxi.databinding.FragmentChangePasswordBinding
import com.gramavaxi.util.SessionManager

class ChangePasswordFragment : Fragment() {
    private var _binding: FragmentChangePasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.currentPhoneText.text = SessionManager.currentFarmerPhone(requireContext())
        binding.continueChangePasswordButton.setOnClickListener {
            val current = binding.currentPasswordInput.text.toString().trim()
            if (current.isBlank()) {
                Toast.makeText(requireContext(), R.string.password_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            AuthOtpStore.mode = AuthOtpStore.MODE_CHANGE
            AuthOtpStore.identifier = SessionManager.currentFarmerPhone(requireContext())
            AuthOtpStore.currentPassword = current
            findNavController().navigate(R.id.action_changePassword_to_resetPassword)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
