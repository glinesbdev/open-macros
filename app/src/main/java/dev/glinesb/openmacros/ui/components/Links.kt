package dev.glinesb.openmacros.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent

/** Opens a web link in a Custom Tab, falling back to a plain ACTION_VIEW, then to a toast. */
fun openLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e2: ActivityNotFoundException) {
            Toast.makeText(context, "No browser found to open $url", Toast.LENGTH_LONG).show()
        }
    }
}

/** Where users get API keys. Kept in one place so the app and README stay in sync. */
object KeyLinks {
    const val USDA_SIGNUP = "https://fdc.nal.usda.gov/api-key-signup"
    const val USDA_DOCS = "https://fdc.nal.usda.gov/api-guide"
    const val XAI_SIGNUP = "https://console.x.ai"
    const val XAI_API_KEYS = "https://console.x.ai/team/default/api-keys"
    const val XAI_DOCS = "https://docs.x.ai/developers/quickstart"
}
