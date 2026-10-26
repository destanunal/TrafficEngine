package com.destan.trafficengine.util;

import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Utils {
    private static double calcScale(double minScale, double maxScale, double maxWidth, double fontWidth) {
        double scale = Math.min(maxWidth / fontWidth, 1.0D);
        return Math.max(maxScale * scale, minScale);
    }

    public static double getScale(float fontWidth, float lineWidth, float min, float max) {
        return calcScale(min, max, lineWidth / max, fontWidth);
    }   
    
    public static byte[] compress(byte[] data, int level) throws Exception {
        Deflater deflater = new Deflater(level);
        deflater.setInput(data);
        deflater.finish();
    
        byte[] buffer = new byte[1024];
        int compressedDataLength;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
            while (!deflater.finished()) {
                compressedDataLength = deflater.deflate(buffer);
                outputStream.write(buffer, 0, compressedDataLength);
            }
            return outputStream.toByteArray();
        } finally { deflater.end(); }
    }

    public static byte[] decompress(byte[] data) throws Exception {
        Inflater inflater = new Inflater();
        inflater.setInput(data);

        byte[] buffer = new byte[1024];
        int decompressedDataLength;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(data.length, 8192))) {
            while (!inflater.finished()) {
                decompressedDataLength = inflater.inflate(buffer);
                if (decompressedDataLength == 0 && !inflater.finished())
                    throw new java.util.zip.DataFormatException("Incomplete or stalled compressed data");
                if (outputStream.size() + decompressedDataLength > 1024 * 1024)
                    throw new java.util.zip.DataFormatException("Decompressed data exceeds 1 MiB");
                outputStream.write(buffer, 0, decompressedDataLength);
            }
            return outputStream.toByteArray();
        } finally { inflater.end(); }
    }
}
