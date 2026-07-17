package de.e_nexus.vrplatform.app;

import de.e_nexus.vrplatform.render.SceneRegistry;
import de.e_nexus.vrplatform.render.VRRenderer;
import de.e_nexus.vrplatform.vr.HMDDevice;

/**
 * Framework's own standalone app: a complete, runnable VR session showing
 * just the skydome, with an (initially empty) {@link SceneRegistry} other
 * code can publish content into -- either in this same process (see the
 * registry getter) or, in the OSGi/Karaf deployment, from a separate bundle
 * that looks up this registry as a published service (see {@link Activator}).
 */
public class VRPlatformApplication {

    // Lets an external caller (e.g. the OSGi BundleActivator's stop()) end the
    // render loop the same way closing the window or pressing Escape does,
    // since there's no window to close when Karaf shuts the bundle down.
    private volatile boolean stopRequested = false;

    private final SceneRegistry registry = new SceneRegistry();

    public void requestStop() {
        stopRequested = true;
    }

    /** Available immediately after construction, before {@link #run()} is called or returns. */
    public SceneRegistry sceneRegistry() {
        return registry;
    }

    public void run() {
        HMDDevice hmd = new HMDDevice();
        VRRenderer renderer = null;

        try {
            hmd.initialize();

            renderer = new VRRenderer(hmd, registry);
            renderer.initialize();

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
            if (renderer != null) renderer.destroy();
            hmd.shutdown();
        }
    }
}
