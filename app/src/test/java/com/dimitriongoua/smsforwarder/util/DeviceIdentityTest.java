package com.dimitriongoua.smsforwarder.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DeviceIdentityTest {
    private static final String PACKAGE = "com.dimitriongoua.smsforwarder";

    @Test
    public void leMemeAndroidIdDonneToujoursLeMemeIdentifiant() {
        String id = DeviceIdentity.fromAndroidId("3f2a9c4b7d1e8f06", PACKAGE);
        assertEquals(id, DeviceIdentity.fromAndroidId(" 3F2A9C4B7D1E8F06 ", PACKAGE));
        assertTrue(id.matches("a-[0-9a-f]{32}"));
        // ANDROID_ID n'est jamais transmis tel quel.
        assertTrue(!id.contains("3f2a9c4b7d1e8f06"));
    }

    @Test
    public void deuxTelephonesOntDesIdentifiantsDifferents() {
        assertNotEquals(DeviceIdentity.fromAndroidId("3f2a9c4b7d1e8f06", PACKAGE),
                DeviceIdentity.fromAndroidId("3f2a9c4b7d1e8f07", PACKAGE));
    }

    @Test
    public void androidIdInutilisable() {
        assertNull(DeviceIdentity.fromAndroidId(null, PACKAGE));
        assertNull(DeviceIdentity.fromAndroidId("", PACKAGE));
        assertNull(DeviceIdentity.fromAndroidId("9774d56d682e549c", PACKAGE));
        assertNull(DeviceIdentity.fromAndroidId("0000000000000000", PACKAGE));
    }
}
