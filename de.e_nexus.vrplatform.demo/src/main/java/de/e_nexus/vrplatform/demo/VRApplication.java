package de.e_nexus.vrplatform.demo;

import de.e_nexus.vrplatform.demo.DemoBeans;
import de.e_nexus.vrplatform.demo.scene.VRScene;
import de.e_nexus.vrplatform.render.SceneRegistry;
import de.e_nexus.vrplatform.render.VRRenderer;
import de.e_nexus.vrplatform.vr.HMDDevice;
import jakarta.enterprise.inject.se.SeContainer;

/**
 * Standalone, single-process run: creates framework's HMD/renderer/registry
 * itself (rather than looking up an already-running instance the way the
 * OSGi Activator does) and immediately publishes demo's own scene into it.
 */
public class VRApplication {

    // Lets an external caller (e.g. the OSGi BundleActivator's stop()) end the
    // render loop the same way closing the window or pressing Escape does,
    // since there's no window to close when Karaf shuts the bundle down.
    private volatile boolean stopRequested = false;

    public void requestStop() {
        stopRequested = true;
    }

    public void run() {
        HMDDevice hmd = new HMDDevice();
        SceneRegistry registry = new SceneRegistry();
        VRRenderer renderer = null;
        VRScene scene = null;
        SeContainer cdiContainer = null;

        try {
            hmd.initialize();

            renderer = new VRRenderer(hmd, registry);
            renderer.initialize();

            // Started only after the GL context exists, since injected components
            // (e.g. TextPanel) issue GL calls from their initialize() method.
            cdiContainer = DemoBeans.createContainer();
            scene = cdiContainer.select(VRScene.class).get();
            registry.publish(scene); // calls scene.initialize()

            long lastTime = System.nanoTime();

            while (!renderer.shouldClose() && !stopRequested) {
                long now = System.nanoTime();
                float dt = (now - lastTime) / 1_000_000_000f;
                lastTime = now;

                hmd.beginFrame();
                renderer.renderFrame(dt);
                hmd.pollEvents();
            }
        } finally {
            if (scene != null) registry.unpublish(scene); // calls scene.destroy()
            if (renderer != null) renderer.destroy();
            hmd.shutdown();
            if (cdiContainer != null) cdiContainer.close();
        }
    }
}
