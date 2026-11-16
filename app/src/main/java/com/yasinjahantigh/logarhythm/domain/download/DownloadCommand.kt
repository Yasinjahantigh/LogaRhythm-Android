package com.yasinjahantigh.logarhythm.domain.download

import java.io.File

/** Single source of truth for the yt-dlp arguments we run. */
object DownloadCommand {

    fun searchOptions(cookiesPath: String? = null): List<Pair<String, String?>> = buildList {
        add("--dump-json" to null)
        add("--flat-playlist" to null)
        add("--no-warnings" to null)
        add("--ignore-errors" to null)
        add("--no-playlist" to null)
        add("--no-check-certificates" to null)
        add("--geo-bypass" to null)
        add("--no-cache-dir" to null)
        add("--socket-timeout" to "10")
        add("--extractor-args" to "youtube:player_client=android_vr,ios")
        if (cookiesPath != null) add("--cookies" to cookiesPath)
    }

    /** Without ffmpeg there is no postprocessing: keep the original audio stream as-is. */
    fun downloadOptions(
        workspace: File,
        videoId: String,
        cookiesPath: String? = null,
        qualityAbrKbps: Int? = null
    ): List<Pair<String, String?>> = buildList {
        val formatExpr = if (qualityAbrKbps != null && qualityAbrKbps > 0) {
            "bestaudio[abr<=${qualityAbrKbps + 20}][ext=m4a]/bestaudio[abr<=${qualityAbrKbps + 20}]/bestaudio[ext=m4a]/bestaudio/best"
        } else {
            "bestaudio[ext=m4a]/bestaudio[ext=mp3]/bestaudio/best"
        }
        add("-f" to formatExpr)
        add("--newline" to null)
        add("--continue" to null)
        add("--no-playlist" to null)
        add("--no-mtime" to null)
        add("--retries" to "5")
        add("--fragment-retries" to "5")
        add("--socket-timeout" to "25")
        add("--buffer-size" to "64k")
        add("--http-chunk-size" to "10M")
        add("--concurrent-fragments" to "4")
        add("--extractor-args" to "youtube:player_client=android_vr,android,ios")
        add("--paths" to workspace.absolutePath)
        add("--output" to "$videoId.%(ext)s")
        if (cookiesPath != null) add("--cookies" to cookiesPath)
    }
}
