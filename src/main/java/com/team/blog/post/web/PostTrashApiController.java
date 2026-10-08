package com.team.blog.post.web;

import com.team.blog.post.application.ManagePage;
import com.team.blog.post.application.ManageTab;
import com.team.blog.post.application.PostManageQuery;
import com.team.blog.post.application.PostTrashService;
import com.team.blog.shared.security.CurrentUserProvider;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 글 관리 API와 삭제·복구·영구 삭제(011 contracts). */
@RestController
public class PostTrashApiController {

    private final PostTrashService trashService;
    private final PostManageQuery manageQuery;
    private final CurrentUserProvider currentUserProvider;

    public PostTrashApiController(PostTrashService trashService, PostManageQuery manageQuery,
                                  CurrentUserProvider currentUserProvider) {
        this.trashService = trashService;
        this.manageQuery = manageQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/me/posts")
    public ManagePage myPosts(@RequestParam(value = "tab", required = false) String tab,
                              @RequestParam(value = "visibility", required = false) String visibility,
                              @RequestParam(value = "cursor", required = false) String cursor) {
        return manageQuery.page(currentUserProvider.current(), ManageTab.of(tab), visibility, cursor);
    }

    @DeleteMapping("/api/posts/{postId}")
    public PostTrashService.TrashResult trash(@PathVariable long postId) {
        return trashService.trash(currentUserProvider.current(), postId);
    }

    @PostMapping("/api/posts/{postId}/restore")
    public Map<String, Boolean> restore(@PathVariable long postId) {
        trashService.restore(currentUserProvider.current(), postId);
        return Map.of("restored", true);
    }

    @DeleteMapping("/api/posts/{postId}/permanent")
    public ResponseEntity<Void> purge(@PathVariable long postId) {
        trashService.purge(currentUserProvider.current(), postId);
        return ResponseEntity.noContent().build();
    }
}
