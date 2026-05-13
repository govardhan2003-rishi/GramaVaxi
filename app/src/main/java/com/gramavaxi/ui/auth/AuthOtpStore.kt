package com.gramavaxi.ui.auth

object AuthOtpStore {
    const val MODE_FORGOT = "forgot"
    const val MODE_CHANGE = "change"

    var verificationId: String = ""
    var resendToken: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken? = null
    var identifier: String = ""
    var mode: String = MODE_FORGOT
    var currentPassword: String = ""
}
