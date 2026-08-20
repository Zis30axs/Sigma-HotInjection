package dev.zis30axs.sigma.hotinjection.version;

public enum MinecraftVersion {
    V1_7_10("1.7.10"),
    V1_8_9("1.8.9"),
    V1_20_1("1.20.1"),
    V1_21_11("1.21.11"),
    V26_2("26.2"),
    UNKNOWN("unknown");

    private final String id;

    MinecraftVersion(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static MinecraftVersion fromId(String id) {
        if (id != null) {
            for (MinecraftVersion version : values()) {
                if (version.id.equalsIgnoreCase(id.trim())) {
                    return version;
                }
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return id;
    }
}
