package com.stressguard.watch.tile

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyles
import androidx.wear.protolayout.LayoutElementBuilders.Layout
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.stressguard.watch.app.StressGuardApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class StressTile : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val app = StressGuardApp.from(applicationContext)
        val reading = app.store.latestReading.first()
        val score = reading?.score
        val label = score?.let {
            when {
                it < 40 -> "CALM"
                it < 60 -> "MODERATE"
                it < 75 -> "ELEVATED"
                else -> "HIGH"
            }
        } ?: "WAITING"
        val argb = score?.let {
            when {
                it < 40 -> 0xFF4CAF50.toInt()
                it < 60 -> 0xFFFFC107.toInt()
                it < 75 -> 0xFFFF9800.toInt()
                else -> 0xFFF44336.toInt()
            }
        } ?: 0xFF888888.toInt()

        val column = Column.Builder()
            .addContent(
                Text.Builder()
                    .setText("STRESS")
                    .setFontStyle(FontStyles.caption2(requestParams.deviceConfiguration).build())
                    .build(),
            )
            .addContent(
                Text.Builder()
                    .setText(score?.toString() ?: "--")
                    .setFontStyle(
                        FontStyles.display1(requestParams.deviceConfiguration)
                            .setColor(argb(argb))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                Text.Builder()
                    .setText(label)
                    .setFontStyle(
                        FontStyles.caption1(requestParams.deviceConfiguration)
                            .setColor(argb(argb))
                            .build(),
                    )
                    .build(),
            )
            .build()

        val root = Box.Builder()
            .setWidth(dp(192f))
            .setHeight(dp(192f))
            .addContent(column)
            .build()

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(Layout.Builder().setRoot(root).build())
                    .build(),
            )
            .build()

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(60_000L)
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = Futures.immediateFuture(
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build(),
    )

    companion object {
        private const val RESOURCES_VERSION = "1"
    }
}
