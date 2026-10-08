package com.team.blog.media.web;

import com.team.blog.media.application.CompleteResult;
import com.team.blog.media.application.ImageUploadService;
import com.team.blog.media.application.PresignCommand;
import com.team.blog.media.application.PresignResult;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사진 업로드 승인·완료 API(contracts/web-routes.md §3). 올리는 사람은 로그인 정보로만 정한다. */
@RestController
@RequestMapping("/api/images")
public class ImageApiController {

    public record PresignRequest(String purpose, String contentType, Long size, String originalName, Long thumbSize) {
    }

    private final ImageUploadService imageUploadService;
    private final CurrentUserProvider currentUserProvider;

    public ImageApiController(ImageUploadService imageUploadService, CurrentUserProvider currentUserProvider) {
        this.imageUploadService = imageUploadService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/presign")
    public PresignResult presign(@RequestBody PresignRequest body) {
        return imageUploadService.presign(currentUserProvider.current(),
                new PresignCommand(body.purpose(), body.contentType(), body.size(), body.originalName(), body.thumbSize()));
    }

    @PostMapping("/{id}/complete")
    public CompleteResult complete(@PathVariable("id") long id) {
        return imageUploadService.complete(currentUserProvider.current(), id);
    }
}
