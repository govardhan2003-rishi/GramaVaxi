package com.gramavaxi.ui.vet

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gramavaxi.R
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class DoctorProfileFragment : Fragment() {
    private lateinit var rootLayout: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var genderInput: EditText
    private lateinit var dobInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var upiInput: EditText
    private lateinit var feeInput: EditText
    private lateinit var availabilityInput: EditText
    private var doctorLocation: Location? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 24.dp(), 24.dp(), 116.dp())
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background))
        }
        return ScrollView(requireContext()).apply {
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.background))
            isFillViewport = true
            addView(rootLayout)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        buildForm()
        loadProfile()
    }

    private fun buildForm() {
        rootLayout.removeAllViews()
        rootLayout.addView(title(getString(R.string.doctor_profile_title)))
        rootLayout.addView(subtitle(getString(R.string.doctor_profile_hint)))
        nameInput = input(getString(R.string.doctor_name), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        phoneInput = input(getString(R.string.doctor_phone), InputType.TYPE_CLASS_PHONE)
        genderInput = input(getString(R.string.gender), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        dobInput = input(getString(R.string.date_of_birth), InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE)
        emailInput = input(getString(R.string.email_optional), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        upiInput = input(getString(R.string.doctor_upi_id), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        feeInput = input(getString(R.string.consultation_amount), InputType.TYPE_CLASS_NUMBER)
        availabilityInput = input(getString(R.string.availability_status), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        listOf(nameInput, phoneInput, genderInput, dobInput, emailInput, upiInput, feeInput, availabilityInput).forEach(rootLayout::addView)
        rootLayout.addView(primaryButton(getString(R.string.save_doctor_profile)) { saveProfile() })
        rootLayout.addView(linkAction(getString(R.string.change_password)) { findNavController().navigate(R.id.changePasswordFragment) })
        rootLayout.addView(linkAction(getString(R.string.logout)) {
            SessionManager.logout(requireContext())
            findNavController().navigate(R.id.loginFragment)
        })
    }

    private fun loadProfile() {
        nameInput.setText(SessionManager.currentFarmerName(requireContext()))
        phoneInput.setText(SessionManager.currentFarmerPhone(requireContext()))
        lifecycleScope.launch {
            val doctor = FirebaseSyncService(requireContext()).findVetDoctorByPhone(phoneInput.text.toString().trim())
            if (doctor == null) return@launch
            nameInput.setText(doctor.name)
            phoneInput.setText(doctor.phone)
            genderInput.setText(doctor.gender)
            dobInput.setText(doctor.dateOfBirth)
            emailInput.setText(doctor.email)
            upiInput.setText(doctor.upiId)
            feeInput.setText(doctor.consultationFee.toString())
            availabilityInput.setText(doctor.availabilityStatus)
            if (doctor.latitude != 0.0 || doctor.longitude != 0.0) {
                doctorLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
                    latitude = doctor.latitude
                    longitude = doctor.longitude
                }
            }
        }
    }

    private fun saveProfile() {
        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        if (name.isBlank() || phone.isBlank()) {
            Toast.makeText(requireContext(), R.string.doctor_registration_required, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = FirebaseSyncService(requireContext()).updateVetDoctorProfile(
                name = name,
                phone = phone,
                gender = genderInput.text.toString().trim(),
                dateOfBirth = dobInput.text.toString().trim(),
                email = emailInput.text.toString().trim(),
                latitude = doctorLocation?.latitude,
                longitude = doctorLocation?.longitude,
                upiId = upiInput.text.toString().trim(),
                consultationFee = feeInput.text.toString().toIntOrNull() ?: 99,
                availabilityStatus = availabilityInput.text.toString().trim().ifBlank { "Available" }
            )
            Toast.makeText(
                requireContext(),
                if (result.success) getString(R.string.doctor_profile_saved) else getString(R.string.firebase_sync_failed_detail, result.errorMessage),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun input(hintValue: String, inputTypeValue: Int) = EditText(requireContext()).apply {
        hint = hintValue
        inputType = inputTypeValue
        setSingleLine(true)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        textSize = 16f
        minHeight = 48.dp()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12.dp()
        }
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(requireContext()).apply {
        text = label
        backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        setTextColor(ContextCompat.getColor(requireContext(), R.color.surface))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 18.dp()
        }
    }

    private fun linkAction(label: String, onClick: () -> Unit) = TextView(requireContext()).apply {
        text = label
        gravity = Gravity.CENTER
        minHeight = 44.dp()
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8.dp()
        }
    }

    private fun title(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 28f
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        setTypeface(typeface, Typeface.BOLD)
    }

    private fun subtitle(textValue: String) = TextView(requireContext()).apply {
        text = textValue
        textSize = 15f
        setPadding(0, 8.dp(), 0, 8.dp())
        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
