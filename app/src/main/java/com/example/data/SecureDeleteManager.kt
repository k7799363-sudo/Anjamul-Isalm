package com.example.data

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed class AuthResult {
    object Success : AuthResult()
    object Cancelled : AuthResult()
    data class Failed(val message: String) : AuthResult()
}

class SecureDeleteManager(private val context: Context) {

    companion object {
        private const val TAG = "SecureDeleteManager"

        /**
         * Checks whether the device has a secure authentication mechanism configured
         * (Biometric fingerprint/face OR System Device Credential: PIN, Pattern, Password).
         * Returns false if the device has no lock screen or biometrics set up at all.
         */
        fun isSecureAuthAvailable(context: Context): Boolean {
            val biometricManager = BiometricManager.from(context)
            val authenticators = Authenticators.BIOMETRIC_STRONG or
                    Authenticators.BIOMETRIC_WEAK or
                    Authenticators.DEVICE_CREDENTIAL

            val canAuth = biometricManager.canAuthenticate(authenticators)
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                return true
            }

            // Fallback check via KeyguardManager for device credential setup
            try {
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (keyguardManager != null && keyguardManager.isDeviceSecure) {
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "KeyguardManager check exception: ${e.message}")
            }

            return false
        }
    }

    /**
     * Prompts the operating system's native Biometric / Device Credential UI.
     * Delegates 100% of the authentication handling to Android OS.
     * Never stores, accesses, or processes fingerprint, face, PIN, or password data.
     */
    fun promptSystemAuthentication(
        activity: FragmentActivity,
        onResult: (AuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.i(TAG, "System authentication succeeded.")
                onResult(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "System authentication error ($errorCode): $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED -> {
                        onResult(AuthResult.Cancelled)
                    }
                    else -> {
                        val msg = errString.toString().ifBlank { "Authentication failed." }
                        onResult(AuthResult.Failed(msg))
                    }
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "Biometric authentication attempt failed (OS will retry).")
            }
        }

        try {
            val biometricPrompt = BiometricPrompt(activity, executor, callback)

            val authenticators = Authenticators.BIOMETRIC_STRONG or
                    Authenticators.BIOMETRIC_WEAK or
                    Authenticators.DEVICE_CREDENTIAL

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verify your identity")
                .setSubtitle("Authenticate to permanently delete this song from your device.")
                .setAllowedAuthenticators(authenticators)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch BiometricPrompt: ${e.message}", e)
            onResult(AuthResult.Failed("Unable to start system authentication: ${e.message}"))
        }
    }
}
