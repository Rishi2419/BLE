package com.example.bletab.model

import com.google.gson.annotations.SerializedName

data class Patient(
    @SerializedName("hospital_id")
    val hospitalId: String,

    @SerializedName("patient_id")
    val patientId: String,

    @SerializedName("ipd_number")
    val ipdNumber: String,

    @SerializedName("opd_number")
    val opdNumber: String,

    @SerializedName("patient_name")
    val patientName: String,

    @SerializedName("contact_no")
    val contactNumber: String,

    @SerializedName("email")
    val email: String,

    @SerializedName("gender")
    val gender: String,

    @SerializedName("patient_age")
    val age: String,

    @SerializedName("patient_dob")
    val dob: String,

    @SerializedName("address")
    val address: String
)