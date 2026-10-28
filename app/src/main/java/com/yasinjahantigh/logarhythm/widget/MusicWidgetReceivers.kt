package com.yasinjahantigh.logarhythm.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Base receiver that fills the widget with the current state as soon as it is placed. */
abstract class MusicWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MusicWidgetUpdater.requestUpdate(context)
    }
}

class MusicWidgetSmallReceiver : MusicWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidgetSmall()
}

class MusicWidgetStripReceiver : MusicWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidgetStrip()
}

class MusicWidgetMediumReceiver : MusicWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidgetMedium()
}

class MusicWidgetLargeReceiver : MusicWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidgetLarge()
}

class MusicWidgetPlayerReceiver : MusicWidgetReceiverBase() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidgetPlayer()
}
