/***************************************************************************************
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.libanki.Collection;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

import androidx.core.app.NotificationManagerCompat;
import timber.log.Timber;

public class BootReceiver extends BroadcastReceiver implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Also called from {@link com.ichi2.anki.AnkiDroidApp}, protect from double-execution */
    private static boolean sWasRun = false;


    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("onReceive()");
        if (sWasRun) {
            Timber.d("we have already run, skipping");
            return;
        }
        sWasRun = true;

        // make sure we have storage access and can show notifications at all
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (!CollectionHelper.hasStorageAccessPermission(context)) {
            Timber.d("No storage access permission, skipping");
            return;
        }
        if (!notificationManager.areNotificationsEnabled()) {
            Timber.d("Notifications disabled at application level, skipping");
            return;
        }

        scheduleDeckReminders(context);
        scheduleGlobalReminder(context);

        // Make sure we are notified if preferences change, so we may re-align schedules
        PreferenceManager.getDefaultSharedPreferences(context).registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Timber.d("onSharedPreferenceChanged()");
        scheduleDeckReminders(AnkiDroidApp.getInstance().getApplicationContext());
        scheduleGlobalReminder(AnkiDroidApp.getInstance().getApplicationContext());
    }


    /**
     * Schedule any required PendingIntents, configured for us to display deck-specific work notifications
     */
    public static void scheduleDeckReminders(Context context) {
        Timber.d("scheduleDeckReminders()");
        try {
            Collection col = CollectionHelper.getInstance().getCol(context);
            if (col == null) return;
            for (JSONObject deck : CollectionHelper.getInstance().getCol(context).getDecks().all()) {
                col = CollectionHelper.getInstance().getCol(context);
                if (col.getDecks().isDyn(deck.getLong("id"))) {
                    continue;
                }
                final long deckConfigurationId = deck.getLong("conf");
                final JSONObject deckConfiguration = col.getDecks().getConf(deckConfigurationId);

                if (deckConfiguration.has("reminder")) {
                    final JSONObject reminderConfig = deckConfiguration.getJSONObject("reminder");

                    // Build the Intent that will be broadcast, w/deck action and deck id
                    Timber.d("examining status for deck %s, will schedule if needed" +
                            "", deck.getLong("id"));
                    Intent deckReminderIntent = new Intent(context, NotificationReceiver.class);
                    deckReminderIntent.setAction(NotificationReceiver.DECK_REMINDER_INTENT_ACTION);
                    deckReminderIntent.putExtra(NotificationReceiver.EXTRA_DECK_ID, deck.getLong("id"));

                    // Build the PendingIntent for scheduling, with deck id for cancellation if needed
                    final PendingIntent deckReminderPendingIntent = PendingIntent.getBroadcast(
                            context,
                            (int) deck.getLong("id"),
                            deckReminderIntent,
                            0
                    );

                    // disable the reminder just in case
                    ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE)).cancel(deckReminderPendingIntent);

                    if (reminderConfig.getBoolean("enabled")) {

                        // Each deck has it's own reminder time
                        scheduleIntent(
                                reminderConfig.getJSONArray("time").getInt(0),
                                reminderConfig.getJSONArray("time").getInt(1),
                                context,
                                deckReminderPendingIntent
                        );
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Set a PendingIntent configured for us to display the global work notification
     */
    public static void scheduleGlobalReminder(Context context) {
        Timber.d("scheduleGlobalReminder()");
        if (NotificationReceiver.globalRemindersDisabled(context)) {
            Timber.d("global work due reminders disabled, skipping");
            return;
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        Intent globalNotificationIntent = new Intent(context, NotificationReceiver.class);
        globalNotificationIntent.setAction(NotificationReceiver.GLOBAL_REMINDER_INTENT_ACTION);
        final PendingIntent notificationPendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        NotificationReceiver.GLOBAL_REMINDER_INTENT_ID,
                        globalNotificationIntent,
                        0
                );

        // Global reminder is scheduled for "start of day" from preferences
        scheduleIntent(sp.getInt("dayOffset", 0), 0, context, notificationPendingIntent);
    }


    private static void scheduleIntent(int hour, int minute, Context context, PendingIntent intent) {
        Timber.d("scheduleIntent() for HH:MM %s:%s", hour, minute);
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        ((AlarmManager) context.getSystemService(Context.ALARM_SERVICE))
                .setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    intent
        );
    }
}
