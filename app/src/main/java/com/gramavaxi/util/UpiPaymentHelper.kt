package com.gramavaxi.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

object UpiPaymentHelper {
    private const val TAG = "UpiPaymentHelper"
    private const val CURRENCY = "INR"
    private const val MAX_NOTE_LENGTH = 80
    private val upiIdRegex = Regex("^[a-zA-Z0-9._-]{2,256}@[a-zA-Z][a-zA-Z0-9._-]{2,64}$")
    private val unsafeTextRegex = Regex("[\\r\\n\\t]")

    private val preferredUpiPackages = listOf(
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp",
        "in.amazon.mShop.android.shopping"
    )

    data class PaymentRequest(
        val payeeUpiId: String,
        val payeeName: String,
        val amount: BigDecimal,
        val note: String
    )

    data class UpiApp(
        val packageName: String,
        val label: String
    )

    data class ParsedResponse(
        val status: Status,
        val transactionId: String?,
        val approvalRefNo: String?,
        val responseCode: String?,
        val rawResponse: String,
        val fields: Map<String, String>
    )

    enum class Status {
        SUCCESS,
        SUBMITTED,
        FAILED,
        CANCELLED,
        UNKNOWN
    }

    fun buildPaymentRequest(
        payeeUpiId: String,
        payeeName: String,
        amount: Int,
        note: String
    ): Result<PaymentRequest> {
        val sanitizedUpi = sanitizeUpiId(payeeUpiId)
        if (!isValidUpiId(sanitizedUpi)) {
            return Result.failure(IllegalArgumentException("Invalid UPI ID"))
        }

        val sanitizedAmount = sanitizeAmount(amount)
            ?: return Result.failure(IllegalArgumentException("Invalid amount"))

        val sanitizedName = sanitizeText(payeeName, maxLength = 60)
        if (sanitizedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid payee name"))
        }

        val sanitizedNote = sanitizeText(note, maxLength = MAX_NOTE_LENGTH)
            .ifBlank { "Grama Vaxi consultation" }

        return Result.success(
            PaymentRequest(
                payeeUpiId = sanitizedUpi,
                payeeName = sanitizedName,
                amount = sanitizedAmount,
                note = sanitizedNote
            )
        )
    }

    fun createPaymentUri(request: PaymentRequest): Uri {
        val amount = request.amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        val uriString = buildString {
            append("upi://pay")
            append("?pa=").append(Uri.encode(request.payeeUpiId))
            append("&pn=").append(Uri.encode(request.payeeName))
            append("&am=").append(Uri.encode(amount))
            append("&tn=").append(Uri.encode(request.note))
            append("&cu=").append(Uri.encode(CURRENCY))
        }
        val uri = Uri.parse(uriString)
        Log.d(TAG, "Generated UPI URI without sensitive payer data: $uri")
        return uri
    }

    fun createChooserIntent(context: Context, request: PaymentRequest, chooserTitle: String): Intent? {
        val uri = createPaymentUri(request)
        val baseIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        val explicitIntents = queryUpiIntents(context.packageManager, baseIntent)
        if (explicitIntents.isEmpty()) {
            Log.w(TAG, "No installed app can handle upi://pay")
            return null
        }

        val primary = explicitIntents.first()
        val alternatives = explicitIntents.drop(1).toTypedArray()
        return Intent.createChooser(primary, chooserTitle)
            .putExtra(Intent.EXTRA_INITIAL_INTENTS, alternatives)
    }

    fun installedUpiApps(context: Context): List<UpiApp> {
        val baseIntent = Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return queryUpiResolveInfo(context.packageManager, baseIntent)
            .map { resolveInfo ->
                UpiApp(
                    packageName = resolveInfo.activityInfo.packageName,
                    label = resolveInfo.loadLabel(context.packageManager).toString()
                )
            }
            .distinctBy { it.packageName }
    }

    fun parseResponse(resultCode: Int, data: Intent?): ParsedResponse {
        val rawResponse = data?.getStringExtra("response").orEmpty()
        val fields = rawResponse.split("&")
            .mapNotNull { part ->
                val keyValue = part.split("=", limit = 2)
                if (keyValue.size == 2) {
                    keyValue[0].trim().lowercase(Locale.US) to Uri.decode(keyValue[1]).trim()
                } else {
                    null
                }
            }
            .toMap()

        val statusText = fields["status"].orEmpty()
        val responseCode = fields["responsecode"] ?: fields["responseCode".lowercase(Locale.US)]
        val transactionId = fields["txnid"] ?: fields["txnref"] ?: fields["transactionid"]
        val approvalRefNo = fields["approvalrefno"] ?: fields["approvalref"] ?: fields["rrn"]
        val parsedStatus = when {
            resultCode == Activity.RESULT_CANCELED -> Status.CANCELLED
            statusText.equals("success", ignoreCase = true) -> Status.SUCCESS
            statusText.equals("submitted", ignoreCase = true) || statusText.equals("pending", ignoreCase = true) -> Status.SUBMITTED
            statusText.equals("failure", ignoreCase = true) || statusText.equals("failed", ignoreCase = true) -> Status.FAILED
            rawResponse.isBlank() -> Status.UNKNOWN
            else -> Status.FAILED
        }

        Log.d(
            TAG,
            "UPI result parsed: status=$parsedStatus, txnPresent=${!transactionId.isNullOrBlank()}, responseCode=$responseCode"
        )
        return ParsedResponse(
            status = parsedStatus,
            transactionId = transactionId?.ifBlank { null },
            approvalRefNo = approvalRefNo?.ifBlank { null },
            responseCode = responseCode?.ifBlank { null },
            rawResponse = rawResponse,
            fields = fields
        )
    }

    fun isValidUpiId(upiId: String): Boolean = upiIdRegex.matches(sanitizeUpiId(upiId))

    private fun sanitizeUpiId(upiId: String): String {
        return upiId.trim()
            .replace(unsafeTextRegex, "")
            .lowercase(Locale.US)
    }

    private fun sanitizeAmount(amount: Int): BigDecimal? {
        if (amount <= 0) return null
        return BigDecimal(amount).setScale(2, RoundingMode.HALF_UP)
    }

    private fun sanitizeText(value: String, maxLength: Int): String {
        return value.trim()
            .replace(unsafeTextRegex, " ")
            .replace(Regex("\\s+"), " ")
            .take(maxLength)
    }

    private fun queryUpiIntents(packageManager: PackageManager, baseIntent: Intent): List<Intent> {
        val resolvedPackageNames = queryUpiResolveInfo(packageManager, baseIntent)
            .map { it.activityInfo.packageName }
            .distinct()

        val packageNames = (preferredUpiPackages.filter { it in resolvedPackageNames } +
            resolvedPackageNames.filter { it !in preferredUpiPackages })
            .distinct()

        Log.d(TAG, "Detected UPI packages: $packageNames")
        return packageNames.map { packageName ->
            Intent(baseIntent).setPackage(packageName)
        }
    }

    private fun queryUpiResolveInfo(packageManager: PackageManager, baseIntent: Intent): List<ResolveInfo> {
        val resolved = queryIntentActivities(packageManager, baseIntent)
        val explicitPreferred = preferredUpiPackages.flatMap { packageName ->
            queryIntentActivities(packageManager, Intent(baseIntent).setPackage(packageName))
        }
        return (explicitPreferred + resolved)
            .filter { it.activityInfo?.packageName != null }
            .distinctBy { it.activityInfo.packageName }
    }

    @Suppress("DEPRECATION")
    private fun queryIntentActivities(packageManager: PackageManager, intent: Intent): List<ResolveInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }
}
