package com.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36)
public class HeyZavaTest {

    private Context context;
    private AppResolver appResolver;
    private IntentAnalyzer intentAnalyzer;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        appResolver = new AppResolver(context);
        intentAnalyzer = new IntentAnalyzer(appResolver);
    }

    @Test
    public void testLanguageDetection() {
        assertEquals(LanguageDetector.Language.ENGLISH, LanguageDetector.detect("Open YouTube"));
        assertEquals(LanguageDetector.Language.HINGLISH, LanguageDetector.detect("YouTube kholo"));
        assertEquals(LanguageDetector.Language.HINGLISH, LanguageDetector.detect("Wi-Fi settings khol do please"));
        assertEquals(LanguageDetector.Language.HINDI, LanguageDetector.detect("यूट्यूब खोलो"));
    }

    @Test
    public void testCommandNormalizer() {
        assertEquals("YouTube kholo", CommandNormalizer.normalize("Hey Zava, YouTube kholo"));
        assertEquals("Wi-Fi settings kholo", CommandNormalizer.normalize("hey zava Wi-Fi settings kholo"));
        assertEquals("open YouTube", CommandNormalizer.normalize("Zava ji please open YouTube"));
        assertEquals("यूट्यूब खोलो", CommandNormalizer.normalize("हे ज़ावा, यूट्यूब खोलो"));
    }

    @Test
    public void testOpenAppIntent() {
        ZavaCommand cmd = intentAnalyzer.analyze("Hey Zava, YouTube kholo");
        assertEquals(ZavaCommand.INTENT_OPEN_APP, cmd.getIntent());
        assertTrue(cmd.getConversationalResponse().contains("YouTube"));
        assertEquals(1, cmd.getSteps().size());
        assertEquals(ZavaAction.ACTION_OPEN_APP, cmd.getSteps().get(0).getAction());
    }

    @Test
    public void testOpenWifiSettingsIntent() {
        ZavaCommand cmd = intentAnalyzer.analyze("Hey Zava, Wi-Fi settings kholo.");
        assertEquals(ZavaCommand.INTENT_WIFI_SETTINGS, cmd.getIntent());
        assertTrue(cmd.getConversationalResponse().contains("Wi-Fi"));
        assertEquals(1, cmd.getSteps().size());
        assertEquals(ZavaAction.ACTION_OPEN_SETTINGS, cmd.getSteps().get(0).getAction());
    }

    @Test
    public void testVolumeIntent() {
        ZavaCommand cmd = intentAnalyzer.analyze("Hey Zava, volume badhao");
        assertEquals(ZavaCommand.INTENT_VOLUME_CONTROL, cmd.getIntent());
        assertEquals("UP", cmd.getSteps().get(0).getTarget());
    }

    @Test
    public void testScreenReadIntent() {
        ZavaCommand cmd = intentAnalyzer.analyze("Hey Zava, screen par kya likha hai?");
        assertEquals(ZavaCommand.INTENT_READ_SCREEN, cmd.getIntent());
    }

    @Test
    public void testMultiActionParsing() {
        ZavaCommand cmd = intentAnalyzer.analyze("Hey Zava, YouTube kholke search karo Android 16");
        assertEquals(ZavaCommand.INTENT_MULTI_ACTION, cmd.getIntent());
        assertTrue(cmd.getSteps().size() >= 3);
    }
}
