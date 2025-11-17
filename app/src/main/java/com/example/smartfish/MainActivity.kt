package com.example.smartfish


import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.content.Intent // <-- Thêm import này
class MainActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Tên file layout của bạn
        sessionManager = SessionManager(applicationContext)
//        // --- KIỂM TRA TỰ ĐỘNG ĐĂNG NHẬP ---
//        val savedToken = sessionManager.fetchAuthToken()
//        if (savedToken != null) {
//            // Nếu đã có token, chuyển thẳng đến Dashboard
//            Log.d("MainActivity", "Token đã tồn tại. Tự động đăng nhập.")
//            val intent = Intent(this@MainActivity, DashboardActivity::class.java)
//            startActivity(intent)
//            finish()
//            return // Dừng thực thi onCreate ở đây
//        }
        // -------------------------------------
        // Ánh xạ View

        etUsername = findViewById(R.id.edtEmail) // <-- Thay ID
        etPassword = findViewById(R.id.edtPassword) // <-- Thay ID
        btnLogin = findViewById(R.id.btnLogin)       // <-- Thay ID

        // Xử lý sự kiện click
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                // Gọi hàm đăng nhập bằng Coroutine
                performLogin(username, password)
            } else {
                Toast.makeText(this, " email và mật khẩu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogin(username: String, password: String) {
        // Khởi chạy Coroutine trên Main thread
        lifecycleScope.launch {
            try {
                val request = LoginRequest(username, password)

                // Gọi API (từ file RetrofitClient)
                val response = RetrofitClient.instance.login(request)

                if (response.isSuccessful) {
                    // Đăng nhập THÀNH CÔNG
                    val loginResponse = response.body()
                    val token = loginResponse?.token

                    if (token != null) {
                        // 1. LƯU TOKEN
                        sessionManager.saveAuthToken(token)

                        Log.d("MainActivity", "Đăng nhập thành công! Token đã được lưu.")
                        Toast.makeText(this@MainActivity, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()

                        // 2. CHUYỂN SANG MÀN HÌNH MỚI
                        val intent = Intent(this@MainActivity, DashboardActivity::class.java)
                        startActivity(intent)

                        // 3. ĐÓNG MÀN HÌNH LOGIN (để người dùng không back lại được)
                        finish()

                    } else {
                        Log.e("MainActivity", "Token rỗng dù đăng nhập thành công!")
                        Toast.makeText(this@MainActivity, "Lỗi: Token rỗng", Toast.LENGTH_SHORT).show()
                    }
                    // ===============================================
                    // BƯỚC TIẾP THEO: Lưu token này lại (dùng SharedPreferences)
                    // và chuyển sang màn hình Dashboard (sẽ làm sau)
                    // ===============================================

                } else {
                    // Đăng nhập THẤT BẠI (sai pass, sai user)
                    Log.e("MainActivity", "Đăng nhập thất bại: ${response.code()} ${response.message()}")
                    Toast.makeText(this@MainActivity, "Sai email hoặc mật khẩu!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                // Lỗi mạng hoặc lỗi không xác định
                Log.e("MainActivity", "Lỗi: ${e.message}", e)
                Toast.makeText(this@MainActivity, "Lỗi kết nối: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}