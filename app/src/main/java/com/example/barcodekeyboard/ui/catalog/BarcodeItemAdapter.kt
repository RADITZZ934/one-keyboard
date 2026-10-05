package com.example.barcodekeyboard.ui.catalog

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.data.model.BarcodeItem

/**
 * RecyclerView adapter for displaying and filtering non-fruit barcode items.
 */
class BarcodeItemAdapter(
    private var items: List<BarcodeItem> = emptyList(),
    private var isDarkMode: Boolean = true,
    private val onItemClicked: (BarcodeItem) -> Unit
) : RecyclerView.Adapter<BarcodeItemAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvItemName)
        val tvCode: TextView = itemView.findViewById(R.id.tvItemCode)
        val tvUom: TextView = itemView.findViewById(R.id.tvItemUom)
        val btnSelect: TextView = itemView.findViewById(R.id.btnTapToInput)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_barcode_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvCode.text = item.code
        holder.tvUom.text = item.uom

        // Dynamic theme support
        if (isDarkMode) {
            holder.tvName.setTextColor(Color.parseColor("#F2F3F5"))
            holder.tvUom.setTextColor(Color.parseColor("#949BA4"))
        } else {
            holder.tvName.setTextColor(Color.parseColor("#1A1C1E"))
            holder.tvUom.setTextColor(Color.parseColor("#5F6368"))
        }

        holder.itemView.setOnClickListener {
            onItemClicked(item)
        }
        holder.btnSelect.setOnClickListener {
            onItemClicked(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<BarcodeItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun updateTheme(isDark: Boolean) {
        this.isDarkMode = isDark
        notifyDataSetChanged()
    }
}
