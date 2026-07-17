package de.e_nexus.vrplatform.demo.scene;

import de.e_nexus.vrplatform.gl.ShaderProgram;
import de.e_nexus.vrplatform.gl.Texture2D;
import de.e_nexus.vrplatform.checkbox.Checkbox;
import de.e_nexus.vrplatform.checkbox.CheckboxState;
import de.e_nexus.vrplatform.cube.Cube;
import de.e_nexus.vrplatform.cuboidframe.CuboidFrame;
import de.e_nexus.vrplatform.cylinder.Cylinder;
import de.e_nexus.vrplatform.scenecore.SceneObject;
import de.e_nexus.vrplatform.terminal.Terminal;
import de.e_nexus.vrplatform.scenecore.TextPanel;
import de.e_nexus.vrplatform.texturedcube.TexturedCube;
import de.e_nexus.vrplatform.texturedcube.TexturedSceneObject;
import de.e_nexus.vrplatform.scenecore.TypeableTextPanel;
import de.e_nexus.vrplatform.scenecore.VRSceneContribution;
import de.e_nexus.vrplatform.scenecore.text.PanelTheme;
import de.e_nexus.vrplatform.scenecore.text.SingleLineTextContent;
import de.e_nexus.vrplatform.scenecore.text.TypedTextDecoration;
import de.e_nexus.vrplatform.tilelist.Tile;
import de.e_nexus.vrplatform.tilelist.TileList;
import jakarta.inject.Inject;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Everything the demo bundle contributes to the framework's already-running
 * VR session (see {@link VRSceneContribution}) -- the skydome and sun are
 * the renderer's own concern now, not this bundle's.
 *
 * <p>A CDI-managed bean (default {@code @Dependent} scope -- no proxying, so
 * no dynamically generated subclass needs unpredictable OSGi imports), added
 * explicitly to the Weld SE container instead of relying on classpath
 * scanning (see DemoBeans for why: it doesn't reliably enumerate classes
 * from inside an OSGi bundle).
 */
public class VRScene implements VRSceneContribution {

	private final List<SceneObject> objects = new ArrayList<>();
	private final List<TexturedSceneObject> texturedObjects = new ArrayList<>();
	private final List<TextPanel> textPanels = new ArrayList<>();
	private final List<TypeableTextPanel> editablePanels = new ArrayList<>();
	private final List<Checkbox> checkboxes = new ArrayList<>();
	private final LoremIpsumPanel loremIpsumPanel;
	private final PanelTheme theme;
	private final TypedTextDecoration decoration;
	private final Texture2D darkwoodTexture = new Texture2D(VRScene.class, "/darkwood.png");

	// A panel gains focus only while the headset is within this distance
	// band of it; among panels in range, the one looked at most directly wins.
	private static final float FOCUS_MIN_DISTANCE = 0.15f; // 10x smaller than the original 1.5f
	private static final float FOCUS_MAX_DISTANCE = 8.0f;
	private TypeableTextPanel focusedPanel;

	// Checkboxes use a separate hold-to-activate gesture, not keyboard focus:
	// aim dead-center at one from 1-10m, hold AltGr >= 150ms to light it up,
	// release while still aimed at it to toggle. While one is lit, every
	// other component (text-field keyboard focus) loses focus.
	private static final float AIM_MIN_DISTANCE = 1.0f;
	private static final float AIM_MAX_DISTANCE = 10.0f;
	private static final float AIM_MAX_ANGLE_COS = 0.99f; // ~8 degrees off dead-center
	private static final float ALTGR_HOLD_THRESHOLD = 0.15f; // seconds
	private Checkbox armedCheckbox;
	private float altGrHoldTime = 0f;
	private boolean altGrWasHeld = false;

	// Cylinder dials share the same aim thresholds as checkboxes (see
	// updateCylinderAim) but no hold-to-arm delay: AltGr grabs on the same
	// frame it's pressed, since the gesture is "grab, then turn," not "aim
	// and wait."
	private Cylinder<?> armedCylinder;
	private float armedCylinderYaw;

	private Terminal terminal;
	private Cylinder<Float> volumeDial;

	// Competes with editablePanels for the same look-based focus (see
	// updateFocus): while it wins, Left/Right steps its selection instead of
	// moving a text caret (see handleCursorLeft/handleCursorRight).
	private TileList homeDirectoryList;
	private boolean tileListFocused;

	@Inject
	public VRScene(LoremIpsumPanel loremIpsumPanel, PanelTheme theme, TypedTextDecoration decoration) {
		this.loremIpsumPanel = loremIpsumPanel;
		this.theme = theme;
		this.decoration = decoration;
	}

	@Override
	public void initialize() {
		darkwoodTexture.initialize();
		texturedObjects.add(
				TexturedCube.create(darkwoodTexture, 2f).position(0f, -0.35f, -4f).scale(0.9f, 0.9f, 0.9f));

		// Floor platform
		objects.add(Cube.create(0.45f, 0.45f, 0.45f).position(0f, -1.1f, 0f).scale(12f, 0.1f, 12f));

		// Three coloured cubes at eye level
		objects.add(Cube.create(0.90f, 0.20f, 0.20f).position(-1.5f, 0f, -3f));

		objects.add(Cube.create(0.20f, 0.85f, 0.20f).position(0f, 0f, -3f));

		objects.add(Cube.create(0.20f, 0.40f, 0.95f).position(1.5f, 0f, -3f));

		// Small yellow cube floating above
		objects.add(
				Cube.create(0.95f, 0.90f, 0.15f).position(0f, 1.5f, -3.5f).scale(0.45f, 0.45f, 0.45f).rotateY(0.785f));

		// Two grey pillars forming a gateway
		objects.add(Cube.create(0.65f, 0.65f, 0.75f).position(-1.2f, 0.5f, -5f).scale(0.3f, 3.2f, 0.3f));

		objects.add(Cube.create(0.65f, 0.65f, 0.75f).position(1.2f, 0.5f, -5f).scale(0.3f, 3.2f, 0.3f));

		// Lintel connecting the two pillars
		objects.add(Cube.create(0.65f, 0.65f, 0.75f).position(0f, 2.05f, -5f).scale(2.7f, 0.3f, 0.3f));

		// Background wall cubes to give depth cues
		for (int i = -3; i <= 3; i++) {
			objects.add(
					Cube.create(0.3f + i * 0.05f, 0.2f, 0.5f).position(i * 1.5f, -0.5f, -8f).scale(0.6f, 0.6f, 0.6f));
		}

		// Wireframe cage: 2m x 2m x 2m cuboid frame, 2cm bars
		CuboidFrame frame = new CuboidFrame(2f, 2f, 2f, 0.02f, 0.75f, 0.75f, 0.8f);
		objects.add(frame.mesh().position(0f, 1f, -6.5f));

		// Rotatable volume dial: 0..10, turned with Alt-Gr
		volumeDial = new Cylinder<>(2.6f, 1.6f, -2.2f, Cylinder.Direction.VERTICAL, 0.2f, 0.35f, 24,
				0.8f, 0.6f, 0.2f, 5f, 0f, 10f, theme, decoration);
		volumeDial.initialize();
		objects.add(volumeDial.mesh());
		textPanels.add(volumeDial.label());

		// Static showcase panel: three Lorem Ipsum paragraphs in ten font sizes.
		loremIpsumPanel.initialize();
		textPanels.add(loremIpsumPanel);

		// The sample text's third word ("dolor") becomes the standard font
		// size for every panel constructed below (and any other panel that
		// doesn't ask for a specific size of its own) -- doubled per request.
		theme.setFontSize(loremIpsumPanel.thirdWordFontSize() * 2);

		// Demo tri-state checkbox: aim dead-center and hold AltGr to light it up,
		// release while still aimed at it to toggle (see updateCheckboxAim).
		Checkbox checkbox = new Checkbox(2.6f, 0f, -2.2f, theme, new SingleLineTextContent(), decoration,
				"Demo Checkbox", CheckboxState.UNCHECKED);
		checkbox.initialize();
		textPanels.add(checkbox);
		checkboxes.add(checkbox);

		// Real SSH terminal (see Terminal), off to the side of the demo fields,
		// displayed inside its own wireframe cage: the cage's layout tree is
		// just the terminal's single grid panel, so the two aren't just
		// visually co-located but actually linked through the same
		// PanelComponent/FrameElement model CuboidFrame exposes.
		float terminalX = -3.6f, terminalY = 0.8f, terminalZ = -2.2f;
		terminal = new Terminal(terminalX, terminalY, terminalZ, theme);
		terminal.initialize();
		textPanels.add(terminal.panel());
		editablePanels.add(terminal.panel());

		CuboidFrame terminalFrame = new CuboidFrame(1.3f, 1.3f, 0.4f, 0.02f, 0.75f, 0.75f, 0.8f);
		terminalFrame.setTree(terminal.panel());
		objects.add(terminalFrame.mesh().position(terminalX, terminalY, terminalZ));

		// Browses the current user's home directory: one tile per entry, name
		// only (no per-type icons yet, so every tile shows the default "?").
		homeDirectoryList = new TileList(4.2f, 0.9f, -2.2f, 1.2f, 0.9f, 0.3f, theme);
		homeDirectoryList.initialize();
		homeDirectoryList.setTiles(listHomeDirectory());
	}

	private static List<Tile> listHomeDirectory() {
		File home = new File(System.getProperty("user.home"));
		File[] entries = home.listFiles();
		if (entries == null) {
			return List.of();
		}
		Arrays.sort(entries, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
		List<Tile> tiles = new ArrayList<>(entries.length);
		for (File entry : entries) {
			tiles.add(new Tile(entry.getName()));
		}
		return tiles;
	}

	/**
	 * Call once per frame with the headset's world position and (normalized) gaze
	 * direction. Focuses the in-range editable panel looked at most directly; if
	 * none qualify, nothing has focus and typing is ignored. While a checkbox is
	 * lit up (see {@link #updateCheckboxAim}), no panel may hold focus.
	 */
	@Override
	public void updateFocus(Vector3f headPosition, Vector3f gazeDirection) {
		TypeableTextPanel best = null;
		float bestAlignment = 0f; // must face at least somewhat toward the panel
		boolean listWins = false;

		if (armedCheckbox == null) {
			for (TypeableTextPanel panel : editablePanels) {
				Vector3f toPanel = panel.position().toVector3f().sub(headPosition, new Vector3f());
				float distance = toPanel.length();
				if (distance < FOCUS_MIN_DISTANCE || distance > FOCUS_MAX_DISTANCE) {
					continue;
				}
				float alignment = toPanel.normalize().dot(gazeDirection);
				if (alignment > bestAlignment) {
					bestAlignment = alignment;
					best = panel;
				}
			}

			// The tile list competes for the same focus using the same
			// thresholds, even though it's not a TypeableTextPanel: winning
			// clears any panel found above rather than stacking with it.
			Vector3f toList = homeDirectoryList.position().toVector3f().sub(headPosition, new Vector3f());
			float listDistance = toList.length();
			if (listDistance >= FOCUS_MIN_DISTANCE && listDistance <= FOCUS_MAX_DISTANCE) {
				float listAlignment = toList.normalize().dot(gazeDirection);
				if (listAlignment > bestAlignment) {
					bestAlignment = listAlignment;
					best = null;
					listWins = true;
				}
			}
		}

		if (best != focusedPanel) {
			if (focusedPanel != null) {
				focusedPanel.setFocused(false);
			}
			if (best != null) {
				best.setFocused(true);
			}
			focusedPanel = best;
		}
		tileListFocused = listWins;

		// Independent of list-level focus above: whichever single tile the
		// gaze ray actually lands on dead-on gets its own outline (see
		// TileList.hitTestTile), regardless of distance/alignment thresholds.
		homeDirectoryList.setAimedTileIndex(homeDirectoryList.hitTestTile(headPosition, gazeDirection));
	}

	/**
	 * Call once per frame with the headset's world position, (normalized) gaze
	 * direction, whether AltGr is currently held, and the frame delta time.
	 * Lights up the checkbox dead-center in view (1-10m away) once AltGr has
	 * been held for at least {@link #ALTGR_HOLD_THRESHOLD}; releasing AltGr
	 * while still aimed at that same checkbox toggles it.
	 */
	@Override
	public void updateCheckboxAim(Vector3f headPosition, Vector3f gazeDirection, boolean altGrHeld, float dt) {
		Checkbox aimed = findAimedCheckbox(headPosition, gazeDirection);

		if (altGrHeld) {
			if (!altGrWasHeld) {
				altGrHoldTime = 0f;
			}
			altGrHoldTime += dt;

			Checkbox shouldLight = (altGrHoldTime >= ALTGR_HOLD_THRESHOLD) ? aimed : null;
			if (shouldLight != armedCheckbox) {
				if (armedCheckbox != null) {
					armedCheckbox.setFocused(false);
				}
				armedCheckbox = shouldLight;
				if (armedCheckbox != null) {
					armedCheckbox.setFocused(true);
					// Lighting up a checkbox takes priority immediately, rather
					// than waiting for next frame's updateFocus to notice.
					if (focusedPanel != null) {
						focusedPanel.setFocused(false);
						focusedPanel = null;
					}
				}
			}
		} else {
			if (altGrWasHeld && armedCheckbox != null && aimed == armedCheckbox) {
				armedCheckbox.toggle();
			}
			if (armedCheckbox != null) {
				armedCheckbox.setFocused(false);
				armedCheckbox = null;
			}
			altGrHoldTime = 0f;
		}

		altGrWasHeld = altGrHeld;
	}

	private Checkbox findAimedCheckbox(Vector3f headPosition, Vector3f gazeDirection) {
		Checkbox best = null;
		float bestAlignment = AIM_MAX_ANGLE_COS; // must be aimed dead-center, not just "closest of several"

		for (Checkbox box : checkboxes) {
			Vector3f toBox = box.position().toVector3f().sub(headPosition, new Vector3f());
			float distance = toBox.length();
			if (distance < AIM_MIN_DISTANCE || distance > AIM_MAX_DISTANCE) {
				continue;
			}
			float alignment = toBox.normalize().dot(gazeDirection);
			if (alignment > bestAlignment) {
				bestAlignment = alignment;
				best = box;
			}
		}
		return best;
	}

	/**
	 * Call once per frame with the headset's world position, (normalized) gaze
	 * direction, whether AltGr is currently held, and the frame delta time.
	 * The frame AltGr goes down, whichever dial is aimed at (1-10m away,
	 * dead-center, same thresholds as a checkbox) is grabbed; every frame
	 * after that while AltGr stays held, the change in gaze yaw since the
	 * previous frame turns that dial, regardless of where the head is
	 * pointed by then -- see {@link VRSceneContribution#updateCylinderAim}.
	 * Yields to an already-armed checkbox instead of grabbing at the same time.
	 */
	@Override
	public void updateCylinderAim(Vector3f headPosition, Vector3f gazeDirection, boolean altGrHeld, float dt) {
		float currentYaw = (float) Math.atan2(-gazeDirection.x, -gazeDirection.z);

		if (!altGrHeld) {
			if (armedCylinder != null) {
				armedCylinder.setFocused(false);
				armedCylinder = null;
			}
			return;
		}

		if (armedCylinder == null) {
			if (armedCheckbox == null) {
				armedCylinder = findAimedCylinder(headPosition, gazeDirection);
				if (armedCylinder != null) {
					armedCylinder.setFocused(true);
				}
			}
			armedCylinderYaw = currentYaw;
			return; // grabbed (or not) this frame; turning starts next frame
		}

		// Not negated. Traced through the actual numbers this time: Ctrl+Alt+Left
		// decreases camYaw/locoYaw, which decreases currentYaw by the same amount
		// (extractYaw's atan2 convention tracks it 1:1), making this delta
		// negative -- fed straight into rotate(), that decreases the value,
		// which is "turned left" (lower = left, higher = right, like a real
		// knob). The negated version increased the value on a Left press,
		// i.e. acted like a Right turn -- exactly the reported bug.
		armedCylinder.rotate(normalizeAngle(currentYaw - armedCylinderYaw));
		armedCylinderYaw = currentYaw;
	}

	// Only volumeDial exists today; a real multi-dial scene would keep a
	// List<Cylinder<?>> here the same way checkboxes are tracked above.
	private Cylinder<?> findAimedCylinder(Vector3f headPosition, Vector3f gazeDirection) {
		if (volumeDial == null) {
			return null;
		}
		Vector3f toDial = volumeDial.position().sub(headPosition, new Vector3f());
		float distance = toDial.length();
		if (distance < AIM_MIN_DISTANCE || distance > AIM_MAX_DISTANCE) {
			return null;
		}
		float alignment = toDial.normalize().dot(gazeDirection);
		return alignment >= AIM_MAX_ANGLE_COS ? volumeDial : null;
	}

	private static float normalizeAngle(float angle) {
		while (angle > Math.PI) angle -= 2f * Math.PI;
		while (angle < -Math.PI) angle += 2f * Math.PI;
		return angle;
	}

	@Override
	public void handleTypedChar(char c) {
		if (focusedPanel != null) {
			focusedPanel.handleChar(c);
		}
	}

	@Override
	public void handleBackspace() {
		if (focusedPanel != null) {
			focusedPanel.handleBackspace();
		}
	}

	@Override
	public void handleDelete() {
		if (focusedPanel != null) {
			focusedPanel.handleDelete();
		}
	}

	@Override
	public void handleNewLine() {
		if (focusedPanel != null) {
			focusedPanel.handleNewLine();
		}
	}

	@Override
	public void handleCursorLeft() {
		if (tileListFocused) {
			homeDirectoryList.selectPrevious();
		} else if (focusedPanel != null) {
			focusedPanel.handleCursorLeft();
		}
	}

	@Override
	public void handleCursorRight() {
		if (tileListFocused) {
			homeDirectoryList.selectNext();
		} else if (focusedPanel != null) {
			focusedPanel.handleCursorRight();
		}
	}

	@Override
	public void handleCursorUp() {
		if (focusedPanel != null) {
			focusedPanel.handleCursorUp();
		}
	}

	@Override
	public void handleCursorDown() {
		if (focusedPanel != null) {
			focusedPanel.handleCursorDown();
		}
	}

	@Override
	public void handleHome() {
		if (focusedPanel != null) {
			focusedPanel.handleHome();
		}
	}

	@Override
	public void handleEnd() {
		if (focusedPanel != null) {
			focusedPanel.handleEnd();
		}
	}

	@Override
	public void handleExtendLeft() {
		if (focusedPanel != null) {
			focusedPanel.handleExtendLeft();
		}
	}

	@Override
	public void handleExtendRight() {
		if (focusedPanel != null) {
			focusedPanel.handleExtendRight();
		}
	}

	@Override
	public void handleExtendUp() {
		if (focusedPanel != null) {
			focusedPanel.handleExtendUp();
		}
	}

	@Override
	public void handleExtendDown() {
		if (focusedPanel != null) {
			focusedPanel.handleExtendDown();
		}
	}

	@Override
	public void handleExtendHome() {
		if (focusedPanel != null) {
			focusedPanel.handleExtendHome();
		}
	}

	@Override
	public void handleExtendEnd() {
		if (focusedPanel != null) {
			focusedPanel.handleExtendEnd();
		}
	}

	@Override
	public void handleCopy() {
		if (focusedPanel != null) {
			focusedPanel.handleCopy();
		}
	}

	@Override
	public void handleCut() {
		if (focusedPanel != null) {
			focusedPanel.handleCut();
		}
	}

	@Override
	public void handlePaste() {
		if (focusedPanel != null) {
			focusedPanel.handlePaste();
		}
	}

	/** Call once per frame: drains any output a running terminal command has produced. */
	@Override
	public void updateTerminals() {
		if (terminal != null) {
			terminal.update();
		}
	}

	@Override
	public void render(ShaderProgram shader) {
		for (SceneObject obj : objects) {
			obj.render(shader);
		}
	}

	@Override
	public void renderTextured(ShaderProgram shader) {
		for (TexturedSceneObject obj : texturedObjects) {
			obj.render(shader);
		}
	}

	@Override
	public void renderText(Matrix4f proj, Matrix4f view, int backdropTexture, int viewportWidth, int viewportHeight) {
		for (TextPanel panel : textPanels) {
			panel.render(proj, view, backdropTexture, viewportWidth, viewportHeight);
		}
		homeDirectoryList.render(proj, view, backdropTexture, viewportWidth, viewportHeight);
	}

	@Override
	public void destroy() {
		if (terminal != null) {
			terminal.destroy();
		}
		for (SceneObject obj : objects) {
			obj.destroy();
		}
		objects.clear();
		for (TexturedSceneObject obj : texturedObjects) {
			obj.destroy();
		}
		texturedObjects.clear();
		darkwoodTexture.destroy();
		for (TextPanel panel : textPanels) {
			panel.destroy();
		}
		textPanels.clear();
		homeDirectoryList.destroy();
	}
}
