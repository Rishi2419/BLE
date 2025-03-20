package com.example.bletab.model


import com.google.gson.annotations.SerializedName

data class PatientResponse(
    @SerializedName("data")
    val patientList: List<Patient>?
)