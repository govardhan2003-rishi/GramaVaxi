package com.gramavaxi

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import com.google.firebase.FirebaseApp
import com.gramavaxi.R
import com.gramavaxi.databinding.ActivityMainBinding
import com.gramavaxi.util.LanguageManager
import com.gramavaxi.util.SessionManager
import com.gramavaxi.worker.NotificationHelper

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val smsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguageManager.applySavedLanguage(this)
        FirebaseApp.initializeApp(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLanguageSelector()
        NotificationHelper.createChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        smsPermission.launch(Manifest.permission.SEND_SMS)
        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        binding.vetButton.setOnClickListener {
            navController.navigate(R.id.doctorDashboardFragment)
        }
        binding.logoutButton.setOnClickListener {
            SessionManager.logout(this)
            navController.navigate(R.id.loginFragment)
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuth = destination.id in authDestinations
            val isDoctor = SessionManager.currentRole(this) == SessionManager.ROLE_DOCTOR
            setupBottomNavigation(navController, if (isDoctor) doctorBottomItems else farmerBottomItems)
            binding.bottomNavigation.visibility = if (isAuth) View.GONE else View.VISIBLE
            updateBottomNavigationSelection(destination.id)
            binding.vetButton.visibility = View.GONE
            binding.logoutButton.visibility = if (isAuth) View.GONE else View.VISIBLE
        }
    }

    private fun setupBottomNavigation(navController: androidx.navigation.NavController, items: List<BottomItem>) {
        binding.bottomNavigationItems.removeAllViews()
        items.forEach { item ->
            binding.bottomNavigationItems.addView(
                bottomNavItem(item) {
                    if (navController.currentDestination?.id != item.destinationId) {
                        navController.navigate(item.destinationId)
                    }
                }
            )
        }
    }

    private fun bottomNavItem(item: BottomItem, onClick: () -> Unit): View {
        return LinearLayout(this).apply {
            tag = item.destinationId
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(10, 6, 10, 6)
            minimumWidth = resources.displayMetrics.widthPixels / 4
            setOnClickListener { onClick() }

            addView(ImageView(this@MainActivity).apply {
                setImageResource(item.iconRes)
                setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.brand_primary))
                layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
            })
            addView(TextView(this@MainActivity).apply {
                text = getString(item.titleRes)
                textSize = 11f
                maxLines = 1
                gravity = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.brand_primary))
            })
        }
    }

    private fun updateBottomNavigationSelection(destinationId: Int) {
        for (index in 0 until binding.bottomNavigationItems.childCount) {
            val itemView = binding.bottomNavigationItems.getChildAt(index) as LinearLayout
            val selected = itemView.tag == destinationId
            itemView.setBackgroundColor(if (selected) ContextCompat.getColor(this, R.color.background) else Color.TRANSPARENT)
            itemView.alpha = if (selected) 1f else 0.78f
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun setupLanguageSelector() {
        binding.appLanguageSpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.language_options,
            android.R.layout.simple_spinner_dropdown_item
        )
        binding.appLanguageSpinner.setSelection(
            LanguageManager.positionFor(LanguageManager.getSavedLanguage(this)),
            false
        )
        binding.appLanguageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val language = LanguageManager.languageFor(position)
                if (language != LanguageManager.getSavedLanguage(this@MainActivity)) {
                    LanguageManager.setLanguage(this@MainActivity, language)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private data class BottomItem(
        val destinationId: Int,
        val iconRes: Int,
        val titleRes: Int
    )

    private val farmerBottomItems = listOf(
        BottomItem(R.id.dashboardFragment, R.drawable.ic_dashboard, R.string.home_nav_title),
        BottomItem(R.id.animalLedgerFragment, R.drawable.ic_ledger, R.string.animals_nav_title),
        BottomItem(R.id.diseaseReportFragment, R.drawable.ic_report, R.string.reports_nav_title),
        BottomItem(R.id.profileFragment, R.drawable.ic_profile, R.string.profile_title)
    )

    private val doctorBottomItems = listOf(
        BottomItem(R.id.doctorDashboardFragment, R.drawable.ic_dashboard, R.string.dashboard_title),
        BottomItem(R.id.doctorReportsFragment, R.drawable.ic_report, R.string.reports_nav_title),
        BottomItem(R.id.doctorPaymentHistoryFragment, R.drawable.ic_payment, R.string.payment_nav_title),
        BottomItem(R.id.doctorProfileFragment, R.drawable.ic_profile, R.string.profile_title)
    )

    private val authDestinations = setOf(
        R.id.loginFragment,
        R.id.forgotPasswordFragment,
        R.id.otpVerificationFragment,
        R.id.resetPasswordFragment
    )
}
