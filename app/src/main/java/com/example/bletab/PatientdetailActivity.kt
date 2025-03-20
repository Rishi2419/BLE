package com.example.bletab

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.bletab.model.Patient
import com.example.bletab.network.PatientApiClient
import com.example.bletab.network.PatientApiService
import com.example.bletab.model.PatientResponse
import retrofit2.Call
import retrofit2.Callback
import com.example.bletab.databinding.ActivityPatientDetailBinding
import retrofit2.Response

class PatientdetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatientDetailBinding
    private val TAG = "PatientdetailActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPatientDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get device address from intent (in case you need to connect to BLE again)
        val deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")

        // Retrieve admin key and search text from SharedPreferences
        val sharedPreferences = getSharedPreferences("BLEData", MODE_PRIVATE)
        val adminKey = sharedPreferences.getString("ADMIN_KEY", null)
        val searchText = sharedPreferences.getString("SEARCH_TEXT", null)

//        val adminKey = "5"
//        val searchText = "MONIKA TUSHAR JADHAV"

        // Show loading indicator
        binding.progressBar.visibility = View.VISIBLE
        binding.dataContainer.visibility = View.GONE

        // Immediately fetch data if admin key and search text are available
        if (!adminKey.isNullOrEmpty() && !searchText.isNullOrEmpty()) {
            fetchPatientData(adminKey, searchText)
        } else {
            binding.progressBar.visibility = View.GONE
            Toast.makeText(this, "Unable to retrieve patient data. Missing admin key or search text.", Toast.LENGTH_LONG).show()
        }
    }

    private fun fetchPatientData(adminKey: String, searchText: String) {
        val apiService = PatientApiClient.getClient().create(PatientApiService::class.java)

        val call = apiService.getPatientDetails(adminKey, searchText)
        call.enqueue(object : Callback<PatientResponse> {
            override fun onResponse(call: Call<PatientResponse>, response: Response<PatientResponse>) {
                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val patientResponse = response.body()
                    if (patientResponse?.patientList != null && patientResponse.patientList!!.isNotEmpty()) {
                        val patient = patientResponse.patientList!![0]
                        updateUI(patient)
                        binding.dataContainer.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this@PatientdetailActivity,
                            "No patient data found for the provided information",
                            Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "Error: ${response.code()}")
                    Toast.makeText(this@PatientdetailActivity,
                        "Failed to fetch patient data: ${response.message()}",
                        Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<PatientResponse>, t: Throwable) {
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "API call failed: ${t.message}")
                Toast.makeText(this@PatientdetailActivity,
                    "Network error: ${t.message}",
                    Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun updateUI(patient: Patient) {
        with(binding) {
            tvHospitalId.text = patient.hospitalId
            tvPatientId.text = patient.patientId
            tvIpdNumber.text = patient.ipdNumber
            tvOpdNumber.text = patient.opdNumber
            tvPatientName.text = patient.patientName
            tvContactNumber.text = patient.contactNumber
            tvEmail.text = patient.email
            tvGender.text = patient.gender
            tvAge.text = patient.age
            tvDob.text = patient.dob
            tvAddress.text = patient.address
        }
    }
}


