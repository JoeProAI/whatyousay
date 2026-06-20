package ai.whatyousay.data

import ai.whatyousay.core.Languages
import ai.whatyousay.engine.DeviceTier
import android.content.Context

/**
 * Small persistent preferences store. Keeps the onboarding result (which tier and
 * languages the user chose, whether onboarding ran) and the last conversation pair
 * out of the composables and the ViewModels' transient state. Backed by
 * SharedPreferences; no network, nothing leaves the device.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var tier: DeviceTier
        get() = prefs.getString(KEY_TIER, null)?.let { runCatching { DeviceTier.valueOf(it) }.getOrNull() } ?: DeviceTier.MID
        set(value) = prefs.edit().putString(KEY_TIER, value.name).apply()

    /** ISO codes the user wants available, defaulting to the full app language list. */
    var languageCodes: Set<String>
        get() = prefs.getStringSet(KEY_LANGS, null)?.takeIf { it.isNotEmpty() }
            ?: Languages.all.map { it.code }.toSet()
        set(value) = prefs.edit().putStringSet(KEY_LANGS, value).apply()

    var sourceCode: String
        get() = prefs.getString(KEY_SOURCE, Languages.EN.code) ?: Languages.EN.code
        set(value) = prefs.edit().putString(KEY_SOURCE, value).apply()

    var targetCode: String
        get() = prefs.getString(KEY_TARGET, Languages.ES.code) ?: Languages.ES.code
        set(value) = prefs.edit().putString(KEY_TARGET, value).apply()

    private companion object {
        const val FILE = "whatyousay.settings"
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_TIER = "device_tier"
        const val KEY_LANGS = "language_codes"
        const val KEY_SOURCE = "source_lang"
        const val KEY_TARGET = "target_lang"
    }
}
