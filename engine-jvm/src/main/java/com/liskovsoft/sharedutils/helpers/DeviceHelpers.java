package com.liskovsoft.sharedutils.helpers;

import android.content.Context;

/** JVM substitute for SmartTube's Android device capabilities. */
public final class DeviceHelpers {
    public enum ArchType {
        ARM_V7, ARM_64, X86, X86_64, MIPS, UNKNOWN
    }

    private DeviceHelpers() {
    }

    public static boolean isWebViewSupported() {
        // The desktop engine mints poTokens with GraalJS, not a WebView.
        return true;
    }

    public static String getPrimaryAbi() {
        return "x86_64";
    }

    public static ArchType getArchType() {
        return ArchType.X86_64;
    }

    public static int getMaxHeapMemoryMB() {
        return (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    public static int getAllocatedHeapMemoryMB() {
        return (int) (Runtime.getRuntime().totalMemory() / (1024 * 1024));
    }

    public static boolean isMemoryCritical() {
        return getAllocatedHeapMemoryMB() > getMaxHeapMemoryMB() * 0.5;
    }

    public static boolean isVP9Supported() {
        return true;
    }

    public static boolean isAV1Supported() {
        return true;
    }

    public static boolean isVP9ResolutionSupported(int height) {
        return height > 0 && height <= 2160;
    }

    public static boolean isAV1ResolutionSupported(int height) {
        return height > 0 && height <= 2160;
    }

    public static boolean isHardwareAccelerated(String videoCodecName) {
        return false;
    }

    public static long getDeviceRam(Context context) {
        return Runtime.getRuntime().maxMemory();
    }

    public static boolean shouldSupportMediaTunneling() {
        return false;
    }

    public static boolean isConfirmKey(int keyCode) {
        return false;
    }
}
