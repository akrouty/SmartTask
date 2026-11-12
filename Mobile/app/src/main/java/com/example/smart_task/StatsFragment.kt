package com.example.smart_task

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.smart_task.network.ApiClient
import com.example.smart_task.network.dto.ProjectResponseDto
import com.example.smart_task.network.dto.TasksResponse
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.components.Legend


class StatsFragment : Fragment(R.layout.fragment_stats) {

    private lateinit var tvProductivity: TextView
    private lateinit var tvDone: TextView
    private lateinit var tvAvg: TextView

    private lateinit var barChart: BarChart
    private lateinit var pieChart: PieChart
    private lateinit var lineChart: LineChart

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvProductivity = view.findViewById(R.id.tvProductivityValue)
        tvDone = view.findViewById(R.id.tvDoneValue)
        tvAvg = view.findViewById(R.id.tvAvgValue)

        barChart = view.findViewById(R.id.chartWeekBar)
        pieChart = view.findViewById(R.id.chartProjectPie)
        lineChart = view.findViewById(R.id.chartMonthlyLine)

        setupCharts()

        loadStats()
        animateEnter(view)
    }

    private fun loadStats() {
        val token = SessionManager.getToken(requireContext()) ?: return

        // 1) Load projects
        ApiClient.api.getProjects("Bearer $token").enqueue(object : Callback<List<ProjectResponseDto>> {
            override fun onResponse(
                call: Call<List<ProjectResponseDto>>,
                response: Response<List<ProjectResponseDto>>
            ) {
                val projects = response.body().orEmpty()
                // 2) Load tasks after projects
                loadTasksAndRender(token, projects)
            }

            override fun onFailure(call: Call<List<ProjectResponseDto>>, t: Throwable) {
                // fallback : still load tasks
                loadTasksAndRender(token, emptyList())
            }
        })
    }

    private fun loadTasksAndRender(token: String, projects: List<ProjectResponseDto>) {
        ApiClient.api.getTasks("Bearer $token").enqueue(object : Callback<TasksResponse> {
            override fun onResponse(call: Call<TasksResponse>, response: Response<TasksResponse>) {
                val tasks = response.body()?.items.orEmpty()

                val today = LocalDate.now()
                val weekEnd = today.plusDays(6)

                val doneCount = tasks.count { it.status.equals("done", true) }
                val inProgressCount = tasks.count { it.status.equals("in-progression", true) || it.status.equals("in-progress", true) }
                val totalCount = tasks.size

                // Weekly progression: tâches "due" cette semaine + done parmi elles
                val weekTasks = tasks.filter {
                    val d = parseDate(it.dueDate)
                    d != null && !d.isBefore(today) && !d.isAfter(weekEnd)
                }
                val weekTotal = weekTasks.size
                val weekDone = weekTasks.count { it.status.equals("done", true) }

                val productivityPct = if (totalCount > 0) (doneCount * 100 / totalCount) else 0


                animateCounterInt(tvDone, doneCount)
                animateCounterInt(tvProductivity, productivityPct, suffix = " %")

                // Temps moyen: pas dispo v1 (pas de time tracking)
                val open = tasks.filter { !it.status.equals("done", true) }

                val avgDays = open.mapNotNull { parseDate(it.dueDate) }
                    .map { it.toEpochDay() - today.toEpochDay() }
                    .filter { it >= 0 }
                    .average()

                tvAvg.text = if (avgDays.isNaN()) "—" else "${avgDays.toInt()} j"


                renderWeekBar(tasks)
                renderProjectPie(tasks, projects)
                renderMonthlyLine(tasks)
            }

            override fun onFailure(call: Call<TasksResponse>, t: Throwable) {
                // option: show placeholders
            }
        })
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    // ----------------- Animations -----------------

    private fun animateCounterInt(tv: TextView, target: Int, suffix: String = "") {
        val anim = ValueAnimator.ofInt(0, target)
        anim.duration = 650L
        anim.addUpdateListener {
            tv.text = "${it.animatedValue as Int}$suffix"
        }
        anim.start()
    }

    private fun animateEnter(root: View) {
        // Simple stagger
        val views = listOf(
            root.findViewById<View>(R.id.cardProductivity),
            root.findViewById<View>(R.id.cardDone),
            root.findViewById<View>(R.id.cardAvg),
            root.findViewById<View>(R.id.cardBar),
            root.findViewById<View>(R.id.cardPie),
            root.findViewById<View>(R.id.cardLine)
        )

        var delay = 80L
        views.forEach { v ->
            v.alpha = 0f
            v.translationY = 30f
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(380L)
                .start()
            delay += 70L
        }
    }

    // ----------------- Charts -----------------

    private fun setupCharts() {
        val ctx = requireContext()

        val textPrimary = ContextCompat.getColor(ctx, R.color.st_text_primary)
        val textSecondary = ContextCompat.getColor(ctx, R.color.st_text_secondary)
        val grid = ContextCompat.getColor(ctx, R.color.st_chart_grid)

        // ---------- BAR ----------
        barChart.description.isEnabled = false
        barChart.axisRight.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.setPinchZoom(false)

        barChart.legend.apply {
            textColor = textSecondary
            textSize = 11f
            isWordWrapEnabled = true
            formSize = 10f
        }

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = textSecondary
            granularity = 1f
            setCenterAxisLabels(true) // important pour groupBars
        }

        barChart.axisLeft.apply {
            axisMinimum = 0f
            setDrawGridLines(true)
            gridColor = grid
            textColor = textSecondary


            granularity = 1f
            isGranularityEnabled = true

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }
        }

        // ---------- PIE ----------
        pieChart.description.isEnabled = false
        pieChart.isDrawHoleEnabled = true
        pieChart.setHoleColor(Color.WHITE)
        pieChart.setUsePercentValues(true)

        // Donut (visuel)
        pieChart.holeRadius = 72f
        pieChart.transparentCircleRadius = 76f

        // Pas de labels sur les tranches (on garde % outside)
        pieChart.setDrawEntryLabels(false)

        //  Légende en bas + centrée
        pieChart.legend.apply {
            isEnabled = true
            textColor = textSecondary
            textSize = 10f

            verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
            horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            orientation = Legend.LegendOrientation.HORIZONTAL
            setDrawInside(false)

            isWordWrapEnabled = true
            yEntrySpace = 6f
            xEntrySpace = 14f
            formSize = 10f
        }

        //  réserve de la place pour la légende (sinon chevauchement)
        pieChart.setExtraOffsets(18f, 10f, 18f, 70f)
        pieChart.setMinOffset(0f)

        // ---------- LINE ----------
        lineChart.description.isEnabled = false
        lineChart.axisRight.isEnabled = false
        lineChart.setDrawGridBackground(false)

        lineChart.legend.apply {
            textColor = textSecondary
            textSize = 11f
            formSize = 10f
        }

        lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            textColor = textSecondary
            granularity = 1f
        }

        lineChart.axisLeft.apply {
            axisMinimum = 0f
            gridColor = grid
            textColor = textSecondary


            granularity = 1f
            isGranularityEnabled = true

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String = value.toInt().toString()
            }
        }
    }



    private fun renderWeekBar(tasks: List<com.example.smart_task.network.dto.TaskApiDto>) {
        val today = LocalDate.now()
        val days = (0..6).map { today.plusDays(it.toLong()) }

        val dueCounts = days.map { day -> tasks.count { parseDate(it.dueDate) == day } }
        val doneCounts = days.map { day -> tasks.count { parseDate(it.dueDate) == day && it.status.equals("done", true) } }

        val dueEntries  = dueCounts.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }
        val doneEntries = doneCounts.mapIndexed { i, v -> BarEntry(i.toFloat(), v.toFloat()) }

        val set1 = BarDataSet(dueEntries, "Échéances")
        val set2 = BarDataSet(doneEntries, "Complétées")

        val ctx = requireContext()
        set1.color = ContextCompat.getColor(ctx, R.color.st_chart_created)
        set2.color = ContextCompat.getColor(ctx, R.color.st_chart_done)

        set1.setDrawValues(false)
        set2.setDrawValues(false)

        val data = BarData(set1, set2).apply { barWidth = 0.35f }

        // Group settings
        val groupSpace = 0.2f
        val barSpace = 0.05f

        barChart.data = data

        //  bornes X correctes
        val groupWidth = data.getGroupWidth(groupSpace, barSpace)
        barChart.xAxis.axisMinimum = 0f
        barChart.xAxis.axisMaximum = 0f + groupWidth * 7f

        barChart.groupBars(0f, groupSpace, barSpace)

        // Labels X
        val labels = listOf("Lun","Mar","Mer","Jeu","Ven","Sam","Dim")
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.labelCount = labels.size

        // Axe Y entier + labelCount cohérent
        val maxY = maxOf(dueCounts.maxOrNull() ?: 0, doneCounts.maxOrNull() ?: 0).coerceAtLeast(2)
        barChart.axisLeft.axisMaximum = (maxY + 1).toFloat()
        barChart.axisLeft.setLabelCount(maxY + 2, true)

        barChart.invalidate()
        barChart.animateY(650)
    }


    private fun renderProjectPie(
        tasks: List<com.example.smart_task.network.dto.TaskApiDto>,
        projects: List<ProjectResponseDto>
    ) {
        val ctx = requireContext()
        val nameById = projects.associate { it.id to it.name }

        fun shortLabel(s: String, max: Int = 22): String =
            if (s.length <= max) s else s.take(max - 1) + "…"


        val countsAll = tasks.groupBy { it.projectId }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val top = countsAll.take(5)
        val others = countsAll.drop(5).sumOf { it.second }

        val entries = buildList {
            top.forEach { (pid, count) ->
                val name = shortLabel(nameById[pid] ?: "Projet")
                add(PieEntry(count.toFloat(), name))
            }
            if (others > 0) add(PieEntry(others.toFloat(), "Autres"))
        }

        val set = PieDataSet(entries, "").apply {
            sliceSpace = 2f
            selectionShift = 6f


            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE


            valueLinePart1Length = 0.28f
            valueLinePart2Length = 0.18f
            valueLineWidth = 1f
            valueLineColor = ContextCompat.getColor(ctx, R.color.st_text_secondary)

            colors = listOf(
                ContextCompat.getColor(ctx, R.color.st_pie_blue),
                ContextCompat.getColor(ctx, R.color.st_pie_purple),
                ContextCompat.getColor(ctx, R.color.st_pie_green),
                ContextCompat.getColor(ctx, R.color.st_pie_orange),
                ContextCompat.getColor(ctx, R.color.st_pie_indigo),
                ContextCompat.getColor(ctx, R.color.st_purple) // "Autres" si présent
            )
        }

        val data = PieData(set).apply {
            setValueFormatter(com.github.mikephil.charting.formatter.PercentFormatter(pieChart))
            setValueTextColor(ContextCompat.getColor(ctx, R.color.st_text_primary))
            setValueTextSize(12f)
        }


        pieChart.apply {
            setUsePercentValues(true)
            setDrawEntryLabels(false)


            holeRadius = 72f
            transparentCircleRadius = 76f


            setExtraOffsets(12f, 10f, 12f, 18f)
            setMinOffset(0f)

            legend.apply {
                isEnabled = true
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                direction = Legend.LegendDirection.LEFT_TO_RIGHT

                setDrawInside(false)

                isWordWrapEnabled = true
                textSize = 10f
                yEntrySpace = 6f
                formSize = 10f
            }

            this.data = data
            invalidate()
            animateY(650)
        }
    }



    private fun renderMonthlyLine(tasks: List<com.example.smart_task.network.dto.TaskApiDto>) {
        val today = LocalDate.now()
        val start = today.minusWeeks(4)
        val weeks = (0..4).map { start.plusWeeks(it.toLong()) }

        val points = weeks.mapIndexed { idx, w ->
            val wEnd = w.plusDays(6)
            val done = tasks.count {
                val d = parseDate(it.dueDate)
                d != null && !d.isBefore(w) && !d.isAfter(wEnd) && it.status.equals("done", true)
            }
            Entry(idx.toFloat(), done.toFloat())
        }

        val ctx = requireContext()
        val cLine = ContextCompat.getColor(ctx, R.color.st_chart_line)

        val set = LineDataSet(points, "Tendance").apply {
            color = cLine
            setCircleColor(cLine)
            circleRadius = 4f
            lineWidth = 2f
            setDrawValues(false)

            setDrawFilled(true)
            fillAlpha = 35
            fillColor = cLine
        }

        lineChart.data = LineData(set)

        // X labels : Sem 1..Sem 5
        val xLabels = listOf("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5")
        lineChart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
        lineChart.xAxis.labelCount = xLabels.size
        lineChart.xAxis.axisMinimum = 0f
        lineChart.xAxis.axisMaximum = (xLabels.size - 1).toFloat()

        // ✅ Axe Y entier + pas de répétition
        val maxY = (points.maxOfOrNull { it.y } ?: 0f).toInt().coerceAtLeast(2)
        lineChart.axisLeft.axisMaximum = (maxY + 1).toFloat()
        lineChart.axisLeft.setLabelCount(maxY + 2, true)

        lineChart.invalidate()
        lineChart.animateX(700)
    }

}
