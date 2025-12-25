package com.example.smartfish

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var etTankHeight: EditText
    private lateinit var etHeaterOn: EditText
    private lateinit var etHeaterOff: EditText
    private lateinit var etPumpOn: EditText
    private lateinit var etPumpOff: EditText
    private lateinit var btnSave: Button
    private lateinit var btnSync: Button

    private lateinit var sessionManager: SessionManager
    private lateinit var sharedPrefs: SharedPreferences

    private val DEVICE_ID = "1c881950-d89a-11f0-a9c3-a94cc0e19399"
    private val PREFS_NAME = "SmartFishSettings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Ánh xạ views
        etTankHeight = findViewById(R.id.etTankHeight)
        etHeaterOn = findViewById(R.id.etHeaterOn)
        etHeaterOff = findViewById(R.id.etHeaterOff)
        etPumpOn = findViewById(R.id.etPumpOn)
        etPumpOff = findViewById(R.id.etPumpOff)
        btnSave = findViewById(R.id.btnSaveSettings)
        btnSync = findViewById(R.id.btnSyncToDevice)

        sessionManager = SessionManager(applicationContext)
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load cài đặt đã lưu
        loadSettings()

        // Xử lý nút Lưu
        btnSave.setOnClickListener {
            saveSettings()
        }

        // Xử lý nút Đồng bộ
        btnSync.setOnClickListener {
            syncToDevice()
        }
    }

    private fun loadSettings() {
        etTankHeight.setText(sharedPrefs.getFloat("tankHeight", 30.0f).toString())
        etHeaterOn.setText(sharedPrefs.getFloat("heaterOn", 28.5f).toString())
        etHeaterOff.setText(sharedPrefs.getFloat("heaterOff", 29.0f).toString())
        etPumpOn.setText(sharedPrefs.getFloat("pumpOn", 10.0f).toString())
        etPumpOff.setText(sharedPrefs.getFloat("pumpOff", 20.0f).toString())
    }

    private fun saveSettings(): Boolean {
        try {
            val tankHeight = etTankHeight.text.toString().toFloatOrNull()
            val heaterOn = etHeaterOn.text.toString().toFloatOrNull()
            val heaterOff = etHeaterOff.text.toString().toFloatOrNull()
            val pumpOn = etPumpOn.text.toString().toFloatOrNull()
            val pumpOff = etPumpOff.text.toString().toFloatOrNull()

            // Kiểm tra giá trị hợp lệ
            if (tankHeight == null || heaterOn == null || heaterOff == null ||
                pumpOn == null || pumpOff == null) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ các giá trị!", Toast.LENGTH_SHORT).show()
                return false
            }

            // Kiểm tra logic ngưỡng
            if (heaterOn >= heaterOff) {
                Toast.makeText(this, "Ngưỡng bật sưởi phải nhỏ hơn ngưỡng tắt!", Toast.LENGTH_SHORT).show()
                return false
            }

            if (pumpOn >= pumpOff) {
                Toast.makeText(this, "Ngưỡng bật bơm phải nhỏ hơn ngưỡng tắt!", Toast.LENGTH_SHORT).show()
                return false
            }

            // Lưu vào SharedPreferences
            sharedPrefs.edit().apply {
                putFloat("tankHeight", tankHeight)
                putFloat("heaterOn", heaterOn)
                putFloat("heaterOff", heaterOff)
                putFloat("pumpOn", pumpOn)
                putFloat("pumpOff", pumpOff)
                apply()
            }

            Toast.makeText(this, "✅ Đã lưu cài đặt!", Toast.LENGTH_SHORT).show()
            return true
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    private fun syncToDevice() {
        // Lưu trước khi đồng bộ
        if (!saveSettings()) return

        val token = sessionManager.fetchAuthToken()
        if (token == null) {
            Toast.makeText(this, "Chưa đăng nhập!", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Đang đồng bộ...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Tạo JSON params chứa tất cả cài đặt
                val params = """
                    {
                        "tankHeight": ${sharedPrefs.getFloat("tankHeight", 30.0f)},
                        "heaterOn": ${sharedPrefs.getFloat("heaterOn", 28.5f)},
                        "heaterOff": ${sharedPrefs.getFloat("heaterOff", 29.0f)},
                        "pumpOn": ${sharedPrefs.getFloat("pumpOn", 10.0f)},
                        "pumpOff": ${sharedPrefs.getFloat("pumpOff", 20.0f)}
                    }
                """.trimIndent()

                val rpcRequest = RpcRequest(
                    method = "setThresholds",
                    params = params
                )

                val response = RetrofitClient.instance.sendRpcRequest(
                    token = "Bearer $token",
                    deviceId = DEVICE_ID,
                    request = rpcRequest
                )

                launch(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SettingsActivity,
                            "✅ Đã đồng bộ lên thiết bị!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SettingsActivity,
                            "❌ Lỗi: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Lỗi đồng bộ: ${e.message}", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity,
                        "❌ Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}