package com.team.blog.media.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.media.domain.ImageFormat;
import com.team.blog.media.domain.ImageHeader;
import com.team.blog.media.domain.ImageHeaderInspector;
import com.team.blog.support.TestImages;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 003 T237: 앞부분 바이트로 형식·크기·메타데이터 판별(research R-8). */
class ImageHeaderInspectorTest {

    @Test
    void detectsFormatAndSize() {
        assertThat(ImageHeaderInspector.inspect(TestImages.png(256, 128)))
                .contains(new ImageHeader(ImageFormat.PNG, 256, 128, false));
        assertThat(ImageHeaderInspector.inspect(TestImages.jpeg(300, 256)))
                .contains(new ImageHeader(ImageFormat.JPEG, 300, 256, false));
        assertThat(ImageHeaderInspector.inspect(TestImages.gif(64, 32)))
                .contains(new ImageHeader(ImageFormat.GIF, 64, 32, false));
        assertThat(ImageHeaderInspector.inspect(TestImages.webp(256, 256)))
                .contains(new ImageHeader(ImageFormat.WEBP, 256, 256, false));
    }

    @Test
    void lossyWebpAndExtendedWebp() {
        byte[] vp8 = vp8(200, 100);
        assertThat(ImageHeaderInspector.inspect(vp8)).contains(new ImageHeader(ImageFormat.WEBP, 200, 100, false));
        assertThat(ImageHeaderInspector.inspect(TestImages.webpWithExif(256, 256)))
                .contains(new ImageHeader(ImageFormat.WEBP, 256, 256, true));
    }

    @Test
    void detectsMetadata() {
        assertThat(ImageHeaderInspector.inspect(TestImages.jpegWithExif(256, 256)).orElseThrow().hasMetadata()).isTrue();
    }

    @Test
    void rejectsUnknownOrTruncatedBytes() {
        assertThat(ImageHeaderInspector.inspect(new byte[0])).isEmpty();
        assertThat(ImageHeaderInspector.inspect("MZ\u0090\u0000 executable".getBytes())).isEmpty();
        assertThat(ImageHeaderInspector.inspect(Arrays.copyOf(TestImages.png(10, 10), 12))).isEmpty();
        assertThat(ImageHeaderInspector.inspect(Arrays.copyOf(TestImages.webp(10, 10), 20))).isEmpty();
        assertThat(ImageHeaderInspector.inspect(Arrays.copyOf(TestImages.jpeg(10, 10), 4))).isEmpty();
    }

    @Test
    void contentTypeMapping() {
        assertThat(ImageFormat.fromContentType("image/webp")).contains(ImageFormat.WEBP);
        assertThat(ImageFormat.fromContentType("image/jpeg")).contains(ImageFormat.JPEG);
        assertThat(ImageFormat.fromContentType("image/bmp")).isEmpty();
        assertThat(ImageFormat.WEBP.extension()).isEqualTo("webp");
        assertThat(ImageFormat.JPEG.extension()).isEqualTo("jpg");
    }

    /** 손실 WebP: VP8 조각(프레임 태그 3바이트 + 시작 코드 9D 01 2A + 14비트 가로·세로). */
    private static byte[] vp8(int width, int height) {
        byte[] data = new byte[30];
        data[3] = (byte) 0x9D;
        data[4] = 0x01;
        data[5] = 0x2A;
        data[6] = (byte) (width & 0xFF);
        data[7] = (byte) ((width >> 8) & 0x3F);
        data[8] = (byte) (height & 0xFF);
        data[9] = (byte) ((height >> 8) & 0x3F);
        byte[] out = new byte[12 + 8 + data.length];
        System.arraycopy("RIFF".getBytes(), 0, out, 0, 4);
        int riffSize = out.length - 8;
        out[4] = (byte) riffSize;
        System.arraycopy("WEBP".getBytes(), 0, out, 8, 4);
        System.arraycopy("VP8 ".getBytes(), 0, out, 12, 4);
        out[16] = (byte) data.length;
        System.arraycopy(data, 0, out, 20, data.length);
        return out;
    }
}
