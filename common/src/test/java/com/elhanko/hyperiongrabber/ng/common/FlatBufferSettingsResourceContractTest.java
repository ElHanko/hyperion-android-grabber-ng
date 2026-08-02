package com.elhanko.hyperiongrabber.ng.common;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Verifies the small cross-module resource contract without an Android UI test framework. */
public class FlatBufferSettingsResourceContractTest {
    @Test
    public void mobilePreferenceScreenUsesNonPersistentControlAndSeparatePort() throws IOException {
        String xml = source("common/src/main/res/xml/pref_general.xml");
        assertTrue(xml.contains("pref_key_flatbuffer_transport_control"));
        assertTrue(xml.contains("android:persistent=\"false\""));
        assertTrue(xml.contains("pref_key_flatbuffer_port"));
        assertTrue(xml.contains("android:visible=\"false\""));
        assertTrue(xml.contains("pref_key_port"));
        assertFalse(xml.contains("use_flatbuffer"));
    }

    @Test
    public void mobileBindingUsesStableStringValuesAndUpdatesVisibility() throws IOException {
        String source = source("mobile/src/main/java/com/elhanko/hyperiongrabber/ng/mobile/SettingsActivity.java");
        assertTrue(source.contains("pref_key_transport"));
        assertTrue(source.contains("HyperionTransportPreferenceBinding.persistedValue"));
        assertTrue(source.contains("setPersistent(false)"));
        assertTrue(source.contains("setVisible(enabled)"));
        assertTrue(source.contains("isValidPort(port)"));
    }

    @Test
    public void tvGuidedSettingsExposeFocusableExperimentalControlAndDependentPort()
            throws IOException {
        String source = source("tv/src/main/java/com/elhanko/hyperiongrabber/ng/tv/fragments/settings/BasicSettingsStepFragment.kt");
        assertTrue(source.contains("ACTION_FLATBUFFER_TRANSPORT"));
        assertTrue(source.contains("ACTION_FLATBUFFER_PORT"));
        assertTrue(source.contains("CHECKBOX_CHECK_SET_ID"));
        assertTrue(source.contains("enterFlatBufferPort.isEnabled = flatBufferEnabled"));
        assertTrue(source.contains("isEnabled = enabled"));
        assertTrue(source.contains("HyperionTransportPreferenceBinding.persistedValue(enabled)"));
        assertTrue(source.contains("assertFlatBufferPortValue"));
    }

    @Test
    public void resourcesExplainExperimentalDefaultAndRestartBehavior() throws IOException {
        String strings = source("common/src/main/res/values/strings.xml");
        assertTrue(strings.contains("FlatBuffer is experimental"));
        assertTrue(strings.contains("Protocol Buffers remains the recommended default"));
        assertTrue(strings.contains("Transport changes take effect the next time the grabber starts"));
        assertTrue(strings.contains("FlatBuffer port must be a number between 1 and 65535"));
    }

    @Test
    public void discoveryRemainsProtocolBuffersOnly() throws IOException {
        String discovery = source("common/src/main/java/com/elhanko/hyperiongrabber/ng/common/discovery/AndroidNsdDiscoveryBackend.java");
        String selection = source("common/src/main/java/com/elhanko/hyperiongrabber/ng/common/discovery/DiscoverySelection.java");
        assertTrue(discovery.contains("_hyperiond-protobuf._tcp."));
        assertFalse(discovery.toLowerCase().contains("flatbuffer"));
        assertFalse(selection.contains("pref_key_transport"));
        assertFalse(selection.contains("pref_key_flatbuffer_port"));
    }

    @Test
    public void statusConsumersUseTheServiceTransportExtra() throws IOException {
        String mobile = source("mobile/src/main/java/com/elhanko/hyperiongrabber/ng/mobile/MainActivity.java");
        String tile = source("mobile/src/main/java/com/elhanko/hyperiongrabber/ng/mobile/HyperionGrabberTileService.java");
        String tv = source("tv/src/main/java/com/elhanko/hyperiongrabber/ng/tv/activities/MainActivity.java");
        assertTrue(mobile.contains("HyperionScreenService.BROADCAST_TRANSPORT"));
        assertTrue(tv.contains("HyperionScreenService.BROADCAST_TRANSPORT"));
        assertTrue(mobile.contains("HyperionTransportStatus.formatError"));
        assertTrue(tv.contains("HyperionTransportStatus.formatError"));
        assertTrue(tv.contains("HyperionTransportPreferenceBinding.isFlatBufferEnabled"));
        assertTrue(tile.contains("HyperionTransportPreferenceBinding.isFlatBufferEnabled"));
    }

    private static String source(String relativePath) throws IOException {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("Unable to locate project root");
        }
        return new String(Files.readAllBytes(current.resolve(relativePath)));
    }
}
