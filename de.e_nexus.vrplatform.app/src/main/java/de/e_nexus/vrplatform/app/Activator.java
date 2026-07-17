package de.e_nexus.vrplatform.app;

import de.e_nexus.vrplatform.render.SceneRegistry;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts framework's own VR session (skydome only) when this bundle starts,
 * and publishes its {@link SceneRegistry} as an OSGi service so other
 * bundles (e.g. demo) can publish/unpublish their own content into the same
 * running session as their own lifecycle starts and stops -- see that
 * bundle's Activator. Stopping this bundle ends the render loop and closes
 * the window; publishing bundles should already have unpublished by then.
 */
public class Activator implements BundleActivator {

    private VRPlatformApplication application;
    private Thread thread;
    private ServiceRegistration<SceneRegistry> registration;

    @Override
    public void start(BundleContext context) {
        application = new VRPlatformApplication();
        // Registered before the render loop starts, so a consumer bundle can
        // find and publish into it as soon as this bundle becomes active.
        registration = context.registerService(SceneRegistry.class, application.sceneRegistry(), null);

        thread = new Thread(() -> {
            try {
                application.run();
            } catch (Throwable t) {
                // Uncaught-exception handlers only log toString() in this
                // Karaf setup, hiding the cause chain -- java.util.logging is
                // one of the frameworks pax-logging reliably bridges into
                // karaf.log (plain System.err isn't, when run as a service).
                Logger.getLogger("vrplatform").log(Level.SEVERE, "render loop failed", t);
            }
        }, "vrplatform-render-loop");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop(BundleContext context) throws InterruptedException {
        if (registration != null) {
            registration.unregister();
        }
        if (application != null) {
            application.requestStop();
        }
        if (thread != null) {
            thread.join(5000);
        }
    }
}
