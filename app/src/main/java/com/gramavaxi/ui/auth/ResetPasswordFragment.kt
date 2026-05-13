package com.gramavaxi.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.databinding.FragmentResetPasswordBinding
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class ResetPasswordFragment : Fragment() {
    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val changeMode = AuthOtpStore.mode == AuthOtpStore.MODE_CHANGE
        binding.currentPasswordInput.visibility = View.GONE
        if (changeMode) {
            binding.currentPasswordInput.setText(AuthOtpStore.currentPassword)
        } else {
            binding.newPasswordInput.visibility = View.GONE
            binding.confirmNewPasswordInput.visibility = View.GONE
            binding.resetPasswordButton.text = getString(R.string.send_password_reset_link)
        }
        binding.resetPasswordHelp.text = getString(
            if (changeMode) R.string.change_password_help else R.string.reset_password_secure_help
        )
        binding.resetPasswordButton.setOnClickListener { resetPassword(changeMode) }
    }

    private fun resetPassword(changeMode: Boolean) {
        val currentPassword = binding.currentPasswordInput.text.toString().trim()
        val newPassword = binding.newPasswordInput.text.toString().trim()
        val confirmPassword = binding.confirmNewPasswordInput.text.toString().trim()
        if (changeMode) {
            if (newPassword.length < 8) {
                Toast.makeText(requireContext(), R.string.password_too_short, Toast.LENGTH_SHORT).show()
                return
            }
            if (newPassword != confirmPassword) {
                Toast.makeText(requireContext(), R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show()
                return
            }
        }
        binding.resetPasswordButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val service = FirebaseSyncService(requireContext())
            val result = if (changeMode) {
                service.changeCurrentUserPassword(SessionManager.currentFarmerPhone(requireContext()), currentPassword, newPassword)
            } else {
                service.sendPasswordResetForIdentifier(AuthOtpStore.identifier)
            }
            binding.progressBar.visibility = View.GONE
            binding.resetPasswordButton.isEnabled = true
            Toast.makeText(
                requireContext(),
                if (result.success) getString(if (changeMode) R.string.password_changed else R.string.password_reset_sent)
                else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                Toast.LENGTH_LONG
            ).show()
            if (result.success) {
                AuthOtpStore.currentPassword = ""
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
