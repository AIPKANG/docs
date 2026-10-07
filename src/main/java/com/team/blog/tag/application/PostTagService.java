package com.team.blog.tag.application;

import com.team.blog.tag.infra.TagStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 글의 태그(22 §4, 005 발행 트랜잭션 ⑤). 이름은 {@link com.team.blog.tag.domain.TagNormalizer}로 정규화된 값만 받는다.
 * 발행 트랜잭션에 참여한다(전파 REQUIRED).
 */
@Service
public class PostTagService {

    private final TagStore store;

    public PostTagService(TagStore store) {
        this.store = store;
    }

    @Transactional
    public void replace(long postId, List<String> normalizedNames) {
        List<Long> ids = new ArrayList<>(normalizedNames.size());
        for (String name : normalizedNames) {
            ids.add(store.ensure(name));
        }
        store.replacePostTags(postId, ids);
    }

    @Transactional(readOnly = true)
    public List<String> tagsOf(long postId) {
        return store.namesOf(postId);
    }
}
