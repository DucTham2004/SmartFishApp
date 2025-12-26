package com.example.smartfish

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    // TextViews hiển thị giá trị
    private lateinit var tvTankHeight: TextView
    private lateinit var tvHeaterOn: TextView
    private lateinit var tvHeaterOff: TextView
    private lateinit var tvPumpOn: TextView
    private lateinit var tvPumpOff: TextView

    // Buttons +/-
    private lateinit var btnTankHeightMinus: Button
    private lateinit var btnTankHeightPlus: Button
    private lateinit var btnHeaterOnMinus: Button
    private lateinit var btnHeaterOnPlus: Button
    private lateinit var btnHeaterOffMinus: Button
    private lateinit var btnHeaterOffPlus: Button
    private lateinit var btnPumpOnMinus: Button
    private lateinit var btnPumpOnPlus: Button
    private lateinit var btnPumpOffMinus: Button
    private lateinit var btnPumpOffPlus: Button

    private lateinit var btnSave: Button
    private lateinit var btnSync: Button

    private lateinit var sessionManager: SessionManager
    private lateinit var sharedPrefs: SharedPreferences

    // Giá trị hiện tại (số nguyên)
    private var tankHeight = 30
    private var heaterOn = 28
    private var heaterOff = 29
    private var pumpOn = 10
    private var pumpOff = 20

    private val DEVICE_ID = "1c881950-d89a-11f0-a9c3-a94cc0e19399"
    private val PREFS_NAME = "SmartFishSettings"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Ánh xạ TextViews
        tvTankHeight = findViewById(R.id.tvTankHeight)
        tvHeaterOn = findViewById(R.id.tvHeaterOn)
        tvHeaterOff = findViewById(R.id.tvHeaterOff)
        tvPumpOn = findViewById(R.id.tvPumpOn)
        tvPumpOff = findViewById(R.id.tvPumpOff)

        // Ánh xạ Buttons +/-
        btnTankHeightMinus = findViewById(R.id.btnTankHeightMinus)
        btnTankHeightPlus = findViewById(R.id.btnTankHeightPlus)
        btnHeaterOnMinus = findViewById(R.id.btnHeaterOnMinus)
        btnHeaterOnPlus = findViewById(R.id.btnHeaterOnPlus)
        btnHeaterOffMinus = findViewById(R.id.btnHeaterOffMinus)
        btnHeaterOffPlus = findViewById(R.id.btnHeaterOffPlus)
        btnPumpOnMinus = findViewById(R.id.btnPumpOnMinus)
        btnPumpOnPlus = findViewById(R.id.btnPumpOnPlus)
        btnPumpOffMinus = findViewById(R.id.btnPumpOffMinus)
        btnPumpOffPlus = findViewById(R.id.btnPumpOffPlus)

        btnSave = findViewById(R.id.btnSaveSettings)
        btnSync = findViewById(R.id.btnSyncToDevice)

        sessionManager = SessionManager(applicationContext)
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load cài đặt đã lưu
        loadSettings()

        // Setup các nút +/-
        setupNumberPickers()

        // Xử lý nút Lưu
        btnSave.setOnClickListener {
            saveSettings()
        }

        // Xử lý nút Đồng bộ
        btnSync.setOnClickListener {
            syncToDevice()
        }
    }

    private fun setupNumberPickers() {
        // Tank Height: 10-100 cm
        btnTankHeightMinus.setOnClickListener {
            if (tankHeight > 10) {
                tankHeight--
                updateDisplay()
            }
        }
        btnTankHeightPlus.setOnClickListener {
            if (tankHeight < 100) {
                tankHeight++
                updateDisplay()
            }
        }

        // Heater On: 15-35°C
        btnHeaterOnMinus.setOnClickListener {
            if (heaterOn > 15) {
                heaterOn--
                updateDisplay()
            }
        }
        btnHeaterOnPlus.setOnClickListener {
            if (heaterOn < 35 && heaterOn < heaterOff - 1) {
                heaterOn++
                updateDisplay()
            }
        }

        // Heater Off: 15-35°C
        btnHeaterOffMinus.setOnClickListener {
            if (heaterOff > 15 && heaterOff > heaterOn + 1) {
                heaterOff--
                updateDisplay()
            }
        }
        btnHeaterOffPlus.setOnClickListener {
            if (heaterOff < 35) {
                heaterOff++
                updateDisplay()
            }
        }

        // Pump On: 5-50 cm
        btnPumpOnMinus.setOnClickListener {
            if (pumpOn > 5) {
                pumpOn--
                updateDisplay()
            }
        }
        btnPumpOnPlus.setOnClickListener {
            if (pumpOn < 50 && pumpOn < pumpOff - 1) {
                pumpOn++
                updateDisplay()
            }
        }

        // Pump Off: 5-50 cm
        btnPumpOffMinus.setOnClickListener {
            if (pumpOff > 5 && pumpOff > pumpOn + 1) {
                pumpOff--
                updateDisplay()
            }
        }
        btnPumpOffPlus.setOnClickListener {
            if (pumpOff < 50) {
                pumpOff++
                updateDisplay()
            }
        }
    }

    private fun updateDisplay() {
        tvTankHeight.text = tankHeight.toString()
        tvHeaterOn.text = heaterOn.toString()
        tvHeaterOff.text = heaterOff.toString()
        tvPumpOn.text = pumpOn.toString()
        tvPumpOff.text = pumpOff.toString()
    }

    private fun loadSettings() {
        // Migration: Đọc giá trị, hỗ trợ cả Float cũ và Int mới
        tankHeight = getIntOrFloat("tankHeight", 30)
        heaterOn = getIntOrFloat("heaterOn", 28)
        heaterOff = getIntOrFloat("heaterOff", 29)
        pumpOn = getIntOrFloat("pumpOn", 10)
        pumpOff = getIntOrFloat("pumpOff", 20)
        updateDisplay()
    }

    // Hàm helper để đọc giá trị, hỗ trợ cả Float cũ và Int mới
    private fun getIntOrFloat(key: String, defaultValue: Int): Int {
        return try {
            sharedPrefs.getInt(key, defaultValue)
        } catch (e: ClassCastException) {
            // Nếu giá trị cũ là Float, chuyển sang Int
            val floatValue = sharedPrefs.getFloat(key, defaultValue.toFloat())
            floatValue.toInt()
        }
    }

    private fun saveSettings(): Boolean {
        try {
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
                putInt("tankHeight", tankHeight)
                putInt("heaterOn", heaterOn)
                putInt("heaterOff", heaterOff)
                putInt("pumpOn", pumpOn)
                putInt("pumpOff", pumpOff)
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
                        "tankHeight": ${sharedPrefs.getInt("tankHeight", 30)},
                        "heaterOn": ${sharedPrefs.getInt("heaterOn", 28)},
                        "heaterOff": ${sharedPrefs.getInt("heaterOff", 29)},
                        "pumpOn": ${sharedPrefs.getInt("pumpOn", 10)},
                        "pumpOff": ${sharedPrefs.getInt("pumpOff", 20)}
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