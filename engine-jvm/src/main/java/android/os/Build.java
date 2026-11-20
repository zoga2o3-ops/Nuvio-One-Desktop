package android.os;

public final class Build {
    public static final String MANUFACTURER = "Google";
    public static final String BRAND = "google";
    public static final String HARDWARE = "desktop";
    public static final String ID = "AP4A.250105.002";
    public static final String MODEL = "Nuvio Desktop";
    public static final String DEVICE = "desktop";
    public static final String PRODUCT = "nuvio_desktop";
    public static final String CPU_ABI = "x86_64";
    public static final String[] SUPPORTED_ABIS = {"x86_64"};

    private Build() {
    }

    public static final class VERSION {
        public static final int SDK_INT = 35;
        public static final String RELEASE = "15";

        private VERSION() {
        }
    }
}
