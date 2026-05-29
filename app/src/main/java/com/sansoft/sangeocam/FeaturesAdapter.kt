package com.sansoft.sangeocam

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class FeaturesAdapter(
    private val features: List<Feature>,
    private val onFeatureClick: (Feature) -> Unit
) : RecyclerView.Adapter<FeaturesAdapter.FeatureViewHolder>() {

    class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardRoot: MaterialCardView = itemView.findViewById(R.id.cardRoot)
        val cardIconBackground: MaterialCardView = itemView.findViewById(R.id.cardIconBackground)
        val ivFeatureIcon: ImageView = itemView.findViewById(R.id.ivFeatureIcon)
        val tvFeatureTitle: TextView = itemView.findViewById(R.id.tvFeatureTitle)
        val tvFeatureDescription: TextView = itemView.findViewById(R.id.tvFeatureDescription)
        val viewStatusIndicator: View = itemView.findViewById(R.id.viewStatusIndicator)
        val tvFeatureStatus: TextView = itemView.findViewById(R.id.tvFeatureStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feature, parent, false)
        return FeatureViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        val feature = features[position]

        holder.ivFeatureIcon.setImageResource(feature.icon)
        holder.tvFeatureTitle.text = feature.title
        holder.tvFeatureDescription.text = feature.description

        // Set feature status based on type
        val (status, statusColor) = getFeatureStatus(feature.type)
        holder.tvFeatureStatus.text = status
        holder.viewStatusIndicator.backgroundTintList =
            ContextCompat.getColorStateList(holder.itemView.context, statusColor)
        holder.tvFeatureStatus.setTextColor(
            ContextCompat.getColor(holder.itemView.context, statusColor)
        )

        // Set click listener
        holder.cardRoot.setOnClickListener {
            onFeatureClick(feature)
            // Add ripple effect
            animateClick(holder.cardRoot)
        }

        // Add subtle hover effect
        holder.cardRoot.setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .setDuration(100)
                        .start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
            }
            false // Return false to allow click events
        }
    }

    override fun getItemCount(): Int = features.size

    private fun getFeatureStatus(type: FeatureType): Pair<String, Int> {
        return when (type) {
            FeatureType.TIMESTAMP_GEOTAG -> "Ready" to R.color.status_ready
            FeatureType.MAP_VIEW -> "Beta" to R.color.status_beta
            FeatureType.VIDEO_LOCATION -> "Ready" to R.color.status_ready
            FeatureType.WEATHER -> "Coming Soon" to R.color.status_coming_soon
            FeatureType.OFFLINE_MAPS -> "Pro" to R.color.status_pro
            FeatureType.MULTI_CAMERA -> "Ready" to R.color.status_ready
            FeatureType.SHARING -> "Ready" to R.color.status_ready
        }
    }

    private fun animateClick(view: View) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(80)
                    .start()
            }
            .start()
    }
}