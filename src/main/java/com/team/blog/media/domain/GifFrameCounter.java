package com.team.blog.media.domain;

import java.util.OptionalInt;

/**
 * GIF 프레임 수 세기(23 §5, 008 FR-031). 이미지 데이터를 풀지 않고 블록 구조만 훑는다(이미지 기술자 수 = 프레임 수).
 * {@code limit}을 넘는 순간 멈춘다. 구조가 깨졌거나 잘렸으면 빈 값.
 */
public final class GifFrameCounter {

    private GifFrameCounter() {
    }

    public static OptionalInt count(byte[] b, int limit) {
        if (b == null || b.length < 13 || b[0] != 'G' || b[1] != 'I' || b[2] != 'F') {
            return OptionalInt.empty();
        }
        int i = 13;
        int packed = b[10] & 0xFF;
        if ((packed & 0x80) != 0) {
            i += 3 * (1 << ((packed & 0x07) + 1));
        }
        int frames = 0;
        while (i < b.length) {
            int marker = b[i] & 0xFF;
            if (marker == 0x3B) {
                return OptionalInt.of(frames);
            }
            if (marker == 0x21) {
                i += 2; // 도입자 + 라벨
                i = skipSubBlocks(b, i);
            } else if (marker == 0x2C) {
                if (i + 10 > b.length) {
                    return OptionalInt.empty();
                }
                int local = b[i + 9] & 0xFF;
                i += 10;
                if ((local & 0x80) != 0) {
                    i += 3 * (1 << ((local & 0x07) + 1));
                }
                i += 1; // LZW 최소 코드 크기
                i = skipSubBlocks(b, i);
                frames++;
                if (frames > limit) {
                    return OptionalInt.of(frames);
                }
            } else {
                return OptionalInt.empty();
            }
            if (i < 0) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.empty(); // 끝 표시 없이 잘림
    }

    /** 크기 0 블록까지 건너뛴 다음 위치, 잘렸으면 -1. */
    private static int skipSubBlocks(byte[] b, int i) {
        while (true) {
            if (i < 0 || i >= b.length) {
                return -1;
            }
            int size = b[i] & 0xFF;
            i += 1;
            if (size == 0) {
                return i;
            }
            i += size;
        }
    }
}
