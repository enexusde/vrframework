package de.e_nexus.vrplatform.render;

import de.e_nexus.vrplatform.scenecore.VRSceneContribution;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds the {@link VRSceneContribution}s currently visible in the running VR
 * session. {@link VRRenderer} keeps rendering the skydome and iterating this
 * (possibly empty) list every frame regardless of what's published, so
 * content can be added and removed live -- e.g. by an OSGi bundle's
 * activator on start()/stop() -- without restarting the window or the
 * render loop.
 *
 * <p>publish/unpublish call {@link VRSceneContribution#initialize()}/{@code
 * destroy()}, which issue GL calls -- only legal on the thread that has the
 * GL context current, i.e. the render loop's own thread. A publishing
 * bundle's activator usually runs on a different thread (an OSGi lifecycle
 * thread), so in that case the actual work is queued and executed by
 * {@link VRRenderer} once per frame instead of running immediately on the
 * caller's thread; publish/unpublish block until that has happened, both so
 * a caller can rely on the contribution being fully live/torn down when the
 * call returns, and because an OSGi bundle's stop() must not return until
 * anything it published is completely gone. If the caller already *is* the
 * render thread (e.g. the single-process app publishing its own scene
 * before its own render loop has started polling), the work runs directly
 * instead -- queuing and waiting would deadlock, since nothing else would
 * ever drain the queue. A publisher on any *other* thread always queues and
 * waits, even if the render thread hasn't bound itself yet (e.g. a bundle
 * publishing moments after the framework bundle starts, before its
 * background thread has reached VRRenderer.initialize()) -- there's no safe
 * way to tell "not bound yet" apart from "never going to be this thread" up
 * front, so the wait just resolves as soon as the render loop starts polling.
 */
public class SceneRegistry {

    private final List<VRSceneContribution> contributions = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();
    private volatile Thread renderThread;

    /** Called once by {@link VRRenderer#initialize()}. */
    void bindRenderThread(Thread thread) {
        this.renderThread = thread;
    }

    public void publish(VRSceneContribution contribution) {
        runOnRenderThread(() -> {
            contribution.initialize();
            contributions.add(contribution);
        });
    }

    public void unpublish(VRSceneContribution contribution) {
        runOnRenderThread(() -> {
            if (contributions.remove(contribution)) {
                contribution.destroy();
            }
        });
    }

    private void runOnRenderThread(Runnable task) {
        if (Thread.currentThread() == renderThread) {
            task.run();
            return;
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        pendingTasks.add(() -> {
            try {
                task.run();
            } finally {
                done.complete(null);
            }
        });
        done.join(); // blocks the caller until the render loop runs applyPending() below
    }

    /** Call once per frame, on the render thread, before touching {@link #contributions()}. */
    void applyPending() {
        Runnable task;
        while ((task = pendingTasks.poll()) != null) {
            task.run();
        }
    }

    public List<VRSceneContribution> contributions() {
        return contributions;
    }
}
