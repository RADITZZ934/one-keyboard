package com.example.barcodekeyboard.ui.home

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.data.model.ScanHistoryItem

class ScanHistoryAdapter(
    private var items: List<ScanHistoryItem>,
    private val onCopyClicked: (ScanHistoryItem) -> Unit
) : RecyclerView.Adapter<ScanHistoryAdapter.ViewHolder>() {

    fun updateData(newItems: List<ScanHistoryItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvScanText: TextView = itemView.findViewById(R.id.tvScanText)
        private val tvScanFormat: TextView = itemView.findViewById(R.id.tvScanFormat)
        private val tvScanTime: TextView = itemView.findViewById(R.id.tvScanTime)
        private val btnCopyScan: ImageButton = itemView.findViewById(R.id.btnCopyScan)

        fun bind(item: ScanHistoryItem) {
            tvScanText.text = item.text
            tvScanFormat.text = item.format

            val relativeTime = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            tvScanTime.text = relativeTime

            btnCopyScan.setOnClickListener {
                onCopyClicked(item)
            }
        }
    }
}
