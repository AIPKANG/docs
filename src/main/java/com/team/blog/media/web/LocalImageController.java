package com.team.blog.media.web;

import com.team.blog.media.application.ImageProperties;
import com.team.blog.media.application.ImageStorage;
import com.team.blog.media.infra.LocalImageStorage;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대체 저장소의 업로드·내려받기(008 research R-6). 업로드는 서명으로만 허가한다(로그인·CSRF 대신, 저장소 사전 서명 PUT과 같은 모양).
 * 내려받기는 {@code images/} 아래 파일만, 1년 변경 없는 캐시.
 */
@RestController
@ConditionalOnProperty(name = "blog.storage.type", havingValue = "local")
public class LocalImageController {

    private final LocalImageStorage storage;
    private final ImageProperties properties;

    public LocalImageController(LocalImageStorage storage, ImageProperties properties) {
        this.storage = storage;
        this.properties = properties;
    }

    @PutMapping(LocalImageStorage.UPLOAD_PATH)
    public ResponseEntity<Void> upload(@RequestParam String key, @RequestParam long exp, @RequestParam String sig,
                                       @RequestHeader(value = "Content-Type", required = false) String contentType,
                                       HttpServletRequest request) throws IOException {
        String type = contentType == null ? null : contentType.split(";")[0].strip();
        if (!storage.verify(key, type, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            storage.write(key, type, request.getInputStream(), properties.post().maxBytes());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).build();
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(LocalImageStorage.PUBLIC_PATH + "images/**")
    public ResponseEntity<byte[]> download(HttpServletRequest request) throws IOException {
        String key = request.getRequestURI().substring(request.getContextPath().length() + LocalImageStorage.PUBLIC_PATH.length());
        Path file = storage.root().resolve(key).normalize();
        if (!file.startsWith(storage.root()) || !Files.isRegularFile(file) || key.endsWith(".type")) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = storage.contentTypeOf(key).map(MediaType::parseMediaType).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).header("Cache-Control", ImageStorage.CACHE_CONTROL)
                .body(Files.readAllBytes(file));
    }
}
