package com.destan.trafficengine.block.data;

public enum LedDeviceType {
    TRAFFIC_DISPLAY(2, 4),
    LARGE_TRAFFIC_DISPLAY(3, 6),
    LED_LIGHT(1, 0);

    private final int displaySize;
    private final int messageSlots;

    LedDeviceType(int displaySize, int messageSlots) {
        this.displaySize = displaySize;
        this.messageSlots = messageSlots;
    }

    public boolean isTrafficDisplay() { return this != LED_LIGHT; }
    public int getDisplaySize() { return displaySize; }
    public int getMessageSlots() { return messageSlots; }
}
