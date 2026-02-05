package ru.tipowk.tunvpn.data

import platform.UIKit.UIPasteboard

/**
 * iOS implementation of ClipboardProvider.
 */
class IosClipboardProvider : ClipboardProvider {

    override fun getText(): String? {
        return UIPasteboard.generalPasteboard.string
    }

    override fun setText(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}
