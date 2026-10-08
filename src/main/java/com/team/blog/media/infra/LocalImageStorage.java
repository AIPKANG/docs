package com.team.blog.media.infra;

import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.application.StorageProperties;
import com.team.blog.media.application.StoredObject;
import com.team.blog.media.application.UploadTarget;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 서버 디스크 저장소(008 research R-6, 04 §4-1 {@code LocalImageStorage}): 파일을 서버 디렉터리(운영은 Docker 볼륨)에 둔다.
 * 업로드 주소는 우리 서버 {@code PUT /api/images/local-upload}(HMAC 서명·5분), 공개 주소는 상대 주소 {@code /media/{key}}라
 * 도메인이 바뀌어도 그대로다. 운영의 첫 저장 방식이고, 앱 서버를 여러 대로 늘릴 때 S3 호환 저장소(MinIO)로 옮긴다(DEPLOY.md).
 * 서명 키({@code STORAGE_LOCAL_SECRET})가 없으면 실행마다 무작위 키를 쓴다(개발용, 운영은 필수값 검사가 막는다).
 */
@Component
@ConditionalOnProperty(name = "blog.storage.type", havingValue = "local")
public class LocalImageStorage implements ImageStorage {

    public static final String UPLOAD_PATH = "/api/images/local-upload";
    public static final String PUBLIC_PATH = "/media/";

    private final Path root;
    private final byte[] secret;
    private final StorageProperties properties;
    private final Clock clock;

    public LocalImageStorage(StorageProperties properties, Clock clock) throws IOException {
        this.properties = properties;
        this.clock = clock;
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        String configured = properties.localSecret();
        if (configured == null || configured.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.secret = random;
        } else {
            this.secret = configured.getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public UploadTarget prepareUpload(String key, String contentType, long size) {
        Instant expiresAt = clock.instant().plus(properties.presignTtl());
        long exp = expiresAt.getEpochSecond();
        String url = UPLOAD_PATH + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8) + "&exp=" + exp
                + "&sig=" + sign(key, contentType, exp);
        return new UploadTarget(url, "PUT", Map.of("Content-Type", contentType, "Cache-Control", CACHE_CONTROL), expiresAt);
    }

    /** 업로드 요청 확인: 서명·만료·Content-Type·키 형식. */
    public boolean verify(String key, String contentType, long exp, String sig) {
        if (key == null || contentType == null || sig == null || !key.startsWith("images/") || key.contains("..")) {
            return false;
        }
        if (clock.instant().getEpochSecond() > exp) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, contentType, exp).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    public void write(String key, String contentType, InputStream body, long maxBytes) throws IOException {
        Path file = resolve(key);
        Files.createDirectories(file.getParent());
        byte[] bytes = body.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1));
        if (bytes.length > maxBytes) {
            throw new IOException("too large");
        }
        Files.write(file, bytes);
        Files.writeString(meta(file), contentType);
    }

    @Override
    public Optional<StoredObject> inspect(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            long size = Files.size(file);
            byte[] head = in.readNBytes(StoredObject.HEAD_BYTES);
            String type = Files.exists(meta(file)) ? Files.readString(meta(file)) : null;
            return Optional.of(new StoredObject(size, type, head));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Optional<byte[]> read(String key, long maxBytes) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return Optional.of(in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return PUBLIC_PATH + key;
    }

    @Override
    public void delete(String key) {
        Path file = resolve(key);
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(meta(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path root() {
        return root;
    }

    public Optional<String> contentTypeOf(String key) {
        Path meta = meta(resolve(key));
        try {
            return Files.exists(meta) ? Optional.of(Files.readString(meta)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path resolve(String key) {
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("key outside storage");
        }
        return file;
    }

    private static Path meta(Path file) {
        return file.resolveSibling(file.getFileName() + ".type");
    }

    private String sign(String key, String contentType, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal((key + "\n" + contentType + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
