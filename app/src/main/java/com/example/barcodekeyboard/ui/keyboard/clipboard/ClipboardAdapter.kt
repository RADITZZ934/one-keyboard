package com.example.barcodekeyboard.ui.keyboard.clipboard

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.data.model.ClipboardItem

/**
 * Adapter for displaying clipboard history items (Papan Klip).
 */
class ClipboardAdapter(
    private var items: List<ClipboardItem>,
    private var isDarkMode: Boolean,
    private val onClipClicked: (ClipboardItem) -> Unit,
    private val onPinClicked: (ClipboardItem) -> Unit,
    private val onDeleteClicked: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    fun updateData(newItems: List<ClipboardItem>, isDark: Boolean = isDarkMode) {
        this.items = newItems
        this.isDarkMode = isDark
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clipboard_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvContent: TextView = itemView.findViewById(R.id.tvClipContent)
        private val tvTime: TextView = itemView.findViewById(R.id.tvClipTime)
        private val btnPin: ImageButton = itemView.findViewById(R.id.btnPinClip)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDeleteClip)

        fun bind(item: ClipboardItem) {
            val context = itemView.context
            tvContent.text = item.text

            val timeAgo = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            tvTime.text = if (item.isPinned) "📌 Disematkan • $timeAgo" else timeAgo.toString()

            // Pin state styling
            if (item.isPinned) {
                btnPin.setImageResource(R.drawable.ic_pin)
                btnPin.setColorFilter(ContextCompat.getColor(context, R.color.heliboard_accent))
            } else {
                btnPin.setImageResource(R.drawable.ic_pin_outline)
                val subColor = if (isDarkMode) {
                    ContextCompat.getColor(context, R.color.heliboard_text_sub)
                } else {
                    ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
                }
                btnPin.setColorFilter(subColor)
            }

            // Theme colors
            val textColor = if (isDarkMode) {
                ContextCompat.getColor(context, R.color.heliboard_text)
            } else {
                ContextCompat.getColor(context, R.color.heliboard_text_light)
            }
            val textSubColor = if (isDarkMode) {
                ContextCompat.getColor(context, R.color.heliboard_text_sub)
            } else {
                ContextCompat.getColor(context, R.color.heliboard_text_sub_light)
            }

            tvContent.setTextColor(textColor)
            tvTime.setTextColor(textSubColor)
            btnDelete.setColorFilter(textSubColor)

            itemView.setOnClickListener {
                onClipClicked(item)
            }

            btnPin.setOnClickListener {
                onPinClicked(item)
            }

            btnDelete.setOnClickListener {
                onDeleteClicked(item)
            }
        }
    }
}
