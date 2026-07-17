package de.e_nexus.vrplatform.scenecore;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Content a bundle can publish into the framework's already-running VR
 * session (see {@code de.e_nexus.vrplatform.render.SceneRegistry}), on top
 * of the skydome the renderer always shows on its own. A consumer (e.g. the
 * demo bundle) implements this once for whatever it wants visible, then
 * hands an instance to the registry's {@code publish}/{@code unpublish} as
 * its own lifecycle starts and stops -- the renderer itself, the window, and
 * the skydome keep running throughout, unaffected.
 */
public interface VRSceneContribution {

    /** Called by the registry right before this contribution is added; build GL resources here. */
    void initialize();

    void updateFocus(Vector3f headPosition, Vector3f gazeDirection);

    void updateCheckboxAim(Vector3f headPosition, Vector3f gazeDirection, boolean altGrHeld, float dt);

    /**
     * Cylinder dials are turned by looking away, not by aiming precisely
     * while dragging: on the frame Alt-Gr goes down, whichever dial (if any)
     * is aimed at gets "grabbed"; while Alt-Gr stays held, every subsequent
     * frame's change in gaze yaw (independent of where the head is now
     * pointed) turns that same dial, exactly like turning a real knob you're
     * still holding after looking away from it. Releasing Alt-Gr lets go.
     */
    void updateCylinderAim(Vector3f headPosition, Vector3f gazeDirection, boolean altGrHeld, float dt);

    void updateTerminals();

    void handleTypedChar(char c);

    void handleBackspace();

    void handleDelete();

    void handleNewLine();

    void handleCursorLeft();

    void handleCursorRight();

    void handleCursorUp();

    void handleCursorDown();

    void handleHome();

    void handleEnd();

    void handleExtendLeft();

    void handleExtendRight();

    void handleExtendUp();

    void handleExtendDown();

    void handleExtendHome();

    void handleExtendEnd();

    void handleCopy();

    void handleCut();

    void handlePaste();

    /** Flat-shaded opaque geometry; sunDirection is already set on {@code shader} by the caller. */
    void render(ShaderProgram shader);

    /** Textured opaque geometry; sunDirection is already set on {@code texturedShader} by the caller. */
    void renderTextured(ShaderProgram texturedShader);

    void renderText(Matrix4f proj, Matrix4f view, int backdropTexture, int viewportWidth, int viewportHeight);

    /** Called by the registry right after this contribution is removed; release GL resources here. */
    void destroy();
}
