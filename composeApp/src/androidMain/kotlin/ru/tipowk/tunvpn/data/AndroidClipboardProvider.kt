package ru.tipowk.tunvpn.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Android implementation of ClipboardProvider.
 */
class AndroidClipboardProvider(
    private val context: Context,
) : ClipboardProvider {

    override fun getText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        return clip.getItemAt(0)?.text?.toString()
    }

    override fun setText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return

        val clip = ClipData.newPlainText("proxy_address", text)
        clipboard.setPrimaryClip(clip)
    }
}
