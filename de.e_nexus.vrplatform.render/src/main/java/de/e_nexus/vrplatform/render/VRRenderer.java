package de.e_nexus.vrplatform.render;

import de.e_nexus.vrplatform.gl.BlurPass;
import de.e_nexus.vrplatform.gl.EyeFramebuffer;
import de.e_nexus.vrplatform.gl.ShaderProgram;
import de.e_nexus.vrplatform.skydome.Skydome;
import de.e_nexus.vrplatform.scenecore.VRSceneContribution;
import de.e_nexus.vrplatform.vr.HMDDevice;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFWErrorCallback;

import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.openvr.VR.*;

/**
 * Owns the VR session: the window, the HMD-driven render loop, the skydome
 * (always rendered), and a {@link SceneRegistry} of {@link VRSceneContribution}s
 * (rendered on top, empty by default). Framework alone -- with an empty
 * registry -- is a complete, runnable app that shows just the sky; other
 * bundles publish/unpublish content into the same running session via the
 * registry instead of the renderer needing to know about them upfront.
 */
public class VRRenderer {

    private static final int MIRROR_WIDTH  = 1280;
    private static final int MIRROR_HEIGHT = 720;

    private final HMDDevice hmd;
    private final SceneRegistry registry;
    private long window;
    private EyeFramebuffer leftFbo;
    private EyeFramebuffer rightFbo;
    private ShaderProgram shader;
    private ShaderProgram texturedShader;

    // Captures the opaque scene each eye renders before its text panels are
    // drawn, so unfocused (semi-transparent) panels can show a blurred
    // version of it instead of the sharp live framebuffer.
    private EyeFramebuffer backdropCapture;
    private BlurPass blurPass;

    private final Skydome skydome = new Skydome();
    private final Vector3f sunDirection = new Vector3f(0f, 1f, 0f);
    private float dayTime = 0f;

    // Desktop-fallback camera state
    private float camX = 0f, camY = 0f, camZ = 2f;
    private float camYaw = 0f, camPitch = 0f;
    private double lastMouseX, lastMouseY;
    private boolean mouseLook = false;
    private final boolean[] keys = new boolean[GLFW_KEY_LAST + 1];

    // VR locomotion offset: added on top of the tracked headset position so
    // the arrow keys can move the player through the scene while wearing the HMD.
    private float locoX = 0f, locoY = 0f, locoZ = 0f;
    // Virtual "smooth turn" yaw on top of the tracked headset orientation, so
    // Ctrl+Alt+Left/Right can rotate the player without physically turning.
    private float locoYaw = 0f;

    public VRRenderer(HMDDevice hmd, SceneRegistry registry) {
        this.hmd = hmd;
        this.registry = registry;
    }

    public void initialize() {
        // Marks this thread as the one GL calls (including a published
        // contribution's initialize()/destroy()) are legal on -- see SceneRegistry.
        registry.bindRenderThread(Thread.currentThread());

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new RuntimeException("GLFW init failed");

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

        window = glfwCreateWindow(MIRROR_WIDTH, MIRROR_HEIGHT, "VR Platform – mirror view", 0L, 0L);
        if (window == 0L) throw new RuntimeException("Window creation failed");

        glfwSetKeyCallback(window, (win, key, sc, action, mods) -> {
            if (key >= 0 && key <= GLFW_KEY_LAST) keys[key] = action != GLFW_RELEASE;

            if (action == GLFW_RELEASE) return;

            boolean ctrl  = (mods & GLFW_MOD_CONTROL) != 0;
            boolean shift = (mods & GLFW_MOD_SHIFT) != 0;

            // Clipboard shortcuts: Ctrl+C/X/V and the Shift+Insert/Delete aliases.
            if (ctrl && key == GLFW_KEY_C) {
                broadcast(VRSceneContribution::handleCopy);
                return;
            }
            if (ctrl && key == GLFW_KEY_X) {
                broadcast(VRSceneContribution::handleCut);
                return;
            }
            if (ctrl && key == GLFW_KEY_V) {
                broadcast(VRSceneContribution::handlePaste);
                return;
            }
            if (shift && key == GLFW_KEY_INSERT) {
                broadcast(VRSceneContribution::handlePaste);
                return;
            }
            if (shift && key == GLFW_KEY_DELETE) {
                broadcast(VRSceneContribution::handleCut);
                return;
            }

            if (key == GLFW_KEY_BACKSPACE) {
                broadcast(VRSceneContribution::handleBackspace);
            } else if (key == GLFW_KEY_DELETE) {
                broadcast(VRSceneContribution::handleDelete);
            } else if (key == GLFW_KEY_ENTER) {
                broadcast(VRSceneContribution::handleNewLine);
            } else if (ctrl) {
                // Arrow keys with Ctrl are reserved for player movement (see
                // updateDesktopCamera / updateVRLocomotion's continuous polling).
            } else if (shift && key == GLFW_KEY_LEFT) {
                broadcast(VRSceneContribution::handleExtendLeft);
            } else if (shift && key == GLFW_KEY_RIGHT) {
                broadcast(VRSceneContribution::handleExtendRight);
            } else if (shift && key == GLFW_KEY_UP) {
                broadcast(VRSceneContribution::handleExtendUp);
            } else if (shift && key == GLFW_KEY_DOWN) {
                broadcast(VRSceneContribution::handleExtendDown);
            } else if (shift && key == GLFW_KEY_HOME) {
                broadcast(VRSceneContribution::handleExtendHome);
            } else if (shift && key == GLFW_KEY_END) {
                broadcast(VRSceneContribution::handleExtendEnd);
            } else if (key == GLFW_KEY_LEFT) {
                broadcast(VRSceneContribution::handleCursorLeft);
            } else if (key == GLFW_KEY_RIGHT) {
                broadcast(VRSceneContribution::handleCursorRight);
            } else if (key == GLFW_KEY_UP) {
                broadcast(VRSceneContribution::handleCursorUp);
            } else if (key == GLFW_KEY_DOWN) {
                broadcast(VRSceneContribution::handleCursorDown);
            } else if (key == GLFW_KEY_HOME) {
                broadcast(VRSceneContribution::handleHome);
            } else if (key == GLFW_KEY_END) {
                broadcast(VRSceneContribution::handleEnd);
            }
        });

        // Unicode text input for the typing panel, separate from the raw key
        // codes above (which drive movement/backspace).
        glfwSetCharCallback(window, (win, codepoint) -> {
            for (VRSceneContribution c : registry.contributions()) {
                c.handleTypedChar((char) codepoint);
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                mouseLook = action == GLFW_PRESS;
                glfwSetInputMode(win, GLFW_CURSOR, mouseLook ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            if (mouseLook) {
                camYaw   += (float)(xpos - lastMouseX) * 0.002f;
                camPitch  = Math.max(-1.4f, Math.min(1.4f, camPitch + (float)(ypos - lastMouseY) * 0.002f));
            }
            lastMouseX = xpos;
            lastMouseY = ypos;
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(0); // VR compositor controls frame timing

        createCapabilities();

        int eyeW = hmd.getRecommendedWidth();
        int eyeH = hmd.getRecommendedHeight();
        leftFbo  = new EyeFramebuffer(eyeW, eyeH);
        rightFbo = new EyeFramebuffer(eyeW, eyeH);
        leftFbo.initialize();
        rightFbo.initialize();

        backdropCapture = new EyeFramebuffer(eyeW, eyeH);
        backdropCapture.initialize();
        blurPass = new BlurPass(eyeW, eyeH);
        blurPass.initialize();

        shader = new ShaderProgram("/shaders/vertex.glsl", "/shaders/fragment.glsl");
        shader.compile();

        texturedShader = new ShaderProgram("/shaders/textured_vertex.glsl", "/shaders/textured_fragment.glsl");
        texturedShader.compile();

        skydome.initialize();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
    }

    private void broadcast(Consumer<VRSceneContribution> action) {
        for (VRSceneContribution c : registry.contributions()) {
            action.accept(c);
        }
    }

    public void renderFrame(float dt) {
        glfwPollEvents();
        // Runs any publish()/unpublish() requests queued from other threads
        // (e.g. an OSGi bundle's activator) -- their GL calls are only legal
        // here, on the thread that has the context current. See SceneRegistry.
        registry.applyPending();

        Matrix4f headMatrix;
        if (hmd.isVRAvailable()) {
            Matrix4f rawHead = hmd.getHeadMatrix();
            updateVRLocomotion(dt, HMDDevice.extractYaw(rawHead));
            // The play area's tracking origin is rarely under the player's
            // feet, so rotating around it (translate * rotateY * rawHead)
            // would swing the whole room around that point instead of
            // turning the player on the spot. Pivoting the rotation on the
            // HMD's own current room-space position keeps every rotation and
            // the resulting gaze direction anchored to the HMD, wherever in
            // the room it actually is.
            Vector3f hmdRoomPos = rawHead.getTranslation(new Vector3f());
            headMatrix = new Matrix4f()
                .translate(locoX, locoY, locoZ)
                .translate(hmdRoomPos)
                .rotateY(locoYaw)
                .translate(-hmdRoomPos.x, -hmdRoomPos.y, -hmdRoomPos.z)
                .mul(rawHead);
        } else {
            updateDesktopCamera(dt);
            headMatrix = buildDesktopHeadMatrix();
        }

        // Head position and gaze direction are derived from this same final
        // matrix, so both automatically follow the HMD's real, pivot-corrected pose.
        Vector3f headPos = headMatrix.getTranslation(new Vector3f());
        Vector3f gazeDir = headMatrix.transformDirection(new Vector3f(0f, 0f, -1f)).normalize();

        updateSun(dt);
        boolean altGrHeld = keys[GLFW_KEY_RIGHT_ALT];
        for (VRSceneContribution c : registry.contributions()) {
            c.updateFocus(headPos, gazeDir);
            c.updateCheckboxAim(headPos, gazeDir, altGrHeld, dt);
            c.updateCylinderAim(headPos, gazeDir, altGrHeld, dt);
            c.updateTerminals();
        }

        Matrix4f leftProj  = hmd.getProjectionMatrix(EVREye_Eye_Left);
        Matrix4f rightProj = hmd.getProjectionMatrix(EVREye_Eye_Right);
        Matrix4f leftView  = hmd.getEyeView(EVREye_Eye_Left,  headMatrix);
        Matrix4f rightView = hmd.getEyeView(EVREye_Eye_Right, headMatrix);

        renderEye(leftFbo,  leftProj,  leftView);
        renderEye(rightFbo, rightProj, rightView);

        if (hmd.isVRAvailable()) {
            hmd.submitFrame(EVREye_Eye_Left,  leftFbo.getColorTexture());
            hmd.submitFrame(EVREye_Eye_Right, rightFbo.getColorTexture());
        }

        blitMirror();
        glfwSwapBuffers(window);
    }

    /** Advances the day/sunset cycle; call once per frame, before rendering either eye. */
    private void updateSun(float dt) {
        dayTime += dt;
        float elevation = 0.25f + 0.55f * (float) Math.sin(dayTime * 0.05);
        float azimuth = 0.6f;
        sunDirection.set((float) (Math.cos(elevation) * Math.cos(azimuth)), (float) Math.sin(elevation),
                (float) (Math.cos(elevation) * Math.sin(azimuth))).normalize();
    }

    private void renderEye(EyeFramebuffer fbo, Matrix4f proj, Matrix4f view) {
        fbo.bind();
        glClear(GL_DEPTH_BUFFER_BIT); // color is fully repainted by the skydome below

        skydome.render(proj, view, sunDirection);

        Vector3f camPos = new Matrix4f(view).invert().getTranslation(new Vector3f());

        shader.use();
        shader.setMatrix4f("projection", proj);
        shader.setMatrix4f("view", view);
        shader.setVec3("viewPos", camPos);
        shader.setVec3("sunDirection", sunDirection);

        for (VRSceneContribution c : registry.contributions()) {
            c.render(shader);
        }

        texturedShader.use();
        texturedShader.setMatrix4f("projection", proj);
        texturedShader.setMatrix4f("view", view);
        texturedShader.setVec3("viewPos", camPos);
        texturedShader.setVec3("sunDirection", sunDirection);

        for (VRSceneContribution c : registry.contributions()) {
            c.renderTextured(texturedShader);
        }

        // Capture the opaque scene rendered so far and blur it, so panels
        // drawn next can composite their transparent areas against a
        // frosted-glass backdrop instead of GL-blending against the sharp
        // live framebuffer.
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo.getFramebufferId());
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, backdropCapture.getFramebufferId());
        glBlitFramebuffer(
            0, 0, fbo.getWidth(), fbo.getHeight(),
            0, 0, fbo.getWidth(), fbo.getHeight(),
            GL_COLOR_BUFFER_BIT, GL_LINEAR
        );
        int blurredBackdrop = blurPass.blur(backdropCapture.getColorTexture());

        fbo.bind(); // re-bind the eye target + viewport; the blur pass changed both
        for (VRSceneContribution c : registry.contributions()) {
            c.renderText(proj, view, blurredBackdrop, fbo.getWidth(), fbo.getHeight());
        }

        fbo.unbind();
    }

    private void blitMirror() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, leftFbo.getFramebufferId());
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBlitFramebuffer(
            0, 0, leftFbo.getWidth(), leftFbo.getHeight(),
            0, 0, MIRROR_WIDTH, MIRROR_HEIGHT,
            GL_COLOR_BUFFER_BIT, GL_LINEAR
        );
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    }

    private static final float ARROW_BOOST_FACTOR = 5f; // Ctrl+Shift+Arrow moves this much faster than plain Ctrl+Arrow
    private static final float ROTATE_SPEED = 3.6f; // rad/s for Ctrl+Alt+Left/Right turning (3x the original 1.2)
    private static final float MOVE_SPEED = 3.75f; // units/s for Ctrl+Arrow/Space/Shift movement (1.5x the original 2.5)

    private boolean ctrlHeld() {
        return keys[GLFW_KEY_LEFT_CONTROL] || keys[GLFW_KEY_RIGHT_CONTROL];
    }

    private boolean shiftHeld() {
        return keys[GLFW_KEY_LEFT_SHIFT] || keys[GLFW_KEY_RIGHT_SHIFT];
    }

    // Left Alt only: Right Alt (AltGr) is already the checkbox aim-and-hold gesture.
    private boolean altHeld() {
        return keys[GLFW_KEY_LEFT_ALT];
    }

    private void updateDesktopCamera(float dt) {
        boolean ctrl = ctrlHeld(); // arrow keys only move the camera while Ctrl is held
        boolean alt = altHeld();
        float speed = MOVE_SPEED * dt;
        float arrowSpeed = (ctrl && shiftHeld()) ? speed * ARROW_BOOST_FACTOR : speed;
        float sinY = (float) Math.sin(camYaw);
        float cosY = (float) Math.cos(camYaw);

        if (ctrl && alt) {
            if (keys[GLFW_KEY_LEFT])  camYaw += ROTATE_SPEED * dt;
            if (keys[GLFW_KEY_RIGHT]) camYaw -= ROTATE_SPEED * dt;
        }
        if (ctrl && keys[GLFW_KEY_UP])    { camX -= sinY * arrowSpeed; camZ -= cosY * arrowSpeed; }
        if (ctrl && keys[GLFW_KEY_DOWN])  { camX += sinY * arrowSpeed; camZ += cosY * arrowSpeed; }
        if (ctrl && !alt && keys[GLFW_KEY_LEFT])  { camX -= cosY * arrowSpeed; camZ += sinY * arrowSpeed; }
        if (ctrl && !alt && keys[GLFW_KEY_RIGHT]) { camX += cosY * arrowSpeed; camZ -= sinY * arrowSpeed; }

        if (keys[GLFW_KEY_SPACE])               camY += speed;
        if (keys[GLFW_KEY_LEFT_SHIFT] && !ctrl) camY -= speed; // Shift alone still means "descend"
    }

    private void updateVRLocomotion(float dt, float headYaw) {
        if (!ctrlHeld()) return; // arrow keys are cursor navigation unless Ctrl is held

        boolean alt = altHeld();
        if (alt) {
            if (keys[GLFW_KEY_LEFT])  locoYaw += ROTATE_SPEED * dt;
            if (keys[GLFW_KEY_RIGHT]) locoYaw -= ROTATE_SPEED * dt;
        }

        float speed = MOVE_SPEED * dt;
        if (shiftHeld()) speed *= ARROW_BOOST_FACTOR;
        // Movement direction follows the tracked head yaw plus any virtual
        // turn already applied, so it matches what the player currently sees.
        float effectiveYaw = headYaw + locoYaw;
        float sinY = (float) Math.sin(effectiveYaw);
        float cosY = (float) Math.cos(effectiveYaw);

        if (keys[GLFW_KEY_UP])    { locoX -= sinY * speed; locoZ -= cosY * speed; }
        if (keys[GLFW_KEY_DOWN])  { locoX += sinY * speed; locoZ += cosY * speed; }
        if (!alt && keys[GLFW_KEY_LEFT])  { locoX -= cosY * speed; locoZ += sinY * speed; }
        if (!alt && keys[GLFW_KEY_RIGHT]) { locoX += cosY * speed; locoZ -= sinY * speed; }
    }

    private Matrix4f buildDesktopHeadMatrix() {
        // Build head-to-world matrix for the fallback camera
        return new Matrix4f()
            .translate(camX, camY, camZ)
            .rotateY(camYaw)
            .rotateX(camPitch);
    }

    public boolean shouldClose() {
        return glfwWindowShouldClose(window);
    }

    public void destroy() {
        skydome.destroy();
        if (shader != null) shader.destroy();
        if (texturedShader != null) texturedShader.destroy();
        if (leftFbo != null) leftFbo.destroy();
        if (rightFbo != null) rightFbo.destroy();
        if (backdropCapture != null) backdropCapture.destroy();
        if (blurPass != null) blurPass.destroy();
        glfwDestroyWindow(window);
        glfwTerminate();
    }
}
