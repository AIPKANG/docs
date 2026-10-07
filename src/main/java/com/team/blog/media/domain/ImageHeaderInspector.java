package com.team.blog.media.domain;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 파일 앞부분 바이트로 실제 형식(매직 바이트)·가로·세로·메타데이터 조각을 읽는다(003 research R-8, 04 §4-1 ⑤).
 * 파일 전체를 디코딩하지 않는다. 읽을 수 없으면 empty(형식 위장·손상·잘린 파일).
 */
public final class ImageHeaderInspector {

    private ImageHeaderInspector() {
    }

    public static Optional<ImageHeader> inspect(byte[] head) {
        if (head == null || head.length < 4) {
            return Optional.empty();
        }
        try {
            if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
                return jpeg(head);
            }
            if (startsWith(head, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
                return png(head);
            }
            if (ascii(head, 0, "GIF87a") || ascii(head, 0, "GIF89a")) {
                return gif(head);
            }
            if (ascii(head, 0, "RIFF") && ascii(head, 8, "WEBP")) {
                return webp(head);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<ImageHeader> jpeg(byte[] b) {
        int i = 2;
        boolean metadata = false;
        int width = -1;
        int height = -1;
        while (i + 3 < b.length) {
            if (u8(b, i) != 0xFF) {
                return Optional.empty();
            }
            int marker = u8(b, i + 1);
            if (marker == 0xFF) {
                i++;
                continue;
            }
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            int length = be16(b, i + 2);
            if (length < 2) {
                return Optional.empty();
            }
            if (marker == 0xE1 && ascii(b, i + 4, "Exif")) {
                metadata = true;
            }
            boolean sof = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (sof && width < 0) {
                height = be16(b, i + 5);
                width = be16(b, i + 7);
            }
            i += 2 + length;
        }
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ImageHeader(ImageFormat.JPEG, width, height, metadata));
    }

    private static Optional<ImageHeader> png(byte[] b) {
        if (b.length < 24 || !ascii(b, 12, "IHDR")) {
            return Optional.empty();
        }
        int width = be32(b, 16);
        int height = be32(b, 20);
        boolean metadata = false;
        int i = 8;
        while (i + 8 <= b.length) {
            int length = be32(b, i);
            String type = new String(b, i + 4, 4, StandardCharsets.ISO_8859_1);
            if (type.equals("eXIf")) {
                metadata = true;
            }
            if (type.equals("IDAT") || type.equals("IEND") || length < 0) {
                break;
            }
            i += 12 + length;
        }
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ImageHeader(ImageFormat.PNG, width, height, metadata));
    }

    private static Optional<ImageHeader> gif(byte[] b) {
        if (b.length < 10) {
            return Optional.empty();
        }
        int width = le16(b, 6);
        int height = le16(b, 8);
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ImageHeader(ImageFormat.GIF, width, height, false));
    }

    private static Optional<ImageHeader> webp(byte[] b) {
        int i = 12;
        int width = -1;
        int height = -1;
        boolean metadata = false;
        boolean first = true;
        while (i + 8 <= b.length) {
            String fourCc = new String(b, i, 4, StandardCharsets.ISO_8859_1);
            int size = le32(b, i + 4);
            int data = i + 8;
            if (size < 0) {
                return Optional.empty();
            }
            if (first) {
                switch (fourCc) {
                    case "VP8 " -> {
                        if (data + 10 > b.length || u8(b, data + 3) != 0x9D || u8(b, data + 4) != 0x01
                                || u8(b, data + 5) != 0x2A) {
                            return Optional.empty();
                        }
                        width = le16(b, data + 6) & 0x3FFF;
                        height = le16(b, data + 8) & 0x3FFF;
                    }
                    case "VP8L" -> {
                        if (data + 5 > b.length || u8(b, data) != 0x2F) {
                            return Optional.empty();
                        }
                        long bits = le32(b, data + 1) & 0xFFFFFFFFL;
                        width = (int) (bits & 0x3FFF) + 1;
                        height = (int) ((bits >> 14) & 0x3FFF) + 1;
                    }
                    case "VP8X" -> {
                        if (data + 10 > b.length) {
                            return Optional.empty();
                        }
                        int flags = u8(b, data);
                        metadata = (flags & 0x0C) != 0;
                        width = le24(b, data + 4) + 1;
                        height = le24(b, data + 7) + 1;
                    }
                    default -> {
                        return Optional.empty();
                    }
                }
                first = false;
            } else if (fourCc.equals("EXIF") || fourCc.equals("XMP ")) {
                metadata = true;
            }
            i = data + size + (size % 2);
        }
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        return Optional.of(new ImageHeader(ImageFormat.WEBP, width, height, metadata));
    }

    // ----- 바이트 도우미 -----

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int k = 0; k < expected.length; k++) {
            if (u8(b, offset + k) != expected[k]) {
                return false;
            }
        }
        return true;
    }

    private static boolean ascii(byte[] b, int offset, String text) {
        if (b.length < offset + text.length()) {
            return false;
        }
        for (int k = 0; k < text.length(); k++) {
            if (b[offset + k] != (byte) text.charAt(k)) {
                return false;
            }
        }
        return true;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int be16(byte[] b, int i) {
        return (u8(b, i) << 8) | u8(b, i + 1);
    }

    private static int be32(byte[] b, int i) {
        return (u8(b, i) << 24) | (u8(b, i + 1) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3);
    }

    private static int le16(byte[] b, int i) {
        return u8(b, i) | (u8(b, i + 1) << 8);
    }

    private static int le24(byte[] b, int i) {
        return u8(b, i) | (u8(b, i + 1) << 8) | (u8(b, i + 2) << 16);
    }

    private static int le32(byte[] b, int i) {
        return u8(b, i) | (u8(b, i + 1) << 8) | (u8(b, i + 2) << 16) | (u8(b, i + 3) << 24);
    }
}
