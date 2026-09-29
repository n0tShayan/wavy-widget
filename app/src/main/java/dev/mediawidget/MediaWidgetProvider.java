package dev.mediawidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

public class MediaWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        WidgetController.get(context).onWidgetsChanged();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        WidgetController.get(context).onWidgetsChanged();
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        WidgetController.get(context).onWidgetsChanged();
    }
}
