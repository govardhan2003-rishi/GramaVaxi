package com.gramavaxi.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.databinding.FragmentOtpVerificationBinding
import kotlinx.coroutines.launch

class OtpVerificationFragment : Fragment() {
    private var _binding: FragmentOtpVerificationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOtpVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.otpTargetText.text = getString(R.string.otp_sending, AuthOtpStore.identifier)
        binding.verifyOtpButton.setOnClickListener { verifyOtp() }
    }

    private fun verifyOtp() {
        val code = binding.otpInput.text.toString().trim()
        if (code.length < 6 || AuthOtpStore.verificationId.isBlank()) {
            Toast.makeText(requireContext(), R.string.otp_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        binding.verifyOtpButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        val credential = PhoneAuthProvider.getCredential(AuthOtpStore.verificationId, code)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener {
                if (AuthOtpStore.mode == AuthOtpStore.MODE_FORGOT) {
                    sendResetLinkAfterOtp()
                } else {
                    binding.progressBar.visibility = View.GONE
                    findNavController().navigate(R.id.action_otpVerification_to_resetPassword)
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
                binding.verifyOtpButton.isEnabled = true
                Toast.makeText(requireContext(), getString(R.string.otp_failed, it.message), Toast.LENGTH_LONG).show()
            }
    }

    private fun sendResetLinkAfterOtp() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = FirebaseSyncService(requireContext()).sendPasswordResetForIdentifier(AuthOtpStore.identifier)
            FirebaseAuth.getInstance().signOut()
            if (_binding == null) return@launch
            binding.progressBar.visibility = View.GONE
            binding.verifyOtpButton.isEnabled = true
            Toast.makeText(
                requireContext(),
                if (result.success) getString(R.string.password_reset_sent) else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                Toast.LENGTH_LONG
            ).show()
            if (result.success) findNavController().navigate(R.id.loginFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
