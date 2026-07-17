package de.e_nexus.vrplatform.demo;

import de.e_nexus.vrplatform.demo.scene.LoremIpsumPanel;
import de.e_nexus.vrplatform.demo.scene.VRScene;
import de.e_nexus.vrplatform.demo.scene.text.LoremIpsumContent;
import de.e_nexus.vrplatform.demo.scene.text.VariedFontSizeDecoration;
import de.e_nexus.vrplatform.scenecore.text.DefaultPanelTheme;
import de.e_nexus.vrplatform.scenecore.text.TypedTextDecoration;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;

/**
 * Builds the CDI container for demo's own beans -- shared by the standalone
 * app ({@code VRApplication}, one process, no OSGi) and the OSGi Activator
 * (publishes into a separately-running framework bundle's session).
 *
 * <p>disableDiscovery() + explicit addBeanClasses(): Weld's default
 * classpath-scanning bean discovery doesn't reliably enumerate classes
 * packaged inside an OSGi bundle (Felix's bundle: URLs aren't a protocol it
 * knows how to list entries under), so every bean is registered by hand.
 */
public final class DemoBeans {

    private DemoBeans() {
    }

    public static SeContainer createContainer() {
        return SeContainerInitializer.newInstance()
                .disableDiscovery()
                .addBeanClasses(
                        DefaultPanelTheme.class,
                        TypedTextDecoration.class,
                        LoremIpsumContent.class,
                        VariedFontSizeDecoration.class,
                        LoremIpsumPanel.class,
                        VRScene.class)
                .initialize();
    }
}
