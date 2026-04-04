package com.reqlab.ui.shared.i18n

actual fun loadTranslationBundle(languageCode: String): String? =
    GeneratedTranslationBundles.forCode(languageCode)
