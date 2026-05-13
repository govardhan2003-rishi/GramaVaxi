package com.gramavaxi.ui.profile

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.gramavaxi.R
import com.gramavaxi.data.db.AppDatabase
import com.gramavaxi.data.firebase.FirebaseSyncService
import com.gramavaxi.databinding.FragmentProfileViewBinding
import com.gramavaxi.util.SessionManager
import kotlinx.coroutines.launch

class ProfileViewFragment : Fragment() {
    private var _binding: FragmentProfileViewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.editProfileButton.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_editProfile)
        }
        binding.profileMenuButton.setOnClickListener { showProfileMenu() }
        loadProfile()
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            val farmerId = SessionManager.currentFarmerId(requireContext())
            val phone = SessionManager.currentFarmerPhone(requireContext())
            val dao = AppDatabase.getInstance(requireContext()).farmerDao()
            val farmer = dao.getById(farmerId)
                ?: FirebaseSyncService(requireContext()).findFarmerByPhone(phone)

            if (farmer == null) {
                binding.profileNameText.text = getString(R.string.profile_missing)
                binding.profileVillageText.text = ""
                binding.profilePhoneText.text = ""
                return@launch
            }

            binding.profileNameText.text = farmer.name
            binding.profileInitialsText.text = initials(farmer.name)
            binding.profileVillageText.text = getString(R.string.profile_village_value, farmer.village)
            binding.profilePhoneText.text = getString(R.string.profile_phone_value, farmer.phone)
        }
    }

    private fun showProfileMenu() {
        PopupMenu(requireContext(), binding.profileMenuButton).apply {
            menu.add(MENU_CHANGE_PASSWORD, MENU_CHANGE_PASSWORD, 0, R.string.change_password)
            menu.add(MENU_PAYMENT_HISTORY, MENU_PAYMENT_HISTORY, 1, R.string.payment_history_title)
            menu.add(MENU_FEEDBACK, MENU_FEEDBACK, 2, R.string.customer_feedback_title)
            menu.add(MENU_SUPPORT, MENU_SUPPORT, 3, R.string.customer_support_title)
            menu.add(MENU_SETTINGS, MENU_SETTINGS, 4, R.string.notification_settings)
            menu.add(MENU_LOGOUT, MENU_LOGOUT, 5, R.string.logout)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_CHANGE_PASSWORD -> findNavController().navigate(R.id.action_profile_to_changePassword)
                    MENU_PAYMENT_HISTORY -> findNavController().navigate(R.id.action_profile_to_paymentHistory)
                    MENU_FEEDBACK -> showFeedbackDialog()
                    MENU_SUPPORT -> showSupportDialog()
                    MENU_SETTINGS -> Toast.makeText(requireContext(), R.string.notification_settings_saved, Toast.LENGTH_SHORT).show()
                    MENU_LOGOUT -> {
                        SessionManager.logout(requireContext())
                        findNavController().navigate(R.id.loginFragment)
                    }
                }
                true
            }
            show()
        }
    }

    private fun showFeedbackDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.feedback_message)
            minLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.customer_feedback_title)
            .setView(input)
            .setPositiveButton(R.string.submit_feedback) { _, _ -> saveFeedback(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveFeedback(message: String) {
        if (message.isBlank()) {
            Toast.makeText(requireContext(), R.string.feedback_required, Toast.LENGTH_SHORT).show()
            return
        }

        val name = SessionManager.currentFarmerName(requireContext())
        val phone = SessionManager.currentFarmerPhone(requireContext())
        val rating = DEFAULT_FEEDBACK_RATING

        requireContext().getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_NAME, name)
            .putString(KEY_PHONE, phone)
            .putString(KEY_MESSAGE, message)
            .putFloat(KEY_RATING, DEFAULT_FEEDBACK_RATING.toFloat())
            .apply()

        lifecycleScope.launch {
            val firebaseResult = FirebaseSyncService(requireContext()).uploadFeedback(
                farmerName = name.ifBlank { getString(R.string.anonymous_feedback) },
                farmerPhone = phone,
                rating = rating,
                message = message
            )
            Toast.makeText(
                requireContext(),
                if (firebaseResult.success) {
                    getString(R.string.feedback_saved)
                } else {
                    getString(R.string.firebase_sync_failed_detail, firebaseResult.errorMessage)
                },
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showSupportDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.customer_support_title)
            .setMessage(R.string.customer_support_body)
            .setPositiveButton(R.string.call_support) { _, _ -> openDialer() }
            .setNegativeButton(R.string.email_support) { _, _ -> openSupportEmail() }
            .show()
    }

    private fun openDialer() {
        openSupportIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE")))
    }

    private fun initials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifBlank { "GV" }
    }

    private fun openSupportEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$SUPPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.customer_support_email_subject))
        }
        openSupportIntent(intent)
    }

    private fun openSupportIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.customer_support_title)
                .setMessage(getString(R.string.support_contact_fallback, SUPPORT_PHONE, SUPPORT_EMAIL))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PREFS_NAME = "customer_feedback"
        private const val KEY_NAME = "name"
        private const val KEY_PHONE = "phone"
        private const val KEY_MESSAGE = "message"
        private const val KEY_RATING = "rating"
        private const val SUPPORT_PHONE = "+919876543210"
        private const val SUPPORT_EMAIL = "support@gramavaxi.local"
        private const val MENU_CHANGE_PASSWORD = 1
        private const val MENU_PAYMENT_HISTORY = 2
        private const val MENU_FEEDBACK = 3
        private const val MENU_SUPPORT = 4
        private const val MENU_SETTINGS = 5
        private const val MENU_LOGOUT = 6
        private const val DEFAULT_FEEDBACK_RATING = 5
    }
}
