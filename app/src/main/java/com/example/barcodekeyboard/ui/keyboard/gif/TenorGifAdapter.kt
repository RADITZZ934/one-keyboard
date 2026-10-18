package com.example.barcodekeyboard.ui.keyboard.gif

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.barcodekeyboard.R
import com.example.barcodekeyboard.data.model.tenor.TenorResult

/**
 * High-performance adapter for rendering animated Tenor GIFs in a grid layout.
 */
class TenorGifAdapter(
    private val onGifClicked: (TenorResult, View) -> Unit
) : RecyclerView.Adapter<TenorGifAdapter.GifViewHolder>() {

    private val items = mutableListOf<TenorResult>()
    private var selectedPosition = -1

    fun submitList(newItems: List<TenorResult>) {
        items.clear()
        items.addAll(newItems)
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun clearLoadingState() {
        if (selectedPosition != -1) {
            val prev = selectedPosition
            selectedPosition = -1
            notifyItemChanged(prev)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gif_card, parent, false)
        return GifViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position == selectedPosition)
    }

    inner class GifViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivGifThumbnail)
        private val pbLoading: ProgressBar = itemView.findViewById(R.id.pbItemLoading)
        private val overlay: View = itemView.findViewById(R.id.viewSelectedOverlay)

        fun bind(item: TenorResult, isSelected: Boolean) {
            overlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            pbLoading.visibility = if (isSelected) View.VISIBLE else View.GONE

            val previewUrl = item.previewUrl
            if (previewUrl.isNotBlank()) {
                Glide.with(itemView.context)
                    .asGif()
                    .load(previewUrl)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .placeholder(R.drawable.bg_gif_card_rounded)
                    .error(R.drawable.bg_gif_card_rounded)
                    .into(ivThumbnail)
            } else {
                ivThumbnail.setImageDrawable(null)
            }

            itemView.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    val prev = selectedPosition
                    selectedPosition = pos
                    if (prev != -1) notifyItemChanged(prev)
                    notifyItemChanged(selectedPosition)
                    onGifClicked(item, itemView)
                }
            }
        }
    }
}
