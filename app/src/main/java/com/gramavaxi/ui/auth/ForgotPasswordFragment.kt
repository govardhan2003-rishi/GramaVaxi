package com.gramavaxi.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseWriteResult
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.databinding.FragmentForgotPasswordBinding
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ForgotPasswordFragment : Fragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private var otpTimeoutJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.sendOtpButton.text = getString(R.string.send_otp_or_reset_link)
        binding.sendOtpButton.setOnClickListener { startReset() }
    }

    private fun startReset() {
        val identifier = binding.identifierInput.text.toString().trim()
        if (identifier.isBlank()) {
            Toast.makeText(requireContext(), R.string.email_or_phone_required, Toast.LENGTH_SHORT).show()
            return
        }
        val isEmail = Patterns.EMAIL_ADDRESS.matcher(identifier).matches()
        val normalizedIdentifier = if (isEmail) identifier else normalizedPhone(identifier)
        val isPhone = normalizedIdentifier.matches(Regex("^\\+[1-9][0-9]{9,14}$"))
        if (!isEmail && !isPhone) {
            Toast.makeText(requireContext(), R.string.invalid_email_or_phone, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        if (isPhone) {
            startPhoneOtp(normalizedIdentifier)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                FirebaseSyncService(requireContext()).sendPasswordResetForIdentifier(normalizedIdentifier)
            }.getOrElse {
                FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName)
            }
            if (_binding == null) return@launch
            setLoading(false)
            Toast.makeText(
                requireContext(),
                if (result.success) getString(R.string.password_reset_sent) else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                Toast.LENGTH_LONG
            ).show()
            if (result.success) findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun startPhoneOtp(phone: String) {
        AuthOtpStore.identifier = phone
        AuthOtpStore.mode = AuthOtpStore.MODE_FORGOT
        AuthOtpStore.verificationId = ""
        AuthOtpStore.resendToken = null
        startOtpTimeout()
        val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                    cancelOtpTimeout()
                    FirebaseAuth.getInstance().signInWithCredential(credential)
                        .addOnSuccessListener {
                            sendResetLinkAfterOtp()
                        }
                        .addOnFailureListener { error ->
                            if (_binding == null) return@addOnFailureListener
                            setLoading(false)
                            Toast.makeText(requireContext(), getString(R.string.otp_failed, error.message), Toast.LENGTH_LONG).show()
                        }
                }

                override fun onVerificationFailed(error: FirebaseException) {
                    if (_binding == null) return
                    cancelOtpTimeout()
                    setLoading(false)
                    Toast.makeText(requireContext(), getString(R.string.otp_failed, error.message), Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    if (_binding == null) return
                    cancelOtpTimeout()
                    setLoading(false)
                    AuthOtpStore.verificationId = verificationId
                    AuthOtpStore.resendToken = token
                    findNavController().navigate(R.id.action_forgotPassword_to_otpVerification)
                }
            })
            .build()
        runCatching {
            PhoneAuthProvider.verifyPhoneNumber(options)
        }.onFailure { error ->
            cancelOtpTimeout()
            setLoading(false)
            Toast.makeText(requireContext(), getString(R.string.otp_failed, error.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun sendResetLinkAfterOtp() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                FirebaseSyncService(requireContext()).sendPasswordResetForIdentifier(AuthOtpStore.identifier)
            }.getOrElse {
                FirebaseWriteResult(false, it.message ?: it.javaClass.simpleName)
            }
            FirebaseAuth.getInstance().signOut()
            if (_binding == null) return@launch
            setLoading(false)
            Toast.makeText(
                requireContext(),
                if (result.success) getString(R.string.password_reset_sent) else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                Toast.LENGTH_LONG
            ).show()
            if (result.success) findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun normalizedPhone(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        return when {
            phone.startsWith("+") -> phone
            digitsOnly.length == 10 -> "+91$digitsOnly"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            else -> phone
        }
    }

    private fun setLoading(loading: Boolean) {
        if (_binding == null) return
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.sendOtpButton.isEnabled = !loading
    }

    private fun startOtpTimeout() {
        cancelOtpTimeout()
        otpTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(TimeUnit.SECONDS.toMillis(65))
            if (_binding == null || AuthOtpStore.verificationId.isNotBlank()) return@launch
            setLoading(false)
            Toast.makeText(requireContext(), R.string.otp_request_timeout, Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelOtpTimeout() {
        otpTimeoutJob?.cancel()
        otpTimeoutJob = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cancelOtpTimeout()
        _binding = null
    }
}
