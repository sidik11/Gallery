package com.sidik.msgallery.security

import android.view.Window
import android.view.WindowManager

object SecureWindow {
    fun protect(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
