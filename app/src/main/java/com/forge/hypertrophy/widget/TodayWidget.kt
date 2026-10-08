package com.forge.hypertrophy.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.forge.hypertrophy.MainActivity
import com.forge.hypertrophy.R
import com.forge.hypertrophy.ui.theme.Cream
import com.forge.hypertrophy.ui.theme.Ink
import com.forge.hypertrophy.ui.theme.Rose
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Today's workout and the current streak. Tapping opens the Today screen. */
class TodayWidget : GlanceAppWidget() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun summaryProvider(): TodaySummaryProvider
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val provider = EntryPointAccessors.fromApplication(context, Dependencies::class.java).summaryProvider()
        val summary = runCatching { provider.summary() }.getOrDefault(TodaySummary.Empty)
        provideContent {
            GlanceTheme {
                Content(summary)
            }
        }
    }

    @Composable
    private fun Content(summary: TodaySummary) {
        val context = LocalContext.current
        val openToday = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Cream))
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(actionStartActivity(openToday)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_today_title),
                style = TextStyle(color = ColorProvider(Rose), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = headline(context, summary),
                style = TextStyle(color = ColorProvider(Ink), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = context.resources.getQuantityString(R.plurals.widget_streak, summary.streak, summary.streak),
                    style = TextStyle(color = ColorProvider(Ink), fontSize = 14.sp),
                )
            }
        }
    }

    private fun headline(context: Context, summary: TodaySummary): String {
        val label = summary.dayLabel ?: return context.getString(R.string.widget_no_program)
        return when {
            summary.inProgress -> context.getString(R.string.widget_in_progress, label)
            summary.completedToday -> context.getString(R.string.widget_done, label)
            summary.isRest -> context.getString(R.string.widget_rest, label)
            else -> label
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
