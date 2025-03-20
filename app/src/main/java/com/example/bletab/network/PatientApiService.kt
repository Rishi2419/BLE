package com.example.bletab.network

import com.example.bletab.model.PatientResponse
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface PatientApiService {
    @FormUrlEncoded
    @POST("987ZkV2inxuw/get_patient_list_history_ipd_opd.php")
    fun getPatientDetails(
        @Field("admin_key") adminKey: String,
        @Field("search_text") searchText: String
    ): Call<PatientResponse>
}