package de.e_nexus.vrplatform.vr;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.openvr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRCompositor.*;
import static org.lwjgl.openvr.VRSystem.*;

public class HMDDevice {

    private boolean vrAvailable = false;
    private int recommendedWidth = 1920;
    private int recommendedHeight = 1080;
    private TrackedDevicePose.Buffer renderPoses;

    // Yaw (heading) the user was facing when the first valid pose arrived;
    // subtracted from every head pose so that direction becomes "forward".
    private boolean yawRecentered = false;
    private float yawOffset = 0f;

    public void initialize() {
        IntBuffer error = MemoryUtil.memAllocInt(1);
        try {
            long token = VR_InitInternal(error, EVRApplicationType_VRApplication_Scene);
            if (error.get(0) != EVRInitError_VRInitError_None) {
                System.out.println("OpenVR unavailable: " + VR_GetVRInitErrorAsEnglishDescription(error.get(0)));
                System.out.println("Running in desktop fallback mode (Ctrl+Arrows to move, right-mouse-drag to look).");
                return;
            }
            OpenVR.create((int) token);
            vrAvailable = true;

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                VRSystem_GetRecommendedRenderTargetSize(w, h);
                recommendedWidth = w.get(0);
                recommendedHeight = h.get(0);
            }

            renderPoses = TrackedDevicePose.create(k_unMaxTrackedDeviceCount);
            System.out.printf("VR initialized. Render target per eye: %dx%d%n", recommendedWidth, recommendedHeight);
        } finally {
            MemoryUtil.memFree(error);
        }
    }

    public void beginFrame() {
        if (!vrAvailable) return;
        VRCompositor_WaitGetPoses(renderPoses, null);
    }

    public Matrix4f getHeadMatrix() {
        if (!vrAvailable || renderPoses == null) return new Matrix4f();
        TrackedDevicePose head = renderPoses.get(k_unTrackedDeviceIndex_Hmd);
        if (!head.bPoseIsValid()) return new Matrix4f();
        Matrix4f raw = convertMatrix34(head.mDeviceToAbsoluteTracking());

        if (!yawRecentered) {
            yawOffset = extractYaw(raw);
            yawRecentered = true;
        }

        return new Matrix4f().rotateY(-yawOffset).mul(raw);
    }

    public static float extractYaw(Matrix4f headMatrix) {
        Vector3f forward = headMatrix.transformDirection(new Vector3f(0f, 0f, -1f));
        return (float) Math.atan2(-forward.x, -forward.z);
    }

    public Matrix4f getProjectionMatrix(int eye) {
        if (!vrAvailable) {
            float aspect = (float) recommendedWidth / recommendedHeight;
            return new Matrix4f().perspective((float) Math.toRadians(110), aspect, 0.05f, 100f);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            HmdMatrix44 m = HmdMatrix44.malloc(stack);
            VRSystem_GetProjectionMatrix(eye, 0.05f, 100f, m);
            return convertMatrix44(m);
        }
    }

    public Matrix4f getEyeView(int eye, Matrix4f headMatrix) {
        Matrix4f eyeOffset;
        if (!vrAvailable) {
            float ipd = (eye == EVREye_Eye_Left) ? -0.032f : 0.032f;
            eyeOffset = new Matrix4f().translate(ipd, 0f, 0f);
        } else {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                HmdMatrix34 m = HmdMatrix34.malloc(stack);
                VRSystem_GetEyeToHeadTransform(eye, m);
                eyeOffset = convertMatrix34(m);
            }
        }
        // view = inverse(headMatrix * eyeOffset)
        return new Matrix4f(headMatrix).mul(eyeOffset).invert();
    }

    public void submitFrame(int eye, int colorTexture) {
        if (!vrAvailable) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Texture tex = Texture.malloc(stack)
                .handle(colorTexture)
                .eType(ETextureType_TextureType_OpenGL)
                .eColorSpace(EColorSpace_ColorSpace_Gamma);
            VRCompositor_Submit(eye, tex, null, EVRSubmitFlags_Submit_Default);
        }
    }

    public void pollEvents() {
        if (!vrAvailable) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VREvent event = VREvent.malloc(stack);
            while (VRSystem_PollNextEvent(event, VREvent.SIZEOF)) {
                if (event.eventType() == EVREventType_VREvent_Quit) {
                    System.out.println("VR quit requested.");
                    VR_ShutdownInternal();
                    vrAvailable = false;
                    return;
                }
            }
        }
    }

    public void shutdown() {
        if (vrAvailable) {
            VR_ShutdownInternal();
        }
    }

    // ---- matrix conversion (OpenVR row-major → JOML column-major) ----

    private Matrix4f convertMatrix44(HmdMatrix44 m) {
        FloatBuffer b = m.m();
        return new Matrix4f(
            b.get(0),  b.get(4),  b.get(8),  b.get(12),
            b.get(1),  b.get(5),  b.get(9),  b.get(13),
            b.get(2),  b.get(6),  b.get(10), b.get(14),
            b.get(3),  b.get(7),  b.get(11), b.get(15)
        );
    }

    private Matrix4f convertMatrix34(HmdMatrix34 m) {
        FloatBuffer b = m.m();
        return new Matrix4f(
            b.get(0), b.get(4), b.get(8),  0f,
            b.get(1), b.get(5), b.get(9),  0f,
            b.get(2), b.get(6), b.get(10), 0f,
            b.get(3), b.get(7), b.get(11), 1f
        );
    }

    public boolean isVRAvailable()    { return vrAvailable; }
    public int getRecommendedWidth()  { return recommendedWidth; }
    public int getRecommendedHeight() { return recommendedHeight; }
}
