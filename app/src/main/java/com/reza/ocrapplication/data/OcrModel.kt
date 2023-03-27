package com.reza.ocrapplication.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class OcrModel (
    var ocrId: String? = "",
    var ocrText: String? = "",
    var ocrDistance: String? = "",
    var ocrEstimate: String? = "",
)