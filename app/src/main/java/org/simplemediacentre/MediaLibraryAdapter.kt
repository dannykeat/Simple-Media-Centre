package org.simplemediacentre

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil3.load
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import org.simplemediacentre.model.MediaRecord

class MediaLibraryAdapter(
    private val context: Context,
    private var items: List<LibraryCard>,
) : BaseAdapter() {
    fun submitItems(updated: List<LibraryCard>) {
        items = updated
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): LibraryCard = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val card = (convertView as? LinearLayout) ?: createCard()
        bind(card, items[position])
        return card
    }

    private fun createCard(): LinearLayout {
        val density = context.resources.displayMetrics.density
        val tvScale = context.resources.configuration.smallestScreenWidthDp >= 600
        val padding = ((if (tvScale) 12 else 10) * density).toInt()
        val posterHeight = ((if (tvScale) 260 else 220) * density).toInt()
        val minimumCardWidth = ((if (tvScale) 180 else 150) * density).toInt()
        val titleSize = if (tvScale) 18f else 16f
        val subtitleSize = if (tvScale) 14f else 13f

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            background = focusBackground()
            setPadding(padding, padding, padding, padding)
            minimumWidth = minimumCardWidth

            addView(
                ImageView(context).apply {
                    tag = TAG_POSTER
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.rgb(28, 28, 28))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    posterHeight,
                )
            )

            addView(TextView(context).apply {
                tag = TAG_TITLE
                textSize = titleSize
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding(0, padding, 0, 0)
            })

            addView(TextView(context).apply {
                tag = TAG_SUBTITLE
                textSize = subtitleSize
                setTextColor(Color.LTGRAY)
                maxLines = 2
            })
        }
    }

    private fun bind(view: LinearLayout, card: LibraryCard) {
        val poster = findTagged<ImageView>(view, TAG_POSTER)
        val title = findTagged<TextView>(view, TAG_TITLE)
        val subtitle = findTagged<TextView>(view, TAG_SUBTITLE)

        title.text = card.title
        subtitle.text = card.subtitle

        val artwork = card.posterPath?.let { TMDB_IMAGE_BASE + it }
            ?: card.items.firstOrNull()?.uri
        poster.load(artwork) {
            crossfade(true)
            if (card.posterPath == null && artwork != null) {
                decoderFactory { result, options, _ ->
                    VideoFrameDecoder(result.source, options)
                }
            }
        }

        view.contentDescription = if (card.subtitle.isBlank()) {
            card.title
        } else {
            card.title + ", " + card.subtitle
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> findTagged(parent: ViewGroup, tagValue: String): T =
        parent.findViewWithTag<View>(tagValue) as T

    private fun focusBackground(): StateListDrawable {
        val normal = GradientDrawable().apply {
            setColor(Color.rgb(18, 18, 18))
            cornerRadius = 10f
        }
        val focused = GradientDrawable().apply {
            setColor(Color.rgb(45, 45, 45))
            setStroke(4, Color.WHITE)
            cornerRadius = 10f
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_pressed), focused)
            addState(intArrayOf(), normal)
        }
    }

    private companion object {
        const val TAG_POSTER = "poster"
        const val TAG_TITLE = "title"
        const val TAG_SUBTITLE = "subtitle"
        const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w342"
    }
}

data class LibraryCard(
    val key: String,
    val title: String,
    val subtitle: String,
    val posterPath: String?,
    val items: List<MediaRecord>,
)
