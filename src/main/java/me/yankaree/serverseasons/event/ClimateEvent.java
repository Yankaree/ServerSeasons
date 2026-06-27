package me.yankaree.serverseasons.event;

public enum ClimateEvent {
    BLIZZARD("blizzard", "Blizzard", 1),
    TROPICAL_STORM("tropical_storm", "Tropical Storm", 2),
    FLOOD("flood", "Flood", 3),
    HEATWAVE("heatwave", "Heatwave", 4),
    COLD_SNAP("cold_snap", "Cold Snap", 5),
    SEVERE_THUNDERSTORM("severe_thunderstorm", "Severe Thunderstorm", 6),
    DROUGHT("drought", "Drought", 7);

    private final String id;
    private final String displayName;
    private final int priority; // 1 is highest, 7 is lowest

    ClimateEvent(String id, String displayName, int priority) {
        this.id = id;
        this.displayName = displayName;
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPriority() {
        return priority;
    }

    public static ClimateEvent fromId(String id) {
        for (ClimateEvent e : values()) {
            if (e.id.equalsIgnoreCase(id)) {
                return e;
            }
        }
        return null;
    }
}
