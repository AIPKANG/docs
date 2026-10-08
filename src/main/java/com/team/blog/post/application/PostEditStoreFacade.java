package com.team.blog.post.application;

import com.team.blog.post.infra.PostEditStore;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 발행 글이 고치는 중이면 그 내용의 저장 시각(작업본 또는 발행본보다 새 버퍼, 004 R-2). 010 상세 안내용 —
 * 본문(Markdown)은 읽지 않는다(010 FR-027).
 */
@Component
public class PostEditStoreFacade {

    private final PostEditStore store;
    private final AutosaveBuffer buffer;
    private final BufferCircuit circuit;

    public PostEditStoreFacade(PostEditStore store, AutosaveBuffer buffer, BufferCircuit circuit) {
        this.store = store;
        this.buffer = buffer;
        this.circuit = circuit;
    }

    public Optional<Instant> editingSavedAt(long postId) {
        Optional<PostEditStore.EditVersions> versions = store.editVersions(postId);
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        PostEditStore.EditVersions v = versions.get();
        long best = v.postVersion();
        Instant savedAt = null;
        if (v.draftVersion() != null && v.draftVersion() > best) {
            best = v.draftVersion();
            savedAt = v.draftUpdatedAt();
        }
        if (circuit.allowsRedis()) {
            try {
                Optional<BufferedContent> buffered = buffer.read(postId);
                if (buffered.isPresent() && buffered.get().version() > best) {
                    savedAt = buffered.get().savedAt();
                }
            } catch (DataAccessException e) {
                circuit.recordFailure();
            }
        }
        return Optional.ofNullable(savedAt);
    }
}
