package com.destan.trafficengine.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.StringUtil;

/** Text limits and number stepping shared by the editor and its packets. */
public final class WritableSignText {
    public static final int MAX_LENGTH = 96;
    public static final int FRONT_LINES = 4;
    public static final int BACK_LINES = 2;

    private WritableSignText() {}

    public static String stepNumber(String text, int amount) {
        int end = 0;
        while (end < text.length() && text.charAt(end) >= '0' && text.charAt(end) <= '9') end++;
        if (end == 0 || end > 18) return text;
        long next = Long.parseLong(text.substring(0, end)) + amount;
        if (next < 0) return text;
        String digits = Long.toString(next);
        String result = "0".repeat(Math.max(0, end - digits.length())) + digits + text.substring(end);
        return result.length() <= MAX_LENGTH ? result : text;
    }

    public static String[] readLines(CompoundTag tag, String key, int limit) {
        ListTag list = tag.getList(key, Tag.TAG_STRING);
        String[] lines = new String[Math.min(list.size(), limit)];
        for (int i = 0; i < lines.length; i++) {
            String line = list.getString(i);
            int end = Math.min(line.length(), MAX_LENGTH);
            if (end > 0 && Character.isHighSurrogate(line.charAt(end - 1))) end--;
            lines[i] = StringUtil.filterText(line.substring(0, end));
        }
        return lines;
    }

    public static void writeLines(CompoundTag tag, String key, String[] lines) {
        ListTag list = new ListTag();
        for (String line : lines) list.add(StringTag.valueOf(line));
        tag.put(key, list);
    }
}
