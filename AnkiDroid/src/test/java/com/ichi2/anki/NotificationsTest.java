package com.ichi2.anki;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;

import com.ichi2.anki.receiver.NotificationReceiver;
import com.ichi2.compat.CompatHelper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;
import org.robolectric.shadows.ShadowNotificationManager;


@RunWith(RobolectricTestRunner.class)
public class NotificationsTest extends RobolectricTest {

    private ShadowAlarmManager shadowAlarmManager;
    private AlarmManager alarmManager;
    private ShadowNotificationManager shadowNotificationManager;
    private NotificationManager notificationManager;
    private NotificationReceiver notificationReceiver;

    @Before
    public void setUp() {
        super.setUp();
        notificationManager = (NotificationManager)getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE);
        shadowNotificationManager = Shadows.shadowOf(notificationManager);
        alarmManager = (AlarmManager)getTargetContext().getSystemService(Context.ALARM_SERVICE);
        shadowAlarmManager = Shadows.shadowOf(alarmManager);
    }

    @Config(sdk = { 16, 26 })
    @Test
    public void testNotificationChannels() {
        Assert.assertEquals("We already have notifications?", 0, shadowNotificationManager.getAllNotifications().size());

        // We should have channels already though
        if (CompatHelper.getSdkVersion() >= 26) {
            Assert.assertEquals("channels don't exist?", 4, shadowNotificationManager.getNotificationChannels().size());
        }
    }

    @Test
    public void testNotificationsDisabled() {

        // Set up a bunch of work due somehow, and toggle reminders off and on, make sure there are no notifications pending


    }


    @Test
    public void testGlobalReminder() {
        // make sure nothing is there at first

        // make sure there are channels etc after enabling?

        // make sure we set a notification correctly if there are enough cards due
    }


    @Test
    public void testRemindersOnReboot() {
        // make sure that reminders are rescheduled after a reboot
    }


    @Test
    public void testRemindersDuringReview() {
        // make sure we have the correct number to start

        // as we do reviews make sure the notification reflects state

        // make sure the notification goes away when we have reviewed everything
    }


    @Test
    public void testVibrationWhileReviewing() {
        // turn off vibration for global or specific deck notification if we are in there already
    }


    @Test
    public void testDeckSpecificMinimumCards() {
        // right now deck specific reminders go off no matter what, allow you to specify them
    }


    @Test
    public void testGlobalReminderTiming() {
        // right now the global reminder can't be changed like the deck-specific ones can
    }
}
