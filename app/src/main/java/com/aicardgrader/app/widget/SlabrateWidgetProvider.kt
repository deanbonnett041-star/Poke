package com.aicardgrader.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.aicardgrader.app.MainActivity
import com.aicardgrader.app.R

/**
 * A one-tap home-screen shortcut into the capture flow. Widgets can't embed
 * a live camera preview or run the grading pipeline themselves (RemoteViews
 * only supports a small set of static-ish views) -- what they're good for
 * is skipping straight past the home screen at the moment it actually
 * matters, e.g. at a card show where every tap counts.
 */
class SlabrateWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_grade_card)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_START_CAPTURE
                putExtra(EXTRA_START_CAPTURE, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // A unique request code per widget instance keeps each widget's
            // PendingIntent distinct rather than all of them sharing one.
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        const val ACTION_START_CAPTURE = "com.aicardgrader.app.action.START_CAPTURE"
        const val EXTRA_START_CAPTURE = "start_capture"
    }
}
