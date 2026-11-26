package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.ui.theme.resolveAppTypefacePair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

enum class ShareCardStyle {
    DARK_GLASS,
    VIBRANT_GRADIENT,
    MINIMAL_LIGHT,
    WARM_VINYL
}

enum class ShareCardAspect(val widthPx: Int, val heightPx: Int, val ratio: Float) {
    STORY_9_16(1080, 1920, 9f / 16f),
    POST_4_5(1080, 1350, 4f / 5f),
    SQUARE_1_1(1080, 1080, 1f)
}

data class ShareCardOptions(
    val style: ShareCardStyle = ShareCardStyle.DARK_GLASS,
    val aspect: ShareCardAspect = ShareCardAspect.STORY_9_16,
    val accentColor: Int = 0xFFF4B266.toInt(),
    val textScale: Float = 1.0f,
    val lineSpacing: Float = 1.0f,
    val fontKey: String = "vazirmatn",
    val customFontPath: String? = null,
    val centerLyrics: Boolean = false
)

/**
 * Renders customizable high-res graphic cards (and fast in-memory previews) for:
 * - Now Playing Song Cards
 * - Lyric Quote Cards (overlap-proof + RTL Persian friendly + custom line spacing)
 * - Listening Report Cards (dynamic row heights so larger text never clips or deforms)
 */
object ShareCardGenerator {

    private const val MAX_CACHED_CARDS = 10

    data class SongCardData(
        val songTitle: String,
        val artist: String,
        val album: String?,
        val durationLabel: String?,
        val artworkUri: String?,
        val options: ShareCardOptions = ShareCardOptions()
    )

    data class ReportData(
        val headline: String,
        val periodLabel: String,
        val totalTimeLabel: String,
        val playsLabel: String,
        val peakHourLabel: String?,
        val vibeLabel: String?,
        val topArtists: List<Pair<String, String>>,
        val topTracks: List<Pair<String, String>>,
        val artworkUri: String?,
        val accentColor: Int = 0xFFF4B266.toInt(),
        val options: ShareCardOptions = ShareCardOptions(accentColor = accentColor)
    )

    data class LyricCardData(
        val songTitle: String,
        val artist: String,
        val lines: List<String>,
        val artworkUri: String?,
        val accentColor: Int = 0xFFF4B266.toInt(),
        val options: ShareCardOptions = ShareCardOptions(accentColor = accentColor)
    )

    private data class CardPalette(
        val bgTop: Int,
        val bgMid: Int,
        val bgBottom: Int,
        val cardFill: Int,
        val cardStroke: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val accentText: Int,
        val footerText: Int,
        val isLight: Boolean
    )

    suspend fun renderSongCardPreview(context: Context, data: SongCardData, maxDim: Int = 640): Bitmap =
        withContext(Dispatchers.IO) {
            val full = drawSongCardBitmap(context, data)
            scaleForPreview(full, maxDim)
        }

    suspend fun createSongCard(context: Context, data: SongCardData): Uri =
        withContext(Dispatchers.IO) {
            val bitmap = drawSongCardBitmap(context, data)
            saveCardAndGetUri(context, bitmap, "song")
        }

    suspend fun renderLyricCardPreview(context: Context, data: LyricCardData, maxDim: Int = 640): Bitmap =
        withContext(Dispatchers.IO) {
            val full = drawLyricCardBitmap(context, data)
            scaleForPreview(full, maxDim)
        }

    suspend fun createLyricCard(context: Context, data: LyricCardData): Uri =
        withContext(Dispatchers.IO) {
            val bitmap = drawLyricCardBitmap(context, data)
            saveCardAndGetUri(context, bitmap, "lyric")
        }

    suspend fun renderListeningReportPreview(context: Context, data: ReportData, maxDim: Int = 640): Bitmap =
        withContext(Dispatchers.IO) {
            val full = drawReportCardBitmap(context, data)
            scaleForPreview(full, maxDim)
        }

    suspend fun createListeningReportCard(context: Context, data: ReportData): Uri =
        withContext(Dispatchers.IO) {
            val bitmap = drawReportCardBitmap(context, data)
            saveCardAndGetUri(context, bitmap, "report")
        }

    fun launchShareIntent(context: Context, uri: Uri, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    // ---- Song Card Renderer ------------------------------------------------------------------

    private fun drawSongCardBitmap(context: Context, data: SongCardData): Bitmap {
        val opts = data.options
        val w = opts.aspect.widthPx
        val h = opts.aspect.heightPx
        val palette = paletteFor(opts.style, opts.accentColor)
        val (regularTf, boldTf) = resolveAppTypefacePair(opts.fontKey, context, opts.customFontPath)
        val cover = loadBitmap(context, data.artworkUri)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackdrop(canvas, w, h, palette, opts.accentColor)

        val padX = 76f
        val topMargin = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> 190f
            ShareCardAspect.POST_4_5 -> 90f
            ShareCardAspect.SQUARE_1_1 -> 68f
        }
        val bottomMargin = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> 185f
            ShareCardAspect.POST_4_5 -> 110f
            ShareCardAspect.SQUARE_1_1 -> 88f
        }
        val cardRect = RectF(padX, topMargin, w - padX, h - bottomMargin)
        drawGlassCard(canvas, cardRect, palette, radius = 52f)

        val innerPad = 56f
        val innerW = (cardRect.width() - innerPad * 2).toInt().coerceAtLeast(300)
        val scale = opts.textScale.coerceIn(0.75f, 1.8f)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = (54f * scale).coerceIn(34f, 96f)
            typeface = boldTf
        }
        val titleLayout = buildTextLayout(
            text = data.songTitle.ifBlank { "Untitled" },
            paint = titlePaint,
            width = innerW,
            maxLines = 2,
            alignment = Layout.Alignment.ALIGN_CENTER,
            lineMult = 1.12f
        )

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentText
            textSize = (36f * scale).coerceIn(24f, 64f)
            typeface = boldTf
        }
        val artistLayout = buildTextLayout(
            text = data.artist.ifBlank { "Unknown Artist" },
            paint = artistPaint,
            width = innerW,
            maxLines = 2,
            alignment = Layout.Alignment.ALIGN_CENTER
        )

        val metaLine = listOfNotNull(
            data.album?.takeIf { it.isNotBlank() },
            data.durationLabel?.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
        val metaLayout = if (metaLine.isNotBlank()) {
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = (28f * scale).coerceIn(20f, 46f)
                typeface = regularTf
            }
            buildTextLayout(
                text = metaLine,
                paint = metaPaint,
                width = innerW,
                maxLines = 1,
                alignment = Layout.Alignment.ALIGN_CENTER
            )
        } else null

        val barReservedH = 74f
        val textGap = if (opts.aspect == ShareCardAspect.SQUARE_1_1) 30f else 44f
        val totalTextH = titleLayout.height + 14f + artistLayout.height +
            (metaLayout?.let { 10f + it.height } ?: 0f)
        val maxArtByAspect = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> min(cardRect.width() - innerPad * 2, 740f)
            ShareCardAspect.POST_4_5 -> min(cardRect.width() - innerPad * 2, 620f)
            ShareCardAspect.SQUARE_1_1 -> min(cardRect.width() - innerPad * 2, 470f)
        }
        val artTopExtra = if (opts.aspect == ShareCardAspect.STORY_9_16) 24f else 0f
        val availableArtH = (cardRect.height() - innerPad - artTopExtra - textGap - totalTextH - barReservedH)
            .coerceAtLeast(180f)
        val artSize = min(maxArtByAspect, availableArtH)
        val artLeft = (w - artSize) / 2f
        val artTop = cardRect.top + innerPad + artTopExtra
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)

        if (opts.style == ShareCardStyle.WARM_VINYL) {
            drawVinylBadge(canvas, artRect, cover, opts.accentColor)
        } else {
            if (cover != null) {
                drawRoundedBitmap(canvas, cover, artRect, 40f)
            } else {
                drawPlaceholderArt(canvas, artRect, opts.accentColor, 40f)
            }
        }

        var curY = artRect.bottom + textGap
        canvas.save()
        canvas.translate(cardRect.left + innerPad, curY)
        titleLayout.draw(canvas)
        canvas.restore()
        curY += titleLayout.height + 14f

        canvas.save()
        canvas.translate(cardRect.left + innerPad, curY)
        artistLayout.draw(canvas)
        canvas.restore()
        curY += artistLayout.height + 10f

        if (metaLayout != null && curY + metaLayout.height < cardRect.bottom - 52f) {
            canvas.save()
            canvas.translate(cardRect.left + innerPad, curY)
            metaLayout.draw(canvas)
            canvas.restore()
            curY += metaLayout.height + 24f
        }

        // Minimal playback bar decoration inside card if room allows
        val barY = cardRect.bottom - 52f
        if (barY > curY + 22f) {
            val barLeft = cardRect.left + innerPad + 24f
            val barRight = cardRect.right - innerPad - 24f
            val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (palette.isLight) 0x24000000 else 0x30FFFFFF
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accentText
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(barLeft, barY, barRight, barY, trackPaint)
            val progressX = barLeft + (barRight - barLeft) * 0.58f
            canvas.drawLine(barLeft, barY, progressX, barY, fillPaint)
            canvas.drawCircle(progressX, barY, 11f, fillPaint)
        }

        drawFooter(canvas, w, h, palette, boldTf)
        cover?.recycle()
        return bitmap
    }

    // ---- Lyric Card Renderer (Overlap-Proof + RTL-Aware + Custom Fonts & Spacing) ----------

    private fun drawLyricCardBitmap(context: Context, data: LyricCardData): Bitmap {
        val opts = data.options
        val w = opts.aspect.widthPx
        val h = opts.aspect.heightPx
        val palette = paletteFor(opts.style, opts.accentColor)
        val (regularTf, boldTf) = resolveAppTypefacePair(opts.fontKey, context, opts.customFontPath)
        val cover = loadBitmap(context, data.artworkUri)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackdrop(canvas, w, h, palette, opts.accentColor)

        val padX = 68f
        val topMargin = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> 200f
            ShareCardAspect.POST_4_5 -> 88f
            ShareCardAspect.SQUARE_1_1 -> 64f
        }
        val bottomMargin = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> 185f
            ShareCardAspect.POST_4_5 -> 112f
            ShareCardAspect.SQUARE_1_1 -> 92f
        }
        val cardRect = RectF(padX, topMargin, w - padX, h - bottomMargin)
        drawGlassCard(canvas, cardRect, palette, radius = 50f)

        val innerPad = 56f
        val headerTop = cardRect.top + innerPad
        val artSize = if (opts.aspect == ShareCardAspect.SQUARE_1_1) 124f else 152f
        var textStartX = cardRect.left + innerPad
        if (cover != null) {
            drawRoundedBitmap(
                canvas,
                cover,
                RectF(textStartX, headerTop, textStartX + artSize, headerTop + artSize),
                26f
            )
            textStartX += artSize + 32f
        }

        val scale = opts.textScale.coerceIn(0.75f, 1.8f)
        val lineSpacing = opts.lineSpacing.coerceIn(0.6f, 2.0f)
        val headerScale = (1f + (scale - 1f) * 0.25f).coerceIn(0.85f, 1.25f)

        val headerTextWidth = (cardRect.right - innerPad - textStartX).toInt().coerceAtLeast(240)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = (if (opts.aspect == ShareCardAspect.SQUARE_1_1) 36f else 42f) * headerScale
            typeface = boldTf
        }
        val titleLayout = buildTextLayout(
            text = data.songTitle.ifBlank { "Untitled" },
            paint = titlePaint,
            width = headerTextWidth,
            maxLines = 2,
            alignment = Layout.Alignment.ALIGN_NORMAL,
            lineMult = 1.12f
        )

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textSize = (if (opts.aspect == ShareCardAspect.SQUARE_1_1) 28f else 32f) * headerScale
            typeface = regularTf
        }
        val artistLayout = buildTextLayout(
            text = data.artist.ifBlank { "Unknown Artist" },
            paint = artistPaint,
            width = headerTextWidth,
            maxLines = 1,
            alignment = Layout.Alignment.ALIGN_NORMAL
        )

        val headerTextTotalH = titleLayout.height + 10f + artistLayout.height
        val headerBlockTop = headerTop + ((artSize - headerTextTotalH).coerceAtLeast(0f) / 2f)

        canvas.save()
        canvas.translate(textStartX, headerBlockTop)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height + 10f)
        artistLayout.draw(canvas)
        canvas.restore()

        val headerBottom = maxOf(headerTop + artSize, headerBlockTop + headerTextTotalH)

        // Subtle divider separating song header from lyrics so they NEVER collide
        val dividerY = headerBottom + 32f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.cardStroke
            strokeWidth = 2f
        }
        canvas.drawLine(cardRect.left + innerPad, dividerY, cardRect.right - innerPad, dividerY, dividerPaint)

        // Lyrics body: lay out each line independently so chosen font & line spacing stay crisp,
        // and dynamically auto-fit font size only if the block exceeds available height.
        val quoteLines = data.lines.map { it.trim() }.filter { it.isNotEmpty() }
        val totalChars = quoteLines.sumOf { it.length }
        val availableTop = dividerY + 42f
        val availableBottom = cardRect.bottom - innerPad
        val availableHeight = (availableBottom - availableTop).coerceAtLeast(180f)
        val quoteWidth = (cardRect.width() - innerPad * 2).toInt().coerceAtLeast(360)

        val baseSize = when {
            totalChars > 210 -> 44f
            totalChars > 130 -> 54f
            totalChars > 70 -> 64f
            else -> 76f
        } * scale

        var chosenSize = baseSize.coerceIn(26f, 140f)
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            typeface = boldTf
        }
        val wrapLineMult = (1.06f + (lineSpacing - 1.0f) * 0.22f).coerceIn(0.92f, 1.38f)

        var lineLayouts: List<StaticLayout>
        var lineGapPx: Float
        var totalBlockHeight: Float

        while (true) {
            quotePaint.textSize = chosenSize
            lineGapPx = chosenSize * 0.42f * lineSpacing
            lineLayouts = quoteLines.map { line ->
                val isRtlLine = containsRtl(line)
                buildTextLayout(
                    text = line,
                    paint = quotePaint,
                    width = quoteWidth,
                    maxLines = 12,
                    alignment = if (opts.centerLyrics) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL,
                    lineMult = wrapLineMult,
                    preferRtl = isRtlLine
                )
            }
            val gapsTotal = (lineLayouts.size - 1).coerceAtLeast(0) * lineGapPx
            totalBlockHeight = lineLayouts.sumOf { it.height }.toFloat() + gapsTotal
            if (totalBlockHeight <= availableHeight || chosenSize <= 22f) {
                break
            }
            chosenSize -= 2.5f
        }

        // If still slightly taller at minimum font size, compress inter-line gap so nothing clips
        if (totalBlockHeight > availableHeight && lineLayouts.size > 1) {
            val textOnlyH = lineLayouts.sumOf { it.height }.toFloat()
            val remainingForGaps = (availableHeight - textOnlyH).coerceAtLeast(0f)
            lineGapPx = remainingForGaps / (lineLayouts.size - 1)
            totalBlockHeight = textOnlyH + remainingForGaps
        }

        val quoteHeadroom = (availableHeight - totalBlockHeight).coerceAtLeast(0f)
        val showQuoteMark = quoteHeadroom >= 76f
        val verticalBlockShift = if (showQuoteMark) 18f else 0f
        var curY = (availableTop + (quoteHeadroom / 2f) + verticalBlockShift)
            .coerceAtLeast(availableTop)

        if (showQuoteMark) {
            val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(
                    0x66,
                    Color.red(palette.accentText),
                    Color.green(palette.accentText),
                    Color.blue(palette.accentText)
                )
                textSize = 72f
                typeface = boldTf
            }
            val isMostlyRtl = quoteLines.any(::containsRtl)
            val markX = when {
                opts.centerLyrics -> {
                    markPaint.textAlign = Paint.Align.CENTER
                    cardRect.centerX()
                }
                isMostlyRtl -> {
                    markPaint.textAlign = Paint.Align.RIGHT
                    cardRect.right - innerPad
                }
                else -> {
                    markPaint.textAlign = Paint.Align.LEFT
                    cardRect.left + innerPad
                }
            }
            val markBaseline = (curY - 14f).coerceAtLeast(availableTop + 44f)
            canvas.drawText("“", markX, markBaseline, markPaint)
        }

        canvas.save()
        canvas.clipRect(cardRect.left + innerPad, availableTop - 6f, cardRect.right - innerPad, availableBottom + 6f)
        for (layout in lineLayouts) {
            canvas.save()
            canvas.translate(cardRect.left + innerPad, curY)
            layout.draw(canvas)
            canvas.restore()
            curY += layout.height + lineGapPx
        }
        canvas.restore()

        drawFooter(canvas, w, h, palette, boldTf)
        cover?.recycle()
        return bitmap
    }

    // ---- Listening Report Card Renderer ------------------------------------------------------

    private fun drawReportCardBitmap(context: Context, data: ReportData): Bitmap {
        val opts = data.options
        val w = opts.aspect.widthPx
        val h = opts.aspect.heightPx
        val palette = paletteFor(opts.style, opts.accentColor)
        val (regularTf, boldTf) = resolveAppTypefacePair(opts.fontKey, context, opts.customFontPath)
        val cover = loadBitmap(context, data.artworkUri)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackdrop(canvas, w, h, palette, opts.accentColor)

        val requestedScale = opts.textScale.coerceIn(0.75f, 1.8f)
        val padX = 72f
        val contentW = (w - padX * 2).toInt()
        val compact = opts.aspect != ShareCardAspect.STORY_9_16
        val footerGuard = h - 98f

        var curY = if (compact) 68f else 112f

        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = (38f * requestedScale).coerceIn(26f, 68f)
            typeface = boldTf
        }
        val headlineLayout = buildTextLayout(data.headline, badgePaint, contentW, maxLines = 2)
        canvas.save()
        canvas.translate(padX, curY)
        headlineLayout.draw(canvas)
        canvas.restore()
        curY += headlineLayout.height + 6f

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textSize = (28f * requestedScale).coerceIn(20f, 48f)
            typeface = regularTf
        }
        val periodLayout = buildTextLayout(data.periodLabel, subPaint, contentW, maxLines = 1)
        canvas.save()
        canvas.translate(padX, curY)
        periodLayout.draw(canvas)
        canvas.restore()
        curY += periodLayout.height + if (compact) 22f else 34f

        // Hero stats summary card — dynamically sized to fit any textScale without clipping
        val artSize = if (compact) 148f else 188f
        val heroLeft = padX
        val heroRight = w - padX
        val textStartX = if (cover != null) heroLeft + 32f + artSize + 28f else heroLeft + 36f
        val heroTextW = (heroRight - 28f - textStartX).toInt().coerceAtLeast(240)

        var heroScale = requestedScale
        var timeLayout: StaticLayout
        var playsLayout: StaticLayout
        var vibeLayout: StaticLayout?
        var heroTextTotalH: Float
        val vibeLine = listOfNotNull(data.peakHourLabel, data.vibeLabel).joinToString(" · ")

        while (true) {
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.primaryText
                textSize = ((if (compact) 60f else 74f) * heroScale).coerceIn(38f, 126f)
                typeface = boldTf
            }
            timeLayout = buildTextLayout(data.totalTimeLabel, timePaint, heroTextW, maxLines = 2)

            val playsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = (30f * heroScale).coerceIn(22f, 52f)
                typeface = boldTf
            }
            playsLayout = buildTextLayout(data.playsLabel, playsPaint, heroTextW, maxLines = 2)

            vibeLayout = if (vibeLine.isNotBlank()) {
                val vibePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accentText
                    textSize = (25f * heroScale).coerceIn(19f, 44f)
                    typeface = boldTf
                }
                buildTextLayout(vibeLine, vibePaint, heroTextW, maxLines = 2)
            } else null

            heroTextTotalH = timeLayout.height + 8f + playsLayout.height +
                (vibeLayout?.let { 10f + it.height } ?: 0f)

            val maxHeroH = (footerGuard - curY) * 0.45f
            if (heroTextTotalH + 56f <= maxHeroH || heroScale <= 0.85f) break
            heroScale -= 0.08f
        }

        val heroCardH = maxOf(artSize + 52f, heroTextTotalH + 56f)
        val heroRect = RectF(heroLeft, curY, heroRight, curY + heroCardH)
        drawGlassCard(canvas, heroRect, palette, radius = 38f)

        // Vertical accent bar on cards without artwork for visual anchor
        if (cover == null) {
            val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accentText }
            val barRect = RectF(heroRect.left + 16f, heroRect.top + 24f, heroRect.left + 23f, heroRect.bottom - 24f)
            canvas.drawRoundRect(barRect, 4f, 4f, accentBarPaint)
        } else {
            val artTop = heroRect.top + (heroCardH - artSize) / 2f
            val artRect = RectF(heroLeft + 32f, artTop, heroLeft + 32f + artSize, artTop + artSize)
            drawRoundedBitmap(canvas, cover, artRect, 28f)
        }

        var ty = heroRect.top + (heroCardH - heroTextTotalH) / 2f
        canvas.save()
        canvas.translate(textStartX, ty)
        timeLayout.draw(canvas)
        canvas.restore()
        ty += timeLayout.height + 8f

        canvas.save()
        canvas.translate(textStartX, ty)
        playsLayout.draw(canvas)
        canvas.restore()
        ty += playsLayout.height + 10f

        if (vibeLayout != null) {
            canvas.save()
            canvas.translate(textStartX, ty)
            vibeLayout.draw(canvas)
            canvas.restore()
        }

        curY = heroRect.bottom + if (compact) 24f else 36f
        val maxItems = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> 4
            ShareCardAspect.POST_4_5 -> 3
            ShareCardAspect.SQUARE_1_1 -> 2
        }

        val artistsTitle = context.getString(R.string.share_card_top_artists_heading)
        val tracksTitle = context.getString(R.string.share_card_top_tracks_heading)
        val hasArtists = data.topArtists.isNotEmpty()
        val hasTracks = data.topTracks.isNotEmpty()
        val sectionGap = if (compact) 22f else 32f

        // Measure and fit Top Artists + Top Tracks cleanly inside remaining height above footerGuard
        val remainingH = (footerGuard - curY).coerceAtLeast(0f)
        var listScale = requestedScale
        var artistCount = if (hasArtists) minOf(maxItems, data.topArtists.size) else 0
        var trackCount = if (hasTracks) minOf(maxItems, data.topTracks.size) else 0

        var artistSection: PreparedSection? = null
        var trackSection: PreparedSection? = null

        while (true) {
            artistSection = if (artistCount > 0) {
                prepareSectionList(w, artistsTitle, data.topArtists.take(artistCount), palette, listScale, compact, regularTf, boldTf)
            } else null
            trackSection = if (trackCount > 0) {
                prepareSectionList(w, tracksTitle, data.topTracks.take(trackCount), palette, listScale, compact, regularTf, boldTf)
            } else null

            val needed = (artistSection?.totalHeight ?: 0f) +
                (if (artistSection != null && trackSection != null) sectionGap else 0f) +
                (trackSection?.totalHeight ?: 0f)

            if (needed <= remainingH) break

            // Trim item counts fairly first, then scale down list font only if even 1 item won't fit
            if (artistCount > 1 || trackCount > 1) {
                if (trackCount >= artistCount && trackCount > 1) trackCount--
                else if (artistCount > 1) artistCount--
            } else if (listScale > 0.72f) {
                listScale -= 0.08f
            } else {
                // Last resort on extremely tight canvas: keep at least Top Artists
                if (trackCount > 0 && artistCount > 0) trackCount = 0
                else break
            }
        }

        if (artistSection != null && curY + artistSection.totalHeight <= footerGuard + 8f) {
            curY = drawPreparedSectionList(canvas, artistSection, curY, palette) + sectionGap
        }
        if (trackSection != null && curY + trackSection.totalHeight <= footerGuard + 8f) {
            drawPreparedSectionList(canvas, trackSection, curY, palette)
        }

        drawFooter(canvas, w, h, palette, boldTf)
        cover?.recycle()
        return bitmap
    }

    private data class PreparedSectionRow(
        val rank: Int,
        val mainLayout: StaticLayout,
        val subLayout: StaticLayout,
        val rowHeight: Float
    )

    private data class PreparedSection(
        val width: Int,
        val headerLayout: StaticLayout,
        val headerGap: Float,
        val boxPaddingV: Float,
        val rows: List<PreparedSectionRow>,
        val numPaint: TextPaint,
        val badgeSize: Float
    ) {
        val boxHeight: Float = rows.sumOf { it.rowHeight.toDouble() }.toFloat() + boxPaddingV * 2f
        val totalHeight: Float = headerLayout.height + headerGap + boxHeight
    }

    private fun prepareSectionList(
        width: Int,
        title: String,
        items: List<Pair<String, String>>,
        palette: CardPalette,
        scale: Float,
        compact: Boolean,
        regularTf: Typeface,
        boldTf: Typeface
    ): PreparedSection {
        val padX = 72f
        val contentW = (width - padX * 2).toInt().coerceAtLeast(240)
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentText
            textSize = (26f * scale).coerceIn(20f, 44f)
            letterSpacing = if (containsRtl(title)) 0f else 0.08f
            typeface = boldTf
        }
        val headerLayout = buildTextLayout(title, headerPaint, contentW, maxLines = 1)

        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentText
            textSize = (28f * scale).coerceIn(20f, 46f)
            typeface = boldTf
            textAlign = Paint.Align.CENTER
        }
        val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = ((if (compact) 30f else 34f) * scale).coerceIn(22f, 56f)
            typeface = boldTf
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textSize = ((if (compact) 23f else 26f) * scale).coerceIn(18f, 44f)
            typeface = regularTf
        }

        val badgeSize = (46f + (scale - 1f).coerceAtLeast(0f) * 20f).coerceIn(42f, 70f)
        val numColW = badgeSize + 24f
        val textMaxW = (contentW - numColW - 56f).toInt().coerceAtLeast(220)
        val minRowH = if (compact) 84f else 98f

        val rows = items.mapIndexed { idx, (name, meta) ->
            val mainLayout = buildTextLayout(name, mainPaint, textMaxW, maxLines = 1, preferRtl = containsRtl(name))
            val subLayout = buildTextLayout(meta, subPaint, textMaxW, maxLines = 1, preferRtl = containsRtl(meta))
            val rowH = maxOf(minRowH, mainLayout.height + subLayout.height + 24f)
            PreparedSectionRow(idx + 1, mainLayout, subLayout, rowH)
        }

        return PreparedSection(
            width = width,
            headerLayout = headerLayout,
            headerGap = 12f,
            boxPaddingV = 14f,
            rows = rows,
            numPaint = numPaint,
            badgeSize = badgeSize
        )
    }

    private fun drawPreparedSectionList(
        canvas: Canvas,
        section: PreparedSection,
        startY: Float,
        palette: CardPalette
    ): Float {
        val padX = 72f
        var y = startY
        canvas.save()
        canvas.translate(padX + 12f, y)
        section.headerLayout.draw(canvas)
        canvas.restore()
        y += section.headerLayout.height + section.headerGap

        val boxRect = RectF(padX, y, section.width - padX, y + section.boxHeight)
        drawGlassCard(canvas, boxRect, palette, radius = 32f)

        var rowTop = y + section.boxPaddingV
        val fm = section.numPaint.fontMetrics
        val badgeLeft = padX + 24f
        val badgeCenterX = badgeLeft + section.badgeSize / 2f
        val textStartX = badgeLeft + section.badgeSize + 22f
        val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                if (palette.isLight) 0x22 else 0x2E,
                Color.red(palette.accentText),
                Color.green(palette.accentText),
                Color.blue(palette.accentText)
            )
        }
        val rowDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(
                if (palette.isLight) 0x16 else 0x1C,
                Color.red(palette.primaryText),
                Color.green(palette.primaryText),
                Color.blue(palette.primaryText)
            )
            strokeWidth = 1.5f
        }

        for ((index, row) in section.rows.withIndex()) {
            val rowCenterY = rowTop + row.rowHeight / 2f
            val half = section.badgeSize / 2f
            val badgeRect = RectF(badgeLeft, rowCenterY - half, badgeLeft + section.badgeSize, rowCenterY + half)
            canvas.drawRoundRect(badgeRect, 15f, 15f, badgeFillPaint)

            val numBaseline = rowCenterY - (fm.ascent + fm.descent) / 2f
            canvas.drawText("${row.rank}", badgeCenterX, numBaseline, section.numPaint)

            val textBlockH = row.mainLayout.height + 2f + row.subLayout.height
            val textTop = rowTop + (row.rowHeight - textBlockH) / 2f
            canvas.save()
            canvas.translate(textStartX, textTop)
            row.mainLayout.draw(canvas)
            canvas.translate(0f, row.mainLayout.height + 2f)
            row.subLayout.draw(canvas)
            canvas.restore()

            rowTop += row.rowHeight
            if (index < section.rows.lastIndex) {
                canvas.drawLine(textStartX, rowTop, boxRect.right - 24f, rowTop, rowDividerPaint)
            }
        }
        return y + section.boxHeight
    }

    // ---- Shared Drawing Helpers --------------------------------------------------------------

    private fun paletteFor(style: ShareCardStyle, accent: Int): CardPalette = when (style) {
        ShareCardStyle.DARK_GLASS -> CardPalette(
            bgTop = blendDark(accent, 0.36f),
            bgMid = Color.rgb(15, 17, 24),
            bgBottom = Color.rgb(9, 10, 14),
            cardFill = 0x22FFFFFF,
            cardStroke = 0x38FFFFFF,
            primaryText = Color.WHITE,
            secondaryText = 0xCCFFFFFF.toInt(),
            accentText = lighten(accent, 0.35f),
            footerText = 0x88FFFFFF.toInt(),
            isLight = false
        )
        ShareCardStyle.VIBRANT_GRADIENT -> CardPalette(
            bgTop = accent,
            bgMid = blendDark(accent, 0.56f),
            bgBottom = blendDark(accent, 0.22f),
            cardFill = 0x2E000000,
            cardStroke = 0x45FFFFFF,
            primaryText = Color.WHITE,
            secondaryText = 0xE6FFFFFF.toInt(),
            accentText = lighten(accent, 0.55f),
            footerText = 0xAAFFFFFF.toInt(),
            isLight = false
        )
        ShareCardStyle.MINIMAL_LIGHT -> CardPalette(
            bgTop = blendLight(accent, 0.86f),
            bgMid = Color.rgb(246, 245, 242),
            bgBottom = Color.rgb(238, 236, 231),
            cardFill = 0xEEFFFFFF.toInt(),
            cardStroke = 0x1A000000,
            primaryText = Color.rgb(20, 21, 26),
            secondaryText = Color.rgb(90, 92, 102),
            accentText = blendDark(accent, 0.72f),
            footerText = Color.rgb(112, 114, 124),
            isLight = true
        )
        ShareCardStyle.WARM_VINYL -> CardPalette(
            bgTop = Color.rgb(34, 24, 20),
            bgMid = Color.rgb(20, 16, 15),
            bgBottom = Color.rgb(11, 10, 10),
            cardFill = 0x24F4D9B6,
            cardStroke = 0x40F4B266,
            primaryText = Color.rgb(252, 244, 232),
            secondaryText = Color.rgb(214, 198, 178),
            accentText = lighten(accent, 0.28f),
            footerText = Color.rgb(180, 162, 140),
            isLight = false
        )
    }

    private fun drawBackdrop(canvas: Canvas, w: Int, h: Int, palette: CardPalette, accent: Int) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w * 0.4f, h.toFloat(),
                intArrayOf(palette.bgTop, palette.bgMid, palette.bgBottom),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Soft feathered radial highlight orb (no hard circle boundary!)
        val orbAlpha = if (palette.isLight) 42 else 64
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.84f, h * 0.14f, w * 0.55f,
                intArrayOf(
                    Color.argb(orbAlpha, Color.red(accent), Color.green(accent), Color.blue(accent)),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), orbPaint)
    }

    private fun drawGlassCard(canvas: Canvas, rect: RectF, palette: CardPalette, radius: Float) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.cardFill }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = palette.cardStroke
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun drawVinylBadge(canvas: Canvas, rect: RectF, cover: Bitmap?, accent: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() / 2f
        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16, 16, 20) }
        canvas.drawCircle(cx, cy, r, discPaint)

        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x18FFFFFF
        }
        for (factor in listOf(0.92f, 0.84f, 0.76f, 0.68f, 0.60f)) {
            canvas.drawCircle(cx, cy, r * factor, groovePaint)
        }

        val labelR = r * 0.52f
        val labelRect = RectF(cx - labelR, cy - labelR, cx + labelR, cy + labelR)
        if (cover != null) {
            drawRoundedBitmap(canvas, cover, labelRect, labelR)
        } else {
            drawPlaceholderArt(canvas, labelRect, accent, labelR)
        }
        canvas.drawCircle(cx, cy, r * 0.08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(22, 22, 26) })
    }

    private fun drawPlaceholderArt(canvas: Canvas, rect: RectF, accent: Int, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                accent, blendDark(accent, 0.45f), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
    }

    private fun drawFooter(canvas: Canvas, w: Int, h: Int, palette: CardPalette, boldTf: Typeface) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.footerText
            textSize = 26f
            letterSpacing = 0.08f
            typeface = boldTf
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LOGARHYTHM", w / 2f, h - 46f, paint)
    }

    private fun buildTextLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int = Int.MAX_VALUE,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        lineExtra: Float = 0f,
        lineMult: Float = 1.0f,
        preferRtl: Boolean = containsRtl(text)
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width.coerceAtLeast(100))
        .setAlignment(alignment)
        .setMaxLines(maxLines)
        .setLineSpacing(lineExtra, lineMult)
        .setIncludePad(true)
        .setTextDirection(
            if (preferRtl) TextDirectionHeuristics.FIRSTSTRONG_RTL
            else TextDirectionHeuristics.FIRSTSTRONG_LTR
        )
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()

    private fun containsRtl(text: String): Boolean = text.any { ch ->
        val dir = Character.getDirectionality(ch)
        dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun drawRoundedBitmap(canvas: Canvas, bmp: Bitmap, dst: RectF, radius: Float) {
        val path = Path().apply {
            addRoundRect(dst, radius, radius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }

    private fun scaleForPreview(full: Bitmap, maxDim: Int): Bitmap {
        val largest = maxOf(full.width, full.height)
        if (largest <= maxDim) return full
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (full.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (full.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(full, targetW, targetH, true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    private fun loadBitmap(context: Context, artworkUri: String?): Bitmap? {
        if (artworkUri.isNullOrBlank()) return null
        val uri = runCatching { Uri.parse(artworkUri) }.getOrNull() ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: run {
                val mmr = MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, uri)
                    mmr.embeddedPicture?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                } finally {
                    runCatching { mmr.release() }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCardAndGetUri(context: Context, bitmap: Bitmap, prefix: String): Uri {
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_CACHED_CARDS - 1)
            ?.forEach { it.delete() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun blendDark(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun blendLight(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun lighten(color: Int, factor: Float): Int = blendLight(color, factor)
}
