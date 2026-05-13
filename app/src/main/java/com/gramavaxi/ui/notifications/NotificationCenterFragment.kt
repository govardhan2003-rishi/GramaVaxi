package com.gramavaxi.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.gramavaxi.R
import com.gramavaxi.databinding.FragmentNotificationCenterBinding

class NotificationCenterFragment : Fragment() {
    private var _binding: FragmentNotificationCenterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationCenterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showLatestFeedback()
        binding.submitFeedbackButton.setOnClickListener { saveFeedback() }
    }

    private fun saveFeedback() {
        val name = binding.feedbackNameInput.text.toString().trim()
        val message = binding.feedbackMessageInput.text.toString().trim()
        if (message.isBlank()) {
            Toast.makeText(requireContext(), R.string.feedback_required, Toast.LENGTH_SHORT).show()
            return
        }

        requireContext().getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_NAME, name)
            .putString(KEY_MESSAGE, message)
            .putFloat(KEY_RATING, binding.feedbackRatingBar.rating)
            .apply()

        binding.feedbackMessageInput.text?.clear()
        Toast.makeText(requireContext(), R.string.feedback_saved, Toast.LENGTH_SHORT).show()
        showLatestFeedback()
    }

    private fun showLatestFeedback() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)
        val message = prefs.getString(KEY_MESSAGE, null)
        if (message.isNullOrBlank()) {
            binding.latestFeedbackText.text = getString(R.string.no_feedback_yet)
            return
        }

        val name = prefs.getString(KEY_NAME, "").orEmpty()
        val rating = prefs.getFloat(KEY_RATING, 5f).toInt()
        binding.latestFeedbackText.text = getString(
            R.string.latest_feedback,
            if (name.isBlank()) getString(R.string.anonymous_feedback) else name,
            rating,
            message
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val PREFS_NAME = "customer_feedback"
        private const val KEY_NAME = "name"
        private const val KEY_MESSAGE = "message"
        private const val KEY_RATING = "rating"
    }
}
