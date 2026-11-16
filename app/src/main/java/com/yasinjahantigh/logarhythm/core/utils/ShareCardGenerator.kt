package com.yasinjahantigh.logarhythm.core.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders 1080x1920 9:16 PNG graphic cards for Listening Reports and Lyric Quotes,
 * saves them under `cacheDir/share_cards/`, and exposes a FileProvider URI.
 */
object ShareCardGenerator {

    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val MAX_CACHED_CARDS = 8

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
        val accentColor: Int = 0xFFF4B266.toInt()
    )

    data class LyricCardData(
        val songTitle: String,
        val artist: String,
        val lines: List<String>,
        val artworkUri: String?,
        val accentColor: Int = 0xFFF4B266.toInt()
    )

    suspend fun createListeningReportCard(context: Context, data: ReportData): Uri =
        withContext(Dispatchers.IO) {
            val cover = loadBitmap(context, data.artworkUri)
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val topColor = blendDark(data.accentColor, 0.34f)
            val bottomColor = Color.rgb(12, 13, 18)
            drawBackdrop(canvas, topColor, bottomColor, data.accentColor)

            // Header badge
            val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFDADDE5.toInt()
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(data.headline, 88f, 150f, badgePaint)

            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xAAFFFFFF.toInt()
                textSize = 32f
            }
            canvas.drawText(data.periodLabel, 88f, 206f, subPaint)

            // Optional cover art / vibe pill
            var y = 260f
            if (cover != null) {
                val rect = RectF(88f, y, 88f + 220f, y + 220f)
                drawRoundedBitmap(canvas, cover, rect, 34f)
            }
            val heroX = if (cover != null) 344f else 88f
            val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 92f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(data.totalTimeLabel, heroX, y + 96f, timePaint)

            val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(data.playsLabel, heroX, y + 156f, metaPaint)

            val vibeLine = listOfNotNull(data.peakHourLabel, data.vibeLabel).joinToString(" · ")
            if (vibeLine.isNotBlank()) {
                val vibePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = lighten(data.accentColor, 0.35f)
                    textSize = 32f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(vibeLine, heroX, y + 208f, vibePaint)
            }

            y = if (cover != null) 560f else 540f

            if (data.topArtists.isNotEmpty()) {
                y = drawSectionList(
                    canvas = canvas,
                    title = "TOP ARTISTS",
                    items = data.topArtists.take(4),
                    startY = y,
                    accent = lighten(data.accentColor, 0.35f)
                )
                y += 38f
            }

            if (data.topTracks.isNotEmpty()) {
                drawSectionList(
                    canvas = canvas,
                    title = "TOP TRACKS",
                    items = data.topTracks.take(4),
                    startY = y,
                    accent = lighten(data.accentColor, 0.35f)
                )
            }

            drawFooter(canvas)
            cover?.recycle()
            saveCardAndGetUri(context, bitmap, "report")
        }

    suspend fun createLyricCard(context: Context, data: LyricCardData): Uri =
        withContext(Dispatchers.IO) {
            val cover = loadBitmap(context, data.artworkUri)
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val topColor = blendDark(data.accentColor, 0.42f)
            val bottomColor = Color.rgb(15, 16, 22)
            drawBackdrop(canvas, topColor, bottomColor, data.accentColor)

            // Center editorial glass card
            val cardRect = RectF(72f, 250f, WIDTH - 72f, HEIGHT - 230f)
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x26FFFFFF
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = 0x38FFFFFF
            }
            canvas.drawRoundRect(cardRect, 54f, 54f, cardPaint)
            canvas.drawRoundRect(cardRect, 54f, 54f, borderPaint)

            // Artwork + track info header inside card
            var textStartX = 128f
            val artTop = 308f
            if (cover != null) {
                drawRoundedBitmap(canvas, cover, RectF(128f, artTop, 288f, artTop + 160f), 28f)
                textStartX = 320f
            }
            val titleMaxW = (WIDTH - 128f - textStartX).toInt().coerceAtLeast(300)
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titleLayout = StaticLayout.Builder
                .obtain(data.songTitle, 0, data.songTitle.length, titlePaint, titleMaxW)
                .setMaxLines(2)
                .build()
            canvas.save()
            canvas.translate(textStartX, artTop + 18f)
            titleLayout.draw(canvas)
            canvas.restore()

            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                textSize = 32f
            }
            val artistY = artTop + 30f + titleLayout.height
            canvas.drawText(data.artist.take(40), textStartX, artistY, artistPaint)

            // Quote glyph & lyrics block (supports RTL & LTR naturally via StaticLayout)
            val quoteText = data.lines.joinToString("\n").trim()
            val quoteWidth = (WIDTH - 256).coerceAtLeast(400)
            val quoteSize = when {
                quoteText.length > 180 -> 46f
                quoteText.length > 100 -> 54f
                else -> 64f
            }
            val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = quoteSize
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val quoteLayout = StaticLayout.Builder
                .obtain(quoteText, 0, quoteText.length, quotePaint, quoteWidth)
                .setLineSpacing(14f, 1.12f)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            val availableTop = artTop + 230f
            val availableBottom = HEIGHT - 300f
            val quoteTop = (availableTop + ((availableBottom - availableTop - quoteLayout.height) / 2f))
                .coerceAtLeast(availableTop)

            canvas.save()
            canvas.translate(128f, quoteTop)
            quoteLayout.draw(canvas)
            canvas.restore()

            drawFooter(canvas)
            cover?.recycle()
            saveCardAndGetUri(context, bitmap, "lyric")
        }

    fun launchShareIntent(context: Context, uri: Uri, chooserTitle: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    private fun drawBackdrop(canvas: Canvas, topColor: Int, bottomColor: Int, accent: Int) {
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, WIDTH * 0.35f, HEIGHT.toFloat(),
                intArrayOf(topColor, bottomColor, Color.rgb(9, 10, 14)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        // Subtle atmospheric glow orb
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        canvas.drawCircle(WIDTH * 0.85f, 220f, 340f, glowPaint)
    }

    private fun drawSectionList(
        canvas: Canvas,
        title: String,
        items: List<Pair<String, String>>,
        startY: Float,
        accent: Int
    ): Float {
        var y = startY
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 28f
            letterSpacing = 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, 88f, y, headerPaint)
        y += 30f

        val cardHeight = (items.size * 114f) + 32f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1AFFFFFF }
        canvas.drawRoundRect(RectF(72f, y, WIDTH - 72f, y + cardHeight), 34f, 34f, boxPaint)

        var rowY = y + 72f
        val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xAAFFFFFF.toInt()
            textSize = 28f
        }

        items.forEachIndexed { idx, (name, meta) ->
            canvas.drawText("${idx + 1}", 112f, rowY, numPaint)
            canvas.drawText(name.take(30), 178f, rowY, mainPaint)
            canvas.drawText(meta.take(40), 178f, rowY + 38f, subPaint)
            rowY += 114f
        }
        return y + cardHeight
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x88FFFFFF.toInt()
            textSize = 30f
            letterSpacing = 0.06f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("LOGARHYTHM", 88f, HEIGHT - 105f, paint)
    }

    private fun drawRoundedBitmap(canvas: Canvas, bmp: Bitmap, dst: RectF, radius: Float) {
        val path = android.graphics.Path().apply {
            addRoundRect(dst, radius, radius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
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

    private fun lighten(color: Int, factor: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
