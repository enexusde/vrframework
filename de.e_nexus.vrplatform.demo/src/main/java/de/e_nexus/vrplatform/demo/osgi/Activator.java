package de.e_nexus.vrplatform.demo.osgi;

import de.e_nexus.vrplatform.demo.DemoBeans;
import de.e_nexus.vrplatform.demo.scene.VRScene;
import de.e_nexus.vrplatform.render.SceneRegistry;
import jakarta.enterprise.inject.se.SeContainer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishes demo's scene into the framework bundle's already-running VR
 * session when this bundle starts, and removes it again when this bundle
 * stops -- the render loop and window (owned by the framework bundle, see
 * its own Activator) keep running throughout, unaffected.
 */
public class Activator implements BundleActivator {

    private static final long SERVICE_WAIT_MS = 10_000;

    private ServiceTracker<SceneRegistry, SceneRegistry> tracker;
    private SceneRegistry registry;
    private VRScene scene;
    private SeContainer cdiContainer;

    @Override
    public void start(BundleContext context) throws InterruptedException {
        tracker = new ServiceTracker<>(context, SceneRegistry.class, null);
        tracker.open();
        registry = tracker.waitForService(SERVICE_WAIT_MS);
        if (registry == null) {
            Logger.getLogger("vrplatform").log(Level.SEVERE,
                    "No SceneRegistry service found within " + SERVICE_WAIT_MS
                            + "ms -- is the framework bundle installed and started?");
            return;
        }

        cdiContainer = DemoBeans.createContainer();
        scene = cdiContainer.select(VRScene.class).get();
        registry.publish(scene);
    }

    @Override
    public void stop(BundleContext context) {
        if (registry != null && scene != null) {
            registry.unpublish(scene);
        }
        if (cdiContainer != null) {
            cdiContainer.close();
        }
        if (tracker != null) {
            tracker.close();
        }
    }
}
