package com.example.btreceivecall.service

/** Single source of truth for packages whose call UI/notifications we support. */
internal object CallAppRegistry {
    const val OWN_PACKAGE = "com.example.btreceivecall"

    val callPackages = setOf(
        "com.google.android.dialer", "com.samsung.android.incallui",
        "com.samsung.android.dialer", "com.android.dialer", "com.android.incallui",
        "com.miui.incallui", "com.coloros.incallui", "com.vivo.incallui",
        "com.android.systemui", "com.android.phone",
        "com.whatsapp", "com.whatsapp.w4b",
        "com.facebook.orca", "com.facebook.katana", "com.facebook.mlite",
        "org.telegram.messenger", "org.telegram.messenger.web",
        "com.viber.voip",
        "com.imo.android.imoim", "com.imo.android.imoimhd",
        "com.imo.android.imoimbeta", "com.imo.android.imoimlite"
    )

    val systemDialerPackages = setOf(
        "com.google.android.dialer", "com.samsung.android.incallui",
        "com.samsung.android.dialer", "com.android.dialer", "com.android.incallui",
        "com.miui.incallui", "com.coloros.incallui", "com.vivo.incallui", "com.android.phone"
    )
}
