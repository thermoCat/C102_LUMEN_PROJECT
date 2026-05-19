package com.ssafy.smartcane.segformer.pose

/**
 * Rigid mount geometry between the phone back camera and the externally-mounted
 * DJI Osmo Action 4.
 *
 * The IMU lives inside the phone, so the gravity vector reported by
 * [OrientationProvider] is initially expressed in the *phone back camera*
 * optical frame (x=right, y=down, z=forward). When the Osmo is used as the
 * imaging source, downstream geometry ([GroundPlaneProjector],
 * [ImuSegWorldPoseProvider]) needs gravity in the *Osmo* optical frame
 * instead. That requires a single fixed 3x3 rotation
 *
 *     v_osmo = R_PHONE_TO_OSMO * v_phone
 *
 * which is fully determined by how you physically mount the Osmo to the phone.
 *
 * ## How to measure R_PHONE_TO_OSMO
 *
 * Recommended (one-time, ~5 min):
 *   1. Mount the phone+Osmo rig vertically on a level surface (table edge) so
 *      both optical axes point at a known target.
 *   2. Capture one frame from each camera with a vertical plumb line in view.
 *   3. Run the small calibration helper in `tools/measure_mount_rotation.py`
 *      (TODO: add when the rig exists) which prints the 3x3 matrix to paste
 *      below.
 *
 * Quick approximations for common mounts:
 *   - Osmo on top of phone, both in landscape, optical axes parallel: identity.
 *   - Osmo rotated 90째 clockwise relative to phone (looking from behind):
 *       [ 0, -1,  0,
 *         1,  0,  0,
 *         0,  0,  1 ]
 *   - Osmo rotated 180째 (upside-down):
 *       [-1,  0,  0,
 *         0, -1,  0,
 *         0,  0,  1 ]
 *
 * The default below is identity ??*replace it* once the physical rig is set,
 * otherwise the ground-plane distance readings will be tilted by the mount
 * misalignment.
 */
object OsmoMount {
    /**
     * 3x3 row-major rotation: phone back-camera optical frame -> Osmo optical frame.
     * Identity by default. UPDATE THIS after rigidly mounting the Osmo.
     */
    val R_PHONE_TO_OSMO: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f,
    )
}
