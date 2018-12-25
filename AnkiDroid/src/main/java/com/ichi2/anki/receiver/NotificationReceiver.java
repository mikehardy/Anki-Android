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

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.ichi2.anki.AnkiDroidApp;
import com.ichi2.anki.CollectionHelper;
import com.ichi2.anki.DeckPicker;
import com.ichi2.anki.IntentHandler;
import com.ichi2.anki.NotificationChannels;
import com.ichi2.anki.R;
import com.ichi2.libanki.Sched;
import com.ichi2.widget.WidgetStatus;

import javax.annotation.Nullable;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import timber.log.Timber;

/**
 * Responds to explicit broadcast Intents and builds work due notifications.
 * Expects Intent.getAction() to contain constants identifying type of notification.
 * Notifications themselves are configured to load the appropriate Activity for the work due.
 */
public class NotificationReceiver extends BroadcastReceiver {

    /** Constant to indicate no global reminder is desired */
    public static final int DISABLE_GLOBAL_REMINDER_NOTIFICATION = 1000000;

    /** Constant to indicate no global reminder is desired */
    public static final String DEFAULT_GLOBAL_REMINDER_THRESHOLD = "25";

    /** The id of the scheduled intent for general work due */
    public static final int GLOBAL_REMINDER_INTENT_ID = 0;

    /** The Intent.action for global reminders */
    public static final String GLOBAL_REMINDER_INTENT_ACTION = "com.ichi2.anki.DISPLAY_GLOBAL_NOTIFICATION";

    /** The Intent.action for deck reminders */
    public static final String DECK_REMINDER_INTENT_ACTION = "com.ichi2.anki.DISPLAY_DECK_NOTIFICATION";

    /** Deck ID used for the notification and intent request id handles */
    public static final String EXTRA_DECK_ID = "EXTRA_DECK_ID";

    /** The id of the notification for general work due */
    private static final int GLOBAL_REMINDER_NOTIFICATION_ID = 1;


    /**
     * Determine if global reminders are disabled completely
     *
     * @param context the context to inspect for notification preferences
     * @return boolean true if notifications are disabled
     */
    public static boolean globalRemindersDisabled(Context context) {
        int notificationThreshold =
                Integer.parseInt(AnkiDroidApp.getSharedPrefs(context).getString("minimumCardsDueForNotification", "0"));
        return notificationThreshold >= NotificationReceiver.DISABLE_GLOBAL_REMINDER_NOTIFICATION;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Timber.d("onReceive()");

        if (!((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0)) {
            Timber.d("Pending Intent seems to be from history");
        }
        else {
            Timber.d("Intent = %s with flags %s", intent, intent.getFlags());

            if ((intent.getFlags() & Intent.FLAG_RECEIVER_REPLACE_PENDING) == 0) {
                Timber.d("seems to be a replace pending intent?");
            }
            // does it have categories?
            //intent.getCategories().forEach(Timber::d);
            // iterate over the getExtras() bundle?
            Timber.d("extras contents: %s", intent.getExtras().describeContents());
        }

        // Guard against null intent actions. The switch default will catch them.
        String intentAction = intent.getAction();
        if (intentAction == null) {
            intentAction = "";
        }

        switch (intentAction) {
            case DECK_REMINDER_INTENT_ACTION:
                displayDeckReminderNotification(context, intent);
                break;
            case GLOBAL_REMINDER_INTENT_ACTION:
                displayGlobalWorkNotification(context);
                break;
            default:
                Timber.w("Received unknown notification intent: %s", intent.getAction());
                break;
        }
    }


    public static void displayGlobalWorkNotification(@Nullable Context context) {
        Timber.d("displayGlobalWorkNotification()");
        if (context == null) {
            context = AnkiDroidApp.getInstance().getApplicationContext();
        }

        // If notifications are disabled at the system level, return before any work
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (!notificationManager.areNotificationsEnabled()) {
            Timber.d("skipping global work notification, notifications are disabled at app level");
            return;
        }

        SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        int minCardsDue = Integer.parseInt(preferences.getString("minimumCardsDueForNotification", DEFAULT_GLOBAL_REMINDER_THRESHOLD));
        int dueCardsCount = WidgetStatus.fetchDue(context);
        if (dueCardsCount >= minCardsDue) {
            Timber.d("There is enough work due, displaying global work notification");
            displayNotification(
                    context,
                    context.getString(R.string.widget_minimum_cards_due_notification_ticker_title),
                    context.getString(R.string.widget_minimum_cards_due_notification_ticker_text, dueCardsCount),
                    DeckPicker.class,
                    NotificationChannels.Channel.GLOBAL_REMINDERS,
                    GLOBAL_REMINDER_INTENT_ID,
                    GLOBAL_REMINDER_NOTIFICATION_ID
            );
        } else {
            // Cancel the existing notification, but leave repeating alarm intact in case of work due next time
            Timber.d("Not enough work due, canceling global work notification");
            notificationManager.cancel(GLOBAL_REMINDER_NOTIFICATION_ID);
        }
    }


    private static void displayDeckReminderNotification(Context context, Intent intent) {

        final long deckId = intent.getLongExtra(EXTRA_DECK_ID, 0);
        Timber.d("displayDeckReminderNotification() for deck %s", deckId);
        displayDeckReminderNotification(context, deckId);
    }


    public static void displayDeckReminderNotification(@Nullable Context context, long deckId) {

        if (context == null) {
            context = AnkiDroidApp.getInstance().getApplicationContext();
        }

        // If the referenced deck doesn't exist (e.g., deleted before intent fired), cancel repeating alarm
        if (CollectionHelper.getInstance().getCol(context).getDecks().get(deckId, false) == null) {
            Timber.d("deck %s has been deleted, canceling schedule", deckId);
            final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            final PendingIntent reminderIntent = PendingIntent.getBroadcast(
                    context,
                    (int) deckId,
                    new Intent(context, NotificationReceiver.class).putExtra(EXTRA_DECK_ID, deckId),
                    0
            );

            if (alarmManager != null) {
                alarmManager.cancel(reminderIntent);
            }
        }

        // If notifications are disabled, return before any work
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (!notificationManager.areNotificationsEnabled()) {
            Timber.d("skipping deck work notification, notifications are disabled at app level");
            return;
        }

        // Fetch the deck counts and see if there is work
        Sched.DeckDueTreeNode deckDue = null;
        int total = 0;
        for (Sched.DeckDueTreeNode node : CollectionHelper.getInstance().getCol(context).getSched().deckDueTree()) {
            if (node.did == deckId) {
                deckDue = node;
                total = deckDue.revCount + deckDue.lrnCount + deckDue.newCount;
                break;
            }
        }
        if ((deckDue == null) || (total <= 0)) {
            Timber.d("not enough work due (%s) to notify for deck %s", total, deckId);
            notificationManager.cancel((int)deckId);
            return;
        }

        Timber.d("displaying deck work due notification for deck %s / cards %s", deckId, total);
        displayNotification(
                context,
                context.getString(R.string.reminder_title),
                context.getResources().getQuantityString(
                        R.plurals.reminder_text,
                        total,
                        CollectionHelper.getInstance().getCol(context).getDecks().name(deckId),
                        total
                ),
                IntentHandler.class,
                NotificationChannels.Channel.DECK_REMINDERS,
                (int) deckId,
                (int) deckId
        );
    }


    /**
     * Helper method to show a notification with all the standard trimmings
     *
     * @param context context to pull preferences from
     * @param title pre-formatted resource string for notification title
     * @param text pre-formatted resource string for notification text and ticker
     * @param intentTarget the target of the Intent if notification is clicked
     * @param channel the NotificationChannel to use
     * @param pendingIntentId handle to the PendingIntent for the notification
     * @param notificationId handle to the notification itself
     */
    public static void displayNotification(Context context, String title, String text, Class intentTarget,
                                           NotificationChannels.Channel channel, int pendingIntentId, int notificationId) {

        Timber.d("displayNotification()");
        final SharedPreferences preferences = AnkiDroidApp.getSharedPrefs(context);
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // This generates a log warning "Use of stream types is deprecated..."
        // The NotificationCompat code uses setSound() no matter what we do and triggers it.
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, NotificationChannels.getId(channel))
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setSmallIcon(R.drawable.ic_stat_notify)
                        .setColor(ContextCompat.getColor(context, R.color.material_light_blue_700))
                        .setContentTitle(title)
                        .setContentText(text)
                        .setTicker(text)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true);

        // Enable vibrate and blink if set in preferences
        if (preferences.getBoolean("widgetVibrate", false)) {
            builder.setVibrate(new long[] { 1000, 1000, 1000});
        }
        if (preferences.getBoolean("widgetBlink", false)) {
            builder.setLights(Color.BLUE, 1000, 1000);
        }

        // Creates an explicit intent for the target Activity class
        Intent notificationIntent = new Intent(context, intentTarget);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                pendingIntentId,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        builder.setContentIntent(pendingIntent);
        notificationManager.notify(notificationId, builder.build());
    }
}
