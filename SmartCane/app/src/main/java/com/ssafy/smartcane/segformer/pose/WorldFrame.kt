package com.ssafy.smartcane.segformer.pose

/**
 * Conventions for the gravity-aligned local coordinate frame.
 *
 * Camera optical frame (back camera, OpenCV style): x=right, y=down, z=forward.
 * The ground plane is the locus of points whose camera-frame coordinate
 * satisfies gravity_cam_unit 쨌 P = cameraHeightM, so projections express
 * distances measured "downward from camera" along the (sensed) gravity vector.
 *
 * Forward / lateral metrics in [GroundProjection] are taken in the
 * gravity-stabilised camera-local frame: forward = optical-axis projected onto
 * the ground plane (positive in the camera's viewing direction), lateral =
 * gravity 횞 forward (positive to the user's right in the upright image).
 */
object WorldFrame {
    const val CAMERA_HEIGHT_M = 1.20f

    const val MIN_CLASS_PIXELS_FOR_PROJECTION = 64

    const val MIN_RAY_DOT_GRAVITY = 0.05f

    const val PROJECTION_PIXEL_STRIDE = 2
}
