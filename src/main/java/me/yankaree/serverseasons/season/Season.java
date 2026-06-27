package me.yankaree.serverseasons.season;

public enum Season {
    SPRING("spring", "Spring", "\uD83C\uDF21"),
    SUMMER("summer", "Summer", "\u2600\uFE0F"),
    AUTUMN("autumn", "Autumn", "\uD83C\uDF42"),
    WINTER("winter", "Winter", "\u2744\uFE0F");

    private final String id;
    private final String displayName;
    private final String defaultIcon;

    Season(String id, String displayName, String defaultIcon) {
        this.id = id;
        this.displayName = displayName;
        this.defaultIcon = defaultIcon;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultIcon() {
        return defaultIcon;
    }

    public static Season fromId(String id) {
        for (Season s : values()) {
            if (s.id.equalsIgnoreCase(id)) {
                return s;
            }
        }
        return SPRING;
    }
}
