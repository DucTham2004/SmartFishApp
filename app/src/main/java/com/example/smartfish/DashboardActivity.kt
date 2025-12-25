package com.example.smartfish

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.widget.SeekBar // <-- THÊM IMPORT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.serialization.json.JsonArray
import android.widget.Toast
import com.airbnb.lottie.LottieAnimationView
import kotlin.math.roundToInt


import android.widget.ImageButton
import com.github.dhaval2404.colorpicker.MaterialColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape
import com.github.dhaval2404.colorpicker.listener.ColorListener

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// --- KẾT THÚC THÊM IMPORT ---

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvTemperature: TextView
    private lateinit var tvHumidity: TextView
    private lateinit var tvWaterLevel: TextView
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var dotLottieAnimationView: LottieAnimationView
    private lateinit var btnFeed: ImageButton

    // --- THÊM BIẾN CHO NÚT MÀU ---
    private lateinit var btnColorPicker: ImageButton
    private lateinit var btnChart: ImageButton


    private lateinit var sessionManager: SessionManager
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    // Định nghĩa JSON parser (từ kotlinx.serialization)
    private val json = Json { ignoreUnknownKeys = true }

    private val DEVICE_ID = "1c881950-d89a-11f0-a9c3-a94cc0e19399" // <-- DÁN LẠI DEVICE ID VÀO ĐÂY

    // --- CẤU HÌNH NGƯỠNG CẢNH BÁO ---
    private val TEMP_MAX = 32.0f
    private val TEMP_MIN = 20.0f
    private val WATER_LEVEL_MIN = 10.0f // cm
    private val WATER_LEVEL_MAX = 150.0f // cm

    // --- BIẾN TRÁNH SPAM THÔNG BÁO (COOLDOWN) ---
    private var lastAlertTime = 0L
    private val ALERT_COOLDOWN = 60000L // 60 giây mới báo 1 lần nếu vẫn lỗi
    private val CHANNEL_ID = "SMART_FISH_ALERTS"
    private val NOTIFICATION_ID = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Trong DashboardActivity.kt, thêm vào onCreate():
        val btnSettings: ImageButton = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
        tvTemperature = findViewById(R.id.tvTemperature)
        tvHumidity = findViewById(R.id.tvHumidity)
        tvWaterLevel = findViewById(R.id.tvWaterLevel)
//        swLight = findViewById(R.id.swLight) // <-- ÁNH XẠ SWITCH
        dotLottieAnimationView = findViewById(R.id.ivFishTank)

        // --- ÁNH XẠ NÚT CHỌN MÀU ---
        btnColorPicker = findViewById(R.id.imageButton4) // ID từ file XML
        btnFeed = findViewById(R.id.imageButton5)
        // --- ÁNH XẠ THANH TRƯỢT ĐỘ SÁNG ---
        seekBarBrightness = findViewById(R.id.seekBarBrightness)


        // 1. Tạo kênh thông báo
        createNotificationChannel()

        // 2. Xin quyền thông báo (Android 13+)
        checkNotificationPermission()

        sessionManager = SessionManager(applicationContext)

        // Lấy token đã lưu
        val token = sessionManager.fetchAuthToken()

        if (token == null) {
            // Nếu không có token, quay lại màn hình Login (nên có)
            finish() // Đóng màn hình này
            return
        }

        // Bắt đầu kết nối WebSocket
        startWebSocket(token)

        // --- XỬ LÝ SỰ KIỆN CLICK CHO NÚT CHỌN MÀU ---
        btnColorPicker.setOnClickListener {
            showColorBrightnessPicker(token)
        }
        // --- XỬ LÝ SỰ KIỆN NÚT CHO CÁ ĂN ---
        btnFeed.setOnClickListener {
            // Hiệu ứng Toast báo người dùng biết đã bấm
            Toast.makeText(this, "Đang gửi lệnh cho ăn...", Toast.LENGTH_SHORT).show()
            sendFeedControl(token)
        }
        btnChart = findViewById(R.id.imageButton6)
        btnChart.setOnClickListener {
            val intent = Intent(this, ChartActivity::class.java)
            startActivity(intent)
        }

        // --- XỬ LÝ SỰ KIỆN CHO THANH TRƯỢT ĐỘ SÁNG ---
        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // (Không làm gì khi đang kéo để tránh spam lệnh)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // (Không cần làm gì)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Chỉ gửi lệnh khi người dùng nhả tay
                val brightness = seekBar?.progress ?: 50 // Lấy giá trị (0-255)
                Log.d("DashboardActivity", "Gửi độ sáng: $brightness")
                sendBrightnessControl(token, brightness)
            }
        })

    }

    // --- HÀM MỚI ĐỂ HIỂN THỊ BẢNG CHỌN MÀU VÀ ĐỘ SÁNG ---
    private fun showColorBrightnessPicker(token: String) {
        MaterialColorPickerDialog
            .Builder(this)
            .setTitle("Chọn màu & độ sáng")
            .setColorShape(ColorShape.SQAURE) // Hình dạng ô màu
            .setColorListener(object : ColorListener {
                override fun onColorSelected(color: Int, hexColor: String) {
                    // color là dạng Int, hexColor là dạng chuỗi "#RRGGBB"
                    Log.d("DashboardActivity", "Màu đã chọn: $hexColor")
                    // Gửi màu đã chọn lên ThingsBoard
                    sendColorControl(token, hexColor)
                }
            })
            .show()
    }


    // --- HÀM MỚI ĐỂ GỌI API ---
    private fun sendLightControl(token: String, isChecked: Boolean) {
        // Chuẩn bị body cho request
        val rpcRequest = RpcRequest(
            method = "setLight", // Phải khớp với method trong code ESP32
            params = isChecked.toString()
        )

        // Gọi API bằng Coroutine
        lifecycleScope.launch(Dispatchers.IO) { // Chạy trên thread IO
            try {
                // RetrofitClient chính là object chúng ta đã tạo
                val response = RetrofitClient.instance.sendRpcRequest(
                    token = "Bearer $token", // Phải có "Bearer "
                    deviceId = DEVICE_ID,
                    request = rpcRequest
                )

                if (response.isSuccessful) {
                    Log.d("DashboardActivity", "Gửi lệnh RPC thành công!")
                } else {
                    Log.e("DashboardActivity", "Gửi RPC thất bại: ${response.code()}")
                    // Có thể hiển thị Toast trên Main thread
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@DashboardActivity, "Gửi lệnh thất bại", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardActivity", "Lỗi khi gửi RPC: ${e.message}", e)
                launch(Dispatchers.Main) {
                    Toast.makeText(this@DashboardActivity, "Lỗi kết nối", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendBrightnessControl(token: String, brightness: Int) {
        // brightness là giá trị từ 0-255
        val rpcRequest = RpcRequest(
            method = "setBrightness", // Phải khớp với method trong code ESP32
            params = brightness.toString() // Gửi giá trị dưới dạng chuỗi
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.sendRpcRequest(
                    token = "Bearer $token",
                    deviceId = DEVICE_ID,
                    request = rpcRequest
                )

                if (response.isSuccessful) {
                    Log.d("DashboardActivity", "Gửi lệnh setBrightness ($brightness) thành công!")
                } else {
                    Log.e("DashboardActivity", "Gửi RPC setBrightness thất bại: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("DashboardActivity", "Lỗi khi gửi RPC setBrightness: ${e.message}", e)
            }
        }
    }

    private fun sendColorControl(token: String, hexColor: String) {
        val rpcRequest = RpcRequest(
            method = "setColor",
            params = hexColor // Gửi chuỗi hex, ví dụ "#FF0000"
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.sendRpcRequest(
                    token = "Bearer $token",
                    deviceId = DEVICE_ID,
                    request = rpcRequest
                )

                if (response.isSuccessful) {
                    Log.d("DashboardActivity", "Gửi lệnh setColor ($hexColor) thành công!")
                } else {
                    Log.e("DashboardActivity", "Gửi RPC setColor thất bại: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("DashboardActivity", "Lỗi khi gửi RPC setColor: ${e.message}", e)
            }
        }
    }

    // --- HÀM MỚI: GỬI LỆNH CHO CÁ ĂN ---
    private fun sendFeedControl(token: String) {
        // Tạo request RPC
        val rpcRequest = RpcRequest(
            method = "feedFish", // Tên method phải khớp với code ESP32
            params = "true"      // Giá trị tham số (không quan trọng lắm trong trường hợp này)
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitClient.instance.sendRpcRequest(
                    token = "Bearer $token",
                    deviceId = DEVICE_ID,
                    request = rpcRequest
                )

                if (response.isSuccessful) {
                    Log.d("DashboardActivity", "Gửi lệnh feedFish thành công!")
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@DashboardActivity, "Đã cho cá ăn! 🐟", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("DashboardActivity", "Gửi RPC feedFish thất bại: ${response.code()}")
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@DashboardActivity, "Lỗi kết nối thiết bị", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("DashboardActivity", "Lỗi khi gửi RPC feedFish: ${e.message}", e)
            }
        }
    }

    private fun startWebSocket(token: String) {
        // 1. URL của WebSocket ThingsBoard (dùng 'ws' thay vì 'http')
        // /api/ws/plugins/telemetry?token=TOKEN_CUA_BAN
        val wsUrl = "wss://eu.thingsboard.cloud/api/ws/plugins/telemetry?token=$token"

        // 2. Tạo một Request
        val request = Request.Builder().url(wsUrl).build()

        // 3. Tạo một Listener để xử lý các sự kiện
        val wsListener = object : WebSocketListener() {

            // Được gọi khi kết nối thành công
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("DashboardActivity", "WebSocket đã mở!")

                // 4. Gửi tin nhắn "đăng ký" (subscribe)
                // Chúng ta muốn lắng nghe cả "latest telemetry" (ts)
                val subscriptionMessage = """
                {
                    "tsSubCmds": [
                        {
                            "entityType": "DEVICE",
                            "entityId": "$DEVICE_ID", 
                            "scope": "LATEST_TELEMETRY",
                            "cmdId": 1
                        }
                    ],
                    "historyCmds": [],
                    "attrSubCmds": []
                }
                """.trimIndent()

                // CẢNH BÁO: Bạn cần thay thế "YOUR_DEVICE_ID"
                // Bạn có thể lấy nó từ URL của web ThingsBoard khi xem thiết bị
                // Ví dụ: .../devices/abc-123-xyz-789

                // Tạm thời, chúng ta sẽ để trống Device ID
                // và bạn sẽ cần tự lấy nó
                // HÃY HỎI TÔI CÁCH LẤY NẾU BẠN KHÔNG TÌM THẤY

                webSocket.send(subscriptionMessage)
//                Log.w("DashboardActivity", "Chưa gửi subscription. Bạn cần thêm Device ID.")
            }

            // Được gọi khi có tin nhắn mới (dữ liệu)
//... (các import khác của bạn)

//... bên trong class DashboardActivity

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("DashboardActivity", "Nhận được tin nhắn: $text")

                try {
                    val root = json.parseToJsonElement(text).jsonObject

                    // 1. Kiểm tra lỗi (giống như cũ)
                    if (root.containsKey("errorCode") && root["errorCode"]?.jsonPrimitive?.content != "0") {
                        val errorMsg = root["errorMsg"]?.jsonPrimitive?.content
                        Log.e("DashboardActivity", "Lỗi từ WebSocket Server: $errorMsg")
                        return
                    }

                    // 2. Kiểm tra dữ liệu (data)
                    if (root.containsKey("data")) {
                        val telemetryDataElement = root["data"]

                        if (telemetryDataElement != null && telemetryDataElement is JsonObject) {

                            // === PHẦN ĐÃ SỬA ===

                            // Hàm helper để trích xuất giá trị từ cấu trúc [[ts, "value"]]
                            fun extractValue(data: JsonObject, key: String): String? {
                                try {
                                    // 1. Lấy phần tử (là một mảng)
                                    val elementArray = data[key] as? JsonArray

                                    // 2. Lấy phần tử đầu tiên của mảng đó (là [ts, "val"])
                                    val pairArray = elementArray?.firstOrNull() as? JsonArray

                                    // 3. Lấy phần tử thứ 2 (index 1) của mảng con (chính là "val")
                                    // 4. Lấy nội dung của nó
                                    return pairArray?.get(1)?.jsonPrimitive?.content

                                } catch (e: Exception) {
                                    Log.w("DashboardActivity", "Lỗi nhỏ khi parse key '$key': ${e.message}")
                                    return null
                                }
                            }

                            // Sử dụng hàm helper
                            val temp = extractValue(telemetryDataElement, "nhietDo")?.toFloatOrNull()
                            val humid = extractValue(telemetryDataElement, "doAm")?.toFloatOrNull()
                            val waterLevel = extractValue(telemetryDataElement, "mucNuoc_cm")?.toFloatOrNull() // <-- LẤY DỮ LIỆU MỚI
                            // === KẾT THÚC PHẦN SỬA ===

                            checkAndNotify(temp, waterLevel) // <-- KIỂM TRA VÀ GỬI THÔNG BÁO
                            // 3. Cập nhật UI (giữ nguyên)
                            lifecycleScope.launch(Dispatchers.Main) {
                                temp?.let { tvTemperature.text = "${it.roundToInt()} °C" }
                                humid?.let { tvHumidity.text = "${it.roundToInt()} %" }
                                waterLevel?.let { tvWaterLevel.text = "${it.roundToInt()} cm" } // <-- CẬP NHẬT UI
                            }
                        } else {
                            Log.d("DashboardActivity", "Dữ liệu (data) rỗng, bỏ qua.")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Lỗi parse JSON: ${e.message}", e)
                }
            }

            // Được gọi khi WebSocket bị đóng
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("DashboardActivity", "WebSocket đang đóng: $reason")
                webSocket.close(1000, null)
            }

            // Được gọi khi kết nối thất bại
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("DashboardActivity", "WebSocket thất bại: ${t.message}", t)
            }
        }

        // 7. Bắt đầu kết nối
        webSocket = client.newWebSocket(request, wsListener)
    }

    // Đừng quên đóng WebSocket khi Activity bị hủy
    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "Activity bị hủy")
        client.dispatcher.executorService.shutdown()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Cảnh báo hồ cá"
            val descriptionText = "Thông báo khi nhiệt độ hoặc mực nước bất thường"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            // Đăng ký kênh với hệ thống
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // --- HÀM XIN QUYỀN (ANDROID 13+) ---
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    // --- HÀM KIỂM TRA NGƯỠNG VÀ GỬI THÔNG BÁO ---
    private fun checkAndNotify(temp: Float?, waterLevel: Float?) {
        val currentTime = System.currentTimeMillis()

        // Nếu chưa hết thời gian chờ (60s) thì không làm gì cả để tránh spam
        if (currentTime - lastAlertTime < ALERT_COOLDOWN) return

        var alertMessage = ""

        // Kiểm tra nhiệt độ
        if (temp != null) {
            if (temp > TEMP_MAX) alertMessage += "🔥 Nước quá nóng: $temp°C! "
            else if (temp < TEMP_MIN) alertMessage += "❄️ Nước quá lạnh: $temp°C! "
        }

        // Kiểm tra mực nước
        if (waterLevel != null) {
            if (waterLevel < WATER_LEVEL_MIN) alertMessage += "⚠️ Hết nước: ${waterLevel}cm! "
            else if (waterLevel > WATER_LEVEL_MAX) alertMessage += "🌊 Tràn nước: ${waterLevel}cm! "
        }

        // Nếu có cảnh báo
        if (alertMessage.isNotEmpty()) {
            sendNotification(alertMessage)
            lastAlertTime = currentTime // Cập nhật thời gian đã báo
        }
    }

    // --- HÀM GỬI THÔNG BÁO RA MÀN HÌNH ---
    private fun sendNotification(message: String) {
        // Intent để khi bấm vào thông báo sẽ mở lại Dashboard
        val intent = Intent(this, DashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.applogo) // Đảm bảo icon này tồn tại
            .setContentTitle("Cảnh báo SmartFish! 🐟")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message)) // Để hiện text dài
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
            }
        } catch (e: Exception) {
            Log.e("Notification", "Lỗi gửi thông báo: ${e.message}")
        }
    }
}

// --- HÀM TẠO KÊNH THÔNG BÁO (BẮT BUỘC) ---
