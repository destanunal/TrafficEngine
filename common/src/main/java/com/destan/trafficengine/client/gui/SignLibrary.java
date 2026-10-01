package com.destan.trafficengine.client.gui;

import java.util.*;
import java.io.IOException;
import com.destan.trafficengine.TrafficEngine;
import com.destan.trafficengine.block.data.TrafficSignShape;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference;
import com.destan.trafficengine.data.NamedTrafficSignTextureReference.BuildInTrafficSignCodec;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Resource metadata only; the texture manager owns built in images. */
public final class SignLibrary {
    public static final List<TrafficSignShape> CATEGORIES = List.of(TrafficSignShape.CIRCLE, TrafficSignShape.TRIANGLE, TrafficSignShape.SQUARE, TrafficSignShape.DIAMOND, TrafficSignShape.RECTANGLE, TrafficSignShape.MISC);
    private final Map<TrafficSignShape, List<Entry>> metadata = new EnumMap<>(TrafficSignShape.class);
    public record Entry(ResourceLocation texture, NamedTrafficSignTextureReference reference, short width, short height) {}
    public List<Entry> category(TrafficSignShape category) {
        if (category != TrafficSignShape.MISC) return shape(category, true);
        List<Entry> list = new ArrayList<>();
        list.addAll(shape(TrafficSignShape.TRIANGLE_DOWN, true));
        list.addAll(shape(TrafficSignShape.OCTAGON, true));
        for (TrafficSignShape shape : TrafficSignShape.values()) {
            if (shape != TrafficSignShape.TRIANGLE_DOWN && shape != TrafficSignShape.OCTAGON && (!CATEGORIES.contains(shape) || shape == TrafficSignShape.MISC)) list.addAll(shape(shape, true));
        }
        return list;
    }
    public List<Entry> shape(TrafficSignShape shape, boolean catalogue) {
        List<Entry> all = metadata.computeIfAbsent(shape, s -> {
            List<Entry> list = new ArrayList<>();
            var resources = Minecraft.getInstance().getResourceManager();
            String directory = "textures/block/sign/" + s.getShape();
            String prefix = directory + "/" + s.getShape();
            var files = resources.listResources(directory, path -> path.getNamespace().equals(TrafficEngine.MOD_ID) && assetId(path, prefix) > 0);
            // Keep the filename ID: deleted assets must not renumber saved signs
            // or hide the resources after a gap in the sequence.
            for (var resource : files.entrySet().stream().sorted(Comparator.comparingInt(e -> assetId(e.getKey(), prefix))).toList()) {
                ResourceLocation path = resource.getKey();
                int i = assetId(path, prefix);
                if (s == TrafficSignShape.CIRCLE && between(i, 181, 185)) continue;
                try (var input = resource.getValue().open(); NativeImage image = NativeImage.read(input)) {
                    short w = (short)image.getWidth(), h = (short)image.getHeight();
                    list.add(new Entry(path, NamedTrafficSignTextureReference.ofBuildIn("", new BuildInTrafficSignCodec(s, i, w, h)), w, h));
                } catch (IOException e) { TrafficEngine.LOGGER.warn("Cannot read sign metadata {}", path, e); }
            }
            return List.copyOf(list);
        });
        if (!catalogue) return all;
        return all.stream().filter(e -> {
            int id = BuildInTrafficSignCodec.decode(e.reference().getTextureId()).id();
            return !(shape == TrafficSignShape.TRIANGLE && id > 47 || shape == TrafficSignShape.CIRCLE && (between(id,74,112) || between(id,118,122) || between(id,128,132) || between(id,138,141)) || shape == TrafficSignShape.SQUARE && between(id,96,106));
        }).toList();
    }
    private static int assetId(ResourceLocation path, String prefix) {
        String name = path.getPath();
        if (!name.startsWith(prefix) || !name.endsWith(".png")) return -1;
        try {
            int id = Integer.parseInt(name.substring(prefix.length(), name.length() - 4));
            return between(id, 1, 4096) ? id : -1;
        } catch (NumberFormatException e) { return -1; }
    }
    private static boolean between(int n, int a, int b) { return n >= a && n <= b; }
}
