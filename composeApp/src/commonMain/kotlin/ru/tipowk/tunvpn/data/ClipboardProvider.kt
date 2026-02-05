package ru.tipowk.tunvpn.data

/**
 * Platform-specific clipboard access.
 */
interface ClipboardProvider {
    /**
     * Get text from the system clipboard.
     * @return Clipboard text, or null if empty or unavailable
     */
    fun getText(): String?

    /**
     * Set text to the system clipboard.
     * @param text Text to copy to clipboard
     */
    fun setText(text: String)
}
