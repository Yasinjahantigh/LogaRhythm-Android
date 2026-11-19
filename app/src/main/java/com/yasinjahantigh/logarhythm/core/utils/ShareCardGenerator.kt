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
    val textScale: Float = 1.0f
)

/**
 * Renders customizable high-res graphic cards (and fast in-memory previews) for:
 * - Now Playing Song Cards
 * - Lyric Quote Cards (overlap-proof + RTL Persian friendly)
 * - Listening Report Cards
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
        val scale = opts.textScale.coerceIn(0.8f, 1.3f)

        // Centered artwork (or vinyl disc in WARM_VINYL style)
        val artSize = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> min(cardRect.width() - innerPad * 2, 740f)
            ShareCardAspect.POST_4_5 -> min(cardRect.width() - innerPad * 2, 620f)
            ShareCardAspect.SQUARE_1_1 -> min(cardRect.width() - innerPad * 2, 470f)
        }
        val artLeft = (w - artSize) / 2f
        val artTop = cardRect.top + innerPad + if (opts.aspect == ShareCardAspect.STORY_9_16) 28f else 0f
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

        // Track title, artist, album neatly stacked underneath artwork without ever overlapping
        var curY = artRect.bottom + if (opts.aspect == ShareCardAspect.SQUARE_1_1) 36f else 52f
        val titleSize = (54f * scale).coerceIn(38f, 70f)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = buildTextLayout(
            text = data.songTitle.ifBlank { "Untitled" },
            paint = titlePaint,
            width = innerW,
            maxLines = 2,
            alignment = Layout.Alignment.ALIGN_CENTER,
            lineMult = 1.15f
        )
        canvas.save()
        canvas.translate(cardRect.left + innerPad, curY)
        titleLayout.draw(canvas)
        canvas.restore()
        curY += titleLayout.height + 16f

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentText
            textSize = (36f * scale).coerceIn(26f, 48f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistLayout = buildTextLayout(
            text = data.artist.ifBlank { "Unknown Artist" },
            paint = artistPaint,
            width = innerW,
            maxLines = 1,
            alignment = Layout.Alignment.ALIGN_CENTER
        )
        canvas.save()
        canvas.translate(cardRect.left + innerPad, curY)
        artistLayout.draw(canvas)
        canvas.restore()
        curY += artistLayout.height + 12f

        val metaLine = listOfNotNull(
            data.album?.takeIf { it.isNotBlank() },
            data.durationLabel?.takeIf { it.isNotBlank() }
        ).joinToString("  ·  ")
        if (metaLine.isNotBlank() && curY + 42f < cardRect.bottom - 52f) {
            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.secondaryText
                textSize = (28f * scale).coerceIn(22f, 36f)
            }
            val metaLayout = buildTextLayout(
                text = metaLine,
                paint = metaPaint,
                width = innerW,
                maxLines = 1,
                alignment = Layout.Alignment.ALIGN_CENTER
            )
            canvas.save()
            canvas.translate(cardRect.left + innerPad, curY)
            metaLayout.draw(canvas)
            canvas.restore()
            curY += metaLayout.height + 28f
        }

        // Minimal playback bar decoration inside card if room allows
        val barY = cardRect.bottom - 56f
        if (barY > curY + 26f) {
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

        drawFooter(canvas, w, h, palette)
        cover?.recycle()
        return bitmap
    }

    // ---- Lyric Card Renderer (Overlap-Proof + RTL-Aware) -------------------------------------

    private fun drawLyricCardBitmap(context: Context, data: LyricCardData): Bitmap {
        val opts = data.options
        val w = opts.aspect.widthPx
        val h = opts.aspect.heightPx
        val palette = paletteFor(opts.style, opts.accentColor)
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

        val headerTextWidth = (cardRect.right - innerPad - textStartX).toInt().coerceAtLeast(240)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = if (opts.aspect == ShareCardAspect.SQUARE_1_1) 36f else 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
            textSize = if (opts.aspect == ShareCardAspect.SQUARE_1_1) 28f else 32f
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
        val dividerY = headerBottom + 34f
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.cardStroke
            strokeWidth = 2f
        }
        canvas.drawLine(cardRect.left + innerPad, dividerY, cardRect.right - innerPad, dividerY, dividerPaint)

        // Lyrics body: auto-fit font size dynamically to available box height so lines NEVER overlap or clip!
        val quoteLines = data.lines.map { it.trim() }.filter { it.isNotEmpty() }
        val quoteText = quoteLines.joinToString("\n\n")
        val availableTop = dividerY + 42f
        val availableBottom = cardRect.bottom - innerPad
        val availableHeight = (availableBottom - availableTop).coerceAtLeast(180f)
        val quoteWidth = (cardRect.width() - innerPad * 2).toInt().coerceAtLeast(360)

        val baseSize = when {
            quoteText.length > 210 -> 44f
            quoteText.length > 130 -> 52f
            quoteText.length > 70 -> 60f
            else -> 68f
        } * opts.textScale.coerceIn(0.75f, 1.35f)

        var chosenSize = baseSize.coerceIn(28f, 84f)
        var quoteLayout: StaticLayout
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val align = if (containsRtl(quoteText)) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_NORMAL

        while (true) {
            quotePaint.textSize = chosenSize
            quoteLayout = buildTextLayout(
                text = quoteText,
                paint = quotePaint,
                width = quoteWidth,
                maxLines = 18,
                alignment = align,
                lineExtra = chosenSize * 0.16f,
                lineMult = 1.18f
            )
            if (quoteLayout.height <= availableHeight || chosenSize <= 24f) {
                break
            }
            chosenSize -= 3f
        }

        val quoteTop = (availableTop + ((availableHeight - quoteLayout.height) / 2f))
            .coerceAtLeast(availableTop)

        canvas.save()
        canvas.clipRect(cardRect.left + innerPad, availableTop - 6f, cardRect.right - innerPad, availableBottom + 6f)
        canvas.translate(cardRect.left + innerPad, quoteTop)
        quoteLayout.draw(canvas)
        canvas.restore()

        drawFooter(canvas, w, h, palette)
        cover?.recycle()
        return bitmap
    }

    // ---- Listening Report Card Renderer ------------------------------------------------------

    private fun drawReportCardBitmap(context: Context, data: ReportData): Bitmap {
        val opts = data.options
        val w = opts.aspect.widthPx
        val h = opts.aspect.heightPx
        val palette = paletteFor(opts.style, opts.accentColor)
        val cover = loadBitmap(context, data.artworkUri)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackdrop(canvas, w, h, palette, opts.accentColor)

        val scale = opts.textScale.coerceIn(0.85f, 1.25f)
        val padX = 72f
        val contentW = (w - padX * 2).toInt()
        val compact = opts.aspect != ShareCardAspect.STORY_9_16

        var curY = if (compact) 74f else 120f

        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = (38f * scale).coerceIn(30f, 48f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headlineLayout = buildTextLayout(data.headline, badgePaint, contentW, maxLines = 1)
        canvas.save()
        canvas.translate(padX, curY)
        headlineLayout.draw(canvas)
        canvas.restore()
        curY += headlineLayout.height + 6f

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textSize = (28f * scale).coerceIn(22f, 36f)
        }
        val periodLayout = buildTextLayout(data.periodLabel, subPaint, contentW, maxLines = 1)
        canvas.save()
        canvas.translate(padX, curY)
        periodLayout.draw(canvas)
        canvas.restore()
        curY += periodLayout.height + if (compact) 26f else 40f

        // Hero stats summary card
        val artSize = if (compact) 156f else 196f
        val heroCardH = artSize + 56f
        val heroRect = RectF(padX, curY, w - padX, curY + heroCardH)
        drawGlassCard(canvas, heroRect, palette, radius = 38f)

        var heroTextX = heroRect.left + 32f
        if (cover != null) {
            val artRect = RectF(heroTextX, heroRect.top + 28f, heroTextX + artSize, heroRect.top + 28f + artSize)
            drawRoundedBitmap(canvas, cover, artRect, 28f)
            heroTextX += artSize + 30f
        }
        val heroTextW = (heroRect.right - 28f - heroTextX).toInt().coerceAtLeast(240)
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = ((if (compact) 64f else 78f) * scale).coerceIn(48f, 92f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val timeLayout = buildTextLayout(data.totalTimeLabel, timePaint, heroTextW, maxLines = 1)
        var ty = heroRect.top + 32f
        canvas.save()
        canvas.translate(heroTextX, ty)
        timeLayout.draw(canvas)
        canvas.restore()
        ty += timeLayout.height + 10f

        val playsPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textSize = (30f * scale).coerceIn(24f, 38f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val playsLayout = buildTextLayout(data.playsLabel, playsPaint, heroTextW, maxLines = 1)
        canvas.save()
        canvas.translate(heroTextX, ty)
        playsLayout.draw(canvas)
        canvas.restore()
        ty += playsLayout.height + 8f

        val vibeLine = listOfNotNull(data.peakHourLabel, data.vibeLabel).joinToString(" · ")
        if (vibeLine.isNotBlank()) {
            val vibePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accentText
                textSize = (26f * scale).coerceIn(20f, 32f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val vibeLayout = buildTextLayout(vibeLine, vibePaint, heroTextW, maxLines = 1)
            canvas.save()
            canvas.translate(heroTextX, ty)
            vibeLayout.draw(canvas)
            canvas.restore()
        }

        curY = heroRect.bottom + if (compact) 28f else 44f
        val maxItems = when (opts.aspect) {
            ShareCardAspect.STORY_9_16 -> 4
            ShareCardAspect.POST_4_5 -> 3
            ShareCardAspect.SQUARE_1_1 -> 2
        }
        val footerGuard = h - 120f

        if (data.topArtists.isNotEmpty() && curY + 140f < footerGuard) {
            curY = drawSectionList(
                canvas = canvas,
                width = w,
                title = "TOP ARTISTS",
                items = data.topArtists.take(maxItems),
                startY = curY,
                palette = palette,
                scale = scale,
                compact = compact
            ) + if (compact) 24f else 38f
        }

        if (data.topTracks.isNotEmpty() && curY + 140f < footerGuard) {
            drawSectionList(
                canvas = canvas,
                width = w,
                title = "TOP TRACKS",
                items = data.topTracks.take(maxItems),
                startY = curY,
                palette = palette,
                scale = scale,
                compact = compact
            )
        }

        drawFooter(canvas, w, h, palette)
        cover?.recycle()
        return bitmap
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

    private fun drawSectionList(
        canvas: Canvas,
        width: Int,
        title: String,
        items: List<Pair<String, String>>,
        startY: Float,
        palette: CardPalette,
        scale: Float,
        compact: Boolean
    ): Float {
        val padX = 72f
        var y = startY
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentText
            textSize = (26f * scale).coerceIn(22f, 32f)
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, padX + 12f, y, headerPaint)
        y += 22f

        val rowH = if (compact) 92f else 108f
        val cardHeight = (items.size * rowH) + 28f
        val boxRect = RectF(padX, y, width - padX, y + cardHeight)
        drawGlassCard(canvas, boxRect, palette, radius = 32f)

        var rowTop = y + 18f
        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentText
            textSize = (32f * scale).coerceIn(24f, 40f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primaryText
            textSize = ((if (compact) 30f else 34f) * scale).coerceIn(24f, 40f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.secondaryText
            textSize = ((if (compact) 23f else 26f) * scale).coerceIn(19f, 32f)
        }

        val textMaxW = (boxRect.width() - 138f).toInt().coerceAtLeast(240)
        items.forEachIndexed { idx, (name, meta) ->
            canvas.drawText("${idx + 1}", padX + 34f, rowTop + 42f, numPaint)
            val mainLayout = buildTextLayout(name, mainPaint, textMaxW, maxLines = 1)
            val subLayout = buildTextLayout(meta, subPaint, textMaxW, maxLines = 1)
            canvas.save()
            canvas.translate(padX + 96f, rowTop + 6f)
            mainLayout.draw(canvas)
            canvas.translate(0f, mainLayout.height + 2f)
            subLayout.draw(canvas)
            canvas.restore()
            rowTop += rowH
        }
        return y + cardHeight
    }

    private fun drawFooter(canvas: Canvas, w: Int, h: Int, palette: CardPalette) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.footerText
            textSize = 26f
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
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
        lineMult: Float = 1.0f
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width.coerceAtLeast(100))
        .setAlignment(alignment)
        .setMaxLines(maxLines)
        .setLineSpacing(lineExtra, lineMult)
        .setIncludePad(true)
        .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
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
