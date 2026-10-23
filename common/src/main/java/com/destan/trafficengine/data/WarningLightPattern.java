package com.destan.trafficengine.data;

public enum WarningLightPattern {
    BLINK("10", "30"), ALTERNATE("10", "12"), DOUBLE("101000", "101020200000");
    private final String single, dual;
    WarningLightPattern(String single, String dual) { this.single = single; this.dual = dual; }
    public String steps(int lamps) { return lamps == 1 ? single : dual; }
    public String key() { return "gui.trafficengine.warning.pattern." + name().toLowerCase(java.util.Locale.ROOT); }
    public static WarningLightPattern find(String steps, int lamps) {
        for (WarningLightPattern pattern : values()) if (pattern.steps(lamps).equals(steps)) return pattern;
        if (lamps == 2 && "1020".equals(steps)) return ALTERNATE;
        if (lamps == 2 && "10102020".equals(steps)) return DOUBLE;
        if (lamps == 1 && ("1020".equals(steps) || "12".equals(steps))) return BLINK;
        return null;
    }
}
