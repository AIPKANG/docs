package com.team.blog.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * 테스트용 이미지 바이트(003 T208). PNG·JPEG는 ImageIO로 만들고, WebP는 서버가 머리말만 읽으므로 RIFF/WEBP + 머리말을
 * 직접 만든다(JDK에 WebP 인코더가 없음).
 */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png");
    }

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpg");
    }

    /** SOI 바로 뒤에 APP1 {@code Exif} 조각을 끼운 JPEG(촬영 정보가 남은 사진 흉내). */
    public static byte[] jpegWithExif(int width, int height) {
        byte[] jpeg = jpeg(width, height);
        byte[] payload = "Exif\0\0MM\0*\0\0\0\bGPSDATA".getBytes(StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write((length >> 8) & 0xFF);
        out.write(length & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    /** 무손실 WebP(VP8L) 머리말 + 채움 바이트. */
    public static byte[] webp(int width, int height) {
        return webp(width, height, 64);
    }

    public static byte[] webp(int width, int height, int padding) {
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.write(0x2F);
        long bits = (long) (width - 1) | ((long) (height - 1) << 14);
        for (int i = 0; i < 4; i++) {
            chunk.write((int) (bits >> (8 * i)) & 0xFF);
        }
        for (int i = 0; i < padding; i++) {
            chunk.write(0);
        }
        return riff(chunk("VP8L", chunk.toByteArray()));
    }

    /** VP8X 머리말(EXIF 플래그와 EXIF 조각 포함). */
    public static byte[] webpWithExif(int width, int height) {
        byte[] vp8x = new byte[10];
        vp8x[0] = 0x08;
        put24(vp8x, 4, width - 1);
        put24(vp8x, 7, height - 1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(chunk("VP8X", vp8x));
        body.writeBytes(chunk("EXIF", "MM\0*GPS".getBytes(StandardCharsets.ISO_8859_1)));
        return riff(body.toByteArray());
    }

    public static byte[] gif(int width, int height) {
        return encode(width, height, "gif");
    }

    /** 지정한 크기의 의미 없는 바이트(크기 초과 시험용). */
    public static byte[] filler(byte[] head, int totalSize) {
        byte[] out = new byte[totalSize];
        System.arraycopy(head, 0, out, 0, Math.min(head.length, totalSize));
        return out;
    }

    private static byte[] chunk(String fourCc, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(fourCc.getBytes(StandardCharsets.US_ASCII));
        writeLe32(out, data.length);
        out.writeBytes(data);
        if (data.length % 2 == 1) {
            out.write(0);
        }
        return out.toByteArray();
    }

    private static byte[] riff(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLe32(out, body.length + 4);
        out.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(body);
        return out.toByteArray();
    }

    private static void writeLe32(ByteArrayOutputStream out, int value) {
        for (int i = 0; i < 4; i++) {
            out.write((value >> (8 * i)) & 0xFF);
        }
    }

    private static void put24(byte[] target, int offset, int value) {
        target[offset] = (byte) (value & 0xFF);
        target[offset + 1] = (byte) ((value >> 8) & 0xFF);
        target[offset + 2] = (byte) ((value >> 16) & 0xFF);
    }

    private static byte[] encode(int width, int height, String format) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x33, 0x66, 0x99));
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("no writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
