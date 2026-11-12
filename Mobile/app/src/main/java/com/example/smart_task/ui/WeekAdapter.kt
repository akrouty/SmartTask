package com.example.smart_task.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smart_task.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeekAdapter(
    private val onClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<WeekAdapter.VH>() {

    private val days = mutableListOf<LocalDate>()
    private var selected: LocalDate? = null

    fun submitWeek(list: List<LocalDate>, selectedDay: LocalDate?) {
        days.clear()
        days.addAll(list)
        selected = selectedDay
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_week_day, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = days.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val d = days[position]
        holder.bind(d, d == selected, onClick)
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDay: TextView = itemView.findViewById(R.id.tvWeekDay)
        private val tvDate: TextView = itemView.findViewById(R.id.tvWeekDate)

        fun bind(date: LocalDate, isSelected: Boolean, onClick: (LocalDate) -> Unit) {
            val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
            val dateFmt = DateTimeFormatter.ofPattern("dd", Locale.getDefault())
            tvDay.text = date.format(dayFmt).uppercase()
            tvDate.text = date.format(dateFmt)

            itemView.isSelected = isSelected
            itemView.setOnClickListener { onClick(date) }
        }
    }
}
