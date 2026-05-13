package com.gramavaxi.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gramavaxi.R
import com.gramavaxi.data.db.AppDatabase
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.data.model.Farmer
import com.gramavaxi.databinding.FragmentLoginBinding
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private var isSignUpMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (SessionManager.isLoggedIn(requireContext())) {
            openHomeForRole()
            return
        }
        binding.roleSpinner.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.role_options,
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.genderSpinner.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.gender_options,
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.roleSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateRoleUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.loginButton.setOnClickListener {
            if (isSignUpMode) signUp() else login()
        }
        binding.signUpModeButton.setOnClickListener { toggleSignUpMode() }
        binding.forgotPasswordButton.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgotPassword)
        }
        updateRoleUi()
    }

    private fun updateRoleUi() {
        val isDoctor = isDoctorRole()
        binding.loginTitleText.text = getString(
            when {
                isSignUpMode && isDoctor -> R.string.doctor_signup_title
                isSignUpMode -> R.string.farmer_signup_title
                isDoctor -> R.string.doctor_login_title
                else -> R.string.login_title
            }
        )
        binding.loginSubtitleText.text = getString(
            when {
                isSignUpMode -> R.string.signup_subtitle
                isDoctor -> R.string.doctor_login_subtitle
                else -> R.string.login_subtitle
            }
        )
        binding.farmerNameInput.hint = getString(if (isDoctor) R.string.doctor_name else R.string.farmer_name)
        binding.genderSpinner.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.dateOfBirthInput.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.emailInput.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.confirmPasswordContainer.visibility = if (isSignUpMode) View.VISIBLE else View.GONE
        binding.forgotPasswordButton.visibility = if (isSignUpMode || isDoctor) View.GONE else View.VISIBLE
        binding.loginButton.text = getString(if (isSignUpMode) R.string.create_account else R.string.login)
        binding.signUpModeButton.text = getString(if (isSignUpMode) R.string.back_to_login else R.string.sign_up)
    }

    private fun toggleSignUpMode() {
        isSignUpMode = !isSignUpMode
        updateRoleUi()
    }

    private fun login() {
        val name = binding.farmerNameInput.text.toString().trim()
        val phone = normalizedPhone(binding.phoneInput.text.toString())
        val password = binding.passwordInput.text.toString().trim()
        if (name.isBlank() || phone.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), R.string.details_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (isDoctorRole()) {
            loginDoctor(name, phone, password)
        } else {
            loginFarmer(phone, password)
        }
    }

    private fun loginFarmer(phone: String, password: String) {
        lifecycleScope.launch {
            runCatching {
                val dao = AppDatabase.getInstance(requireContext()).farmerDao()
                val firebaseSyncService = FirebaseSyncService(requireContext())
                val remoteFarmer = firebaseSyncService.login(phone, password)
                if (remoteFarmer == null || normalizedPhone(remoteFarmer.phone) != phone) {
                    Toast.makeText(requireContext(), R.string.login_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val localFarmer = dao.findByPhone(phone)
                val farmerId = if (localFarmer == null) {
                    dao.insert(remoteFarmer.copy(id = 0, password = "")).toInt()
                } else {
                    localFarmer.id
                }
                SessionManager.saveSession(requireContext(), farmerId, remoteFarmer.name, remoteFarmer.phone, SessionManager.ROLE_FARMER)
                openDashboard()
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: getString(R.string.login_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loginDoctor(name: String, phone: String, password: String) {
        lifecycleScope.launch {
            runCatching {
                val doctor = FirebaseSyncService(requireContext()).loginDoctor(phone, password)
                if (doctor == null || doctor.name != name) {
                    Toast.makeText(requireContext(), R.string.login_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                SessionManager.saveSession(requireContext(), 0, doctor.name, doctor.phone, SessionManager.ROLE_DOCTOR)
                openDoctorHome()
            }.onFailure { error ->
                Toast.makeText(requireContext(), error.message ?: getString(R.string.login_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signUp() {
        val name = binding.farmerNameInput.text.toString().trim()
        val phone = normalizedPhone(binding.phoneInput.text.toString())
        val password = binding.passwordInput.text.toString().trim()
        val confirmPassword = binding.confirmPasswordInput.text.toString().trim()
        val dateOfBirth = binding.dateOfBirthInput.text.toString().trim()
        val gender = binding.genderSpinner.selectedItem?.toString().orEmpty()
        val email = binding.emailInput.text.toString().trim()
        if (name.isBlank() || phone.isBlank() || password.isBlank()) {
            Toast.makeText(requireContext(), R.string.details_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (dateOfBirth.isBlank()) {
            Toast.makeText(requireContext(), R.string.date_of_birth_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), R.string.email_required_for_auth, Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(requireContext(), R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show()
            return
        }
        if (isDoctorRole()) {
            signUpDoctor(name, phone, password, gender, dateOfBirth, email)
        } else {
            signUpFarmer(name, phone, password, gender, dateOfBirth, email)
        }
    }

    private fun signUpDoctor(name: String, phone: String, password: String, gender: String, dob: String, email: String) {
        lifecycleScope.launch {
            runCatching {
                val result = FirebaseSyncService(requireContext()).createVetDoctorAccount(name, phone, password, gender, dob, "", email)
                if (result.success) {
                    SessionManager.saveSession(requireContext(), 0, name, phone, SessionManager.ROLE_DOCTOR)
                    openDoctorHome()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.firebase_sync_failed_detail, result.errorMessage), Toast.LENGTH_LONG).show()
                }
            }.onFailure {
                Toast.makeText(requireContext(), getString(R.string.firebase_sync_failed_detail, it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun signUpFarmer(name: String, phone: String, password: String, gender: String, dob: String, email: String) {
        lifecycleScope.launch {
            runCatching {
                val dao = AppDatabase.getInstance(requireContext()).farmerDao()
                if (dao.findByPhone(phone) != null || dao.findByPhone(binding.phoneInput.text.toString().trim()) != null) {
                    Toast.makeText(requireContext(), R.string.account_exists, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val farmer = Farmer(
                    name = name,
                    village = getString(R.string.default_village),
                    phone = phone,
                    password = password,
                    gender = gender,
                    dateOfBirth = dob,
                    email = email
                )
                val farmerId = dao.insert(farmer).toInt()
                val result = FirebaseSyncService(requireContext()).registerFarmer(farmer.copy(id = farmerId))
                SessionManager.saveSession(requireContext(), farmerId, name, phone, SessionManager.ROLE_FARMER)
                if (!result.success) {
                    Toast.makeText(requireContext(), getString(R.string.sms_alerts_enabled), Toast.LENGTH_LONG).show()
                }
                openDashboard()
            }.onFailure {
                Toast.makeText(requireContext(), getString(R.string.firebase_sync_failed_detail, it.message ?: it.javaClass.simpleName), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDashboard() {
        findNavController().navigate(R.id.action_login_to_dashboard)
    }

    private fun openDoctorHome() {
        findNavController().navigate(R.id.action_login_to_vet)
    }

    private fun openHomeForRole() {
        if (SessionManager.currentRole(requireContext()) == SessionManager.ROLE_DOCTOR) {
            openDoctorHome()
        } else {
            openDashboard()
        }
    }

    private fun isDoctorRole(): Boolean = binding.roleSpinner.selectedItemPosition == ROLE_DOCTOR_POSITION

    private fun normalizedPhone(phone: String): String {
        val trimmed = phone.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        return when {
            trimmed.startsWith("+") -> trimmed
            digitsOnly.length == 10 -> "+91$digitsOnly"
            digitsOnly.length == 12 && digitsOnly.startsWith("91") -> "+$digitsOnly"
            else -> trimmed
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ROLE_DOCTOR_POSITION = 1
    }
}
