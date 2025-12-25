package com.example.smartfish

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.*

class ChartActivity : AppCompatActivity() {

    private lateinit var lineChartTemp: LineChart
    private lateinit var lineChartHumid: LineChart
    private lateinit var sessionManager: SessionManager
    private lateinit var rgTimePeriod: RadioGroup
    private lateinit var tvDataStatus: TextView
    private lateinit var tvTempAvg: TextView
    private lateinit var tvHumidAvg: TextView

    // Dùng lại DEVICE_ID giống trong DashboardActivity
    private val DEVICE_ID = "1c881950-d89a-11f0-a9c3-a94cc0e19399"

    // Enum để quản lý khoảng thời gian
    private enum class TimePeriod(val days: Int, val label: String) {
        DAY(1, "24 giờ qua"),
        WEEK(7, "7 ngày qua"),
        MONTH(30, "30 ngày qua")
    }

    private var currentPeriod = TimePeriod.DAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        lineChartTemp = findViewById(R.id.lineChartTemp)
        lineChartHumid = findViewById(R.id.lineChartHumid)
        rgTimePeriod = findViewById(R.id.rgTimePeriod)
        tvDataStatus = findViewById(R.id.tvDataStatus)
        tvTempAvg = findViewById(R.id.tvTempAvg)
        tvHumidAvg = findViewById(R.id.tvHumidAvg)
        findViewById<Button>(R.id.btnBack).setOnClickListener { finish() }

        sessionManager = SessionManager(applicationContext)
        val token = sessionManager.fetchAuthToken()

        if (token != null) {
            setupCharts()
            loadHistoryData(token, currentPeriod)

            // Lắng nghe sự kiện thay đổi khoảng thời gian
            rgTimePeriod.setOnCheckedChangeListener { _, checkedId ->
                currentPeriod = when (checkedId) {
                    R.id.rbDay -> TimePeriod.DAY
                    R.id.rbWeek -> TimePeriod.WEEK
                    R.id.rbMonth -> TimePeriod.MONTH
                    else -> TimePeriod.DAY
                }
                // Cấu hình lại biểu đồ với khoảng thời gian mới
                setupCharts()
                loadHistoryData(token, currentPeriod)
            }
        }
    }

    private fun setupCharts() {
        // Cấu hình cho biểu đồ nhiệt độ
        setupChart(lineChartTemp, "Nhiệt độ (°C)", Color.RED)

        // Cấu hình cho biểu đồ độ ẩm
        setupChart(lineChartHumid, "Độ ẩm (%)", Color.BLUE)
    }

    private fun setupChart(chart: LineChart, description: String, color: Int) {
        chart.description.isEnabled = false // Tắt description vì đã có title riêng
        chart.setTouchEnabled(true)
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setPinchZoom(true)
        chart.setDrawGridBackground(false)
        chart.animateXY(1000, 1000) // Animation khi load

        // Format trục X hiển thị thời gian
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textColor = color
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = 0x30000000 // Grid màu xám nhạt
        xAxis.gridLineWidth = 0.5f
        xAxis.granularity = 1f
        xAxis.labelRotationAngle = -45f // Xoay nhãn để dễ đọc hơn

        // Cấu hình số lượng nhãn hiển thị theo khoảng thời gian
        when (currentPeriod) {
            TimePeriod.DAY -> {
                xAxis.setLabelCount(6, false) // Hiển thị ~6 nhãn cho 24h
            }
            TimePeriod.WEEK -> {
                xAxis.setLabelCount(7, false) // Hiển thị ~7 nhãn cho 7 ngày
            }
            TimePeriod.MONTH -> {
                xAxis.setLabelCount(10, false) // Hiển thị ~10 nhãn cho 30 ngày
            }
        }

        xAxis.valueFormatter = object : ValueFormatter() {
            private val dayFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            private val weekFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            private val monthFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

            override fun getFormattedValue(value: Float): String {
                try {
                    val date = Date(value.toLong())
                    return when (currentPeriod) {
                        TimePeriod.DAY -> dayFormat.format(date)
                        TimePeriod.WEEK -> weekFormat.format(date)
                        TimePeriod.MONTH -> monthFormat.format(date)
                    }
                } catch (e: Exception) {
                    Log.e("ChartActivity", "Error formatting date: ${e.message}")
                    return ""
                }
            }
        }

        // Cấu hình trục Y
        val leftAxis = chart.axisLeft
        leftAxis.textColor = color
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = 0x30000000
        leftAxis.gridLineWidth = 0.5f
        leftAxis.granularity = 0.5f // Bước nhảy cho trục Y

        val rightAxis = chart.axisRight
        rightAxis.isEnabled = false

        // Refresh chart legend
        chart.legend.isEnabled = true
        chart.legend.textSize = 12f
        chart.legend.textColor = color
    }

    private fun loadHistoryData(token: String, period: TimePeriod) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Lấy thời gian hiện tại và thời gian trước đó theo khoảng được chọn
                val endTs = System.currentTimeMillis()
                val startTs = endTs - (period.days * 24 * 60 * 60 * 1000L)

                Log.d("ChartActivity", "Loading data for ${period.label}: $startTs to $endTs")

                // Gọi API
                val response = RetrofitClient.instance.getTimeseries(
                    token = "Bearer $token",
                    deviceId = DEVICE_ID,
                    keys = "nhietDo,doAm",
                    startTs = startTs,
                    endTs = endTs
                )

                if (response.isSuccessful && response.body() != null) {
                    val jsonObject = response.body()!!

                    // Xử lý dữ liệu trả về để đưa vào biểu đồ
                    val tempEntries = ArrayList<Entry>()
                    val humidEntries = ArrayList<Entry>()

                    // Parse JSON "nhietDo"
                    if (jsonObject.containsKey("nhietDo")) {
                        val tempArray = jsonObject["nhietDo"]?.jsonArray
                        tempArray?.forEach { element ->
                            val item = element.jsonObject
                            val ts = item["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                            val value = item["value"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                            tempEntries.add(Entry(ts.toFloat(), value))
                        }
                        // Log dữ liệu nhiệt độ
                        if (tempEntries.isNotEmpty()) {
                            val firstDate = Date(tempEntries.first().x.toLong())
                            val lastDate = Date(tempEntries.last().x.toLong())
                            Log.d("ChartActivity", "Temp data range: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(firstDate)} -> ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(lastDate)}")
                        }
                    }

                    // Parse JSON "doAm"
                    if (jsonObject.containsKey("doAm")) {
                        val humidArray = jsonObject["doAm"]?.jsonArray
                        humidArray?.forEach { element ->
                            val item = element.jsonObject
                            val ts = item["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                            val value = item["value"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                            humidEntries.add(Entry(ts.toFloat(), value))
                        }
                        // Log dữ liệu độ ẩm
                        if (humidEntries.isNotEmpty()) {
                            val firstDate = Date(humidEntries.first().x.toLong())
                            val lastDate = Date(humidEntries.last().x.toLong())
                            Log.d("ChartActivity", "Humid data range: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(firstDate)} -> ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(lastDate)}")
                        }
                    }

                    // Sắp xếp lại theo thời gian (quan trọng cho biểu đồ)
                    tempEntries.sortBy { it.x }
                    humidEntries.sortBy { it.x }

                    Log.d("ChartActivity", "Temp entries: ${tempEntries.size}, Humid entries: ${humidEntries.size}")

                    // Cập nhật UI
                    withContext(Dispatchers.Main) {
                        updateCharts(tempEntries, humidEntries)
                    }
                } else {
                    Log.e("ChartActivity", "Lỗi lấy dữ liệu: ${response.code()}")
                    withContext(Dispatchers.Main) {
                        // Hiển thị thông báo lỗi nếu cần
                    }
                }

            } catch (e: Exception) {
                Log.e("ChartActivity", "Exception: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // Hiển thị thông báo lỗi nếu cần
                }
            }
        }
    }

    private fun updateCharts(tempEntries: List<Entry>, humidEntries: List<Entry>) {
        if (tempEntries.isEmpty() && humidEntries.isEmpty()) {
            Log.w("ChartActivity", "No data available for the selected period")
            tvDataStatus.text = "⚠️ Không có dữ liệu cho khoảng thời gian này"
            tvDataStatus.visibility = android.view.View.VISIBLE
            tvTempAvg.text = "--°C"
            tvHumidAvg.text = "--%"
            return
        }

        // Tính giá trị trung bình
        if (tempEntries.isNotEmpty()) {
            val avgTemp = tempEntries.map { it.y }.average()
            tvTempAvg.text = String.format("%.1f°C", avgTemp)
        }

        if (humidEntries.isNotEmpty()) {
            val avgHumid = humidEntries.map { it.y }.average()
            tvHumidAvg.text = String.format("%.1f%%", avgHumid)
        }

        // Phát hiện phạm vi thời gian thực tế của dữ liệu
        val allEntries = tempEntries + humidEntries
        if (allEntries.isNotEmpty()) {
            val minTime = allEntries.minOf { it.x.toLong() }
            val maxTime = allEntries.maxOf { it.x.toLong() }
            val timeRangeDays = (maxTime - minTime) / (24 * 60 * 60 * 1000L)
            val timeRangeHours = (maxTime - minTime) / (60 * 60 * 1000L)
            val timeRangeMinutes = (maxTime - minTime) / (60 * 1000L)

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val startDateStr = sdf.format(Date(minTime))
            val endDateStr = sdf.format(Date(maxTime))

            Log.d("ChartActivity", "Data time range: ${timeRangeDays} days, ${timeRangeHours} hours, ${timeRangeMinutes} minutes")

            // Hiển thị thông báo nếu dữ liệu không đủ cho khoảng thời gian được chọn
            when {
                timeRangeHours < 1 -> {
                    tvDataStatus.text = "ℹ️ Dữ liệu chỉ có ${timeRangeMinutes} phút (${startDateStr} - ${endDateStr})"
                    tvDataStatus.visibility = android.view.View.VISIBLE
                }
                currentPeriod == TimePeriod.MONTH && timeRangeDays < 7 -> {
                    tvDataStatus.text = "ℹ️ Dữ liệu chỉ có ${timeRangeDays} ngày (${startDateStr} - ${endDateStr})"
                    tvDataStatus.visibility = android.view.View.VISIBLE
                }
                currentPeriod == TimePeriod.WEEK && timeRangeDays < 2 -> {
                    tvDataStatus.text = "ℹ️ Dữ liệu chỉ có ${timeRangeDays} ngày (${startDateStr} - ${endDateStr})"
                    tvDataStatus.visibility = android.view.View.VISIBLE
                }
                currentPeriod == TimePeriod.DAY && timeRangeHours < 12 -> {
                    tvDataStatus.text = "ℹ️ Dữ liệu chỉ có ${timeRangeHours} giờ (${startDateStr} - ${endDateStr})"
                    tvDataStatus.visibility = android.view.View.VISIBLE
                }
                else -> {
                    tvDataStatus.visibility = android.view.View.GONE
                }
            }

            // Cấu hình lại trục X với formatter phù hợp với phạm vi dữ liệu thực tế
            configureXAxisForDataRange(timeRangeHours, minTime, maxTime)
        }

        // Cập nhật biểu đồ nhiệt độ với gradient fill
        if (tempEntries.isNotEmpty()) {
            val tempDataSet = LineDataSet(tempEntries, "Nhiệt độ (°C)")
            tempDataSet.color = Color.parseColor("#D32F2F")
            tempDataSet.setCircleColor(Color.parseColor("#D32F2F"))
            tempDataSet.lineWidth = 3f
            tempDataSet.circleRadius = 4f
            tempDataSet.setCircleHoleColor(Color.WHITE)
            tempDataSet.circleHoleRadius = 2f
            tempDataSet.valueTextSize = 9f
            tempDataSet.setDrawValues(false)
            tempDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            tempDataSet.cubicIntensity = 0.2f

            // Gradient fill dưới đường
            tempDataSet.setDrawFilled(true)
            tempDataSet.fillColor = Color.parseColor("#FFCDD2")
            tempDataSet.fillAlpha = 100

            val tempLineData = LineData(tempDataSet)
            lineChartTemp.data = tempLineData
            lineChartTemp.invalidate()
        }

        // Cập nhật biểu đồ độ ẩm với gradient fill
        if (humidEntries.isNotEmpty()) {
            val humidDataSet = LineDataSet(humidEntries, "Độ ẩm (%)")
            humidDataSet.color = Color.parseColor("#1976D2")
            humidDataSet.setCircleColor(Color.parseColor("#1976D2"))
            humidDataSet.lineWidth = 3f
            humidDataSet.circleRadius = 4f
            humidDataSet.setCircleHoleColor(Color.WHITE)
            humidDataSet.circleHoleRadius = 2f
            humidDataSet.valueTextSize = 9f
            humidDataSet.setDrawValues(false)
            humidDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
            humidDataSet.cubicIntensity = 0.2f

            // Gradient fill dưới đường
            humidDataSet.setDrawFilled(true)
            humidDataSet.fillColor = Color.parseColor("#BBDEFB")
            humidDataSet.fillAlpha = 100

            val humidLineData = LineData(humidDataSet)
            lineChartHumid.data = humidLineData
            lineChartHumid.invalidate()
        }
    }

    private fun configureXAxisForDataRange(timeRangeHours: Long, minTime: Long, maxTime: Long) {
        val xAxisTemp = lineChartTemp.xAxis
        val xAxisHumid = lineChartHumid.xAxis

        // Chọn formatter dựa trên phạm vi thời gian thực tế
        val formatter = object : ValueFormatter() {
            private val format = when {
                timeRangeHours < 2 -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // Dưới 2 giờ: hiện giây
                timeRangeHours < 48 -> SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) // Dưới 2 ngày: hiện ngày và giờ
                else -> SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) // Trên 2 ngày: chỉ hiện ngày
            }

            override fun getFormattedValue(value: Float): String {
                return try {
                    format.format(Date(value.toLong()))
                } catch (e: Exception) {
                    ""
                }
            }
        }

        xAxisTemp.valueFormatter = formatter
        xAxisHumid.valueFormatter = formatter

        // Đặt số lượng nhãn phù hợp
        val labelCount = when {
            timeRangeHours < 2 -> 6  // Hiện 6 nhãn cho dữ liệu ngắn
            timeRangeHours < 24 -> 8 // Hiện 8 nhãn cho 1 ngày
            timeRangeHours < 168 -> 7 // Hiện 7 nhãn cho 1 tuần
            else -> 10 // Hiện 10 nhãn cho thời gian dài hơn
        }

        xAxisTemp.setLabelCount(labelCount, false)
        xAxisHumid.setLabelCount(labelCount, false)

        // Thiết lập min/max cho trục X để hiển thị đúng phạm vi dữ liệu
        xAxisTemp.axisMinimum = minTime.toFloat()
        xAxisTemp.axisMaximum = maxTime.toFloat()
        xAxisHumid.axisMinimum = minTime.toFloat()
        xAxisHumid.axisMaximum = maxTime.toFloat()

        Log.d("ChartActivity", "X-axis configured: labelCount=$labelCount, range=$timeRangeHours hours")
    }
}