package com.team.blog.discovery.web;

import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostListQuery;
import com.team.blog.post.application.TagListingQuery;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.tag.domain.TagNormalizer;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriUtils;

/**
 * 태그 화면·API(013, 22 §5~§7). 태그 주소는 이름을 경로 조각으로 인코딩한다({@code #} → {@code %23}). 정리되지 않은 이름은
 * 정리된 주소로 301, 형식에 맞지 않으면 404. 공개 글이 없는 태그도 정상 페이지(아무도 안 씀과 비공개 글에만 쓰임을 구별하지 않음).
 */
@Controller
public class TagPageController {

    private final TagListingQuery tagQuery;
    private final PostListQuery listQuery;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public TagPageController(TagListingQuery tagQuery, PostListQuery listQuery, CurrentUserProvider currentUserProvider,
                             Clock clock) {
        this.tagQuery = tagQuery;
        this.listQuery = listQuery;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public static String tagPath(String name) {
        return "/tags/" + UriUtils.encodePathSegment(name, java.nio.charset.StandardCharsets.UTF_8);
    }

    @GetMapping("/tags/{name}")
    public Object tag(@PathVariable("name") String raw, @RequestParam(value = "cursor", required = false) String cursor,
                      Model model) {
        String name = TagNormalizer.lookupName(raw).orElseThrow(NotFoundException::new);
        if (!name.equals(raw)) {
            RedirectView redirect = new RedirectView(tagPath(name));
            redirect.setStatusCode(HttpStatus.MOVED_PERMANENTLY);
            return redirect;
        }
        Optional<Long> tagId = tagQuery.tagId(name);
        CardPage page;
        try {
            page = tagId.map(id -> listQuery.byTag(id, cursor)).orElse(new CardPage(List.of(), null));
        } catch (PostContentException e) {
            page = tagId.map(id -> listQuery.byTag(id, null)).orElse(new CardPage(List.of(), null));
        }
        CardModel.fill(model, page, clock.instant());
        model.addAttribute("tagName", name);
        model.addAttribute("publicCount", tagId.isPresent() ? tagQuery.publicCount(name) : 0);
        model.addAttribute("continued", cursor != null && !cursor.isEmpty());
        model.addAttribute("listApi", "/api" + tagPath(name) + "/posts");
        model.addAttribute("tagPath", tagPath(name));
        return "tag/tag";
    }

    @GetMapping("/tags")
    public String tags(Model model) {
        model.addAttribute("tags", tagQuery.top(100));
        return "tag/tags";
    }

    @GetMapping("/api/tags/{name}/posts")
    @ResponseBody
    public CardPage tagPosts(@PathVariable("name") String raw, @RequestParam(value = "cursor", required = false) String cursor) {
        String name = TagNormalizer.lookupName(raw).orElseThrow(NotFoundException::new);
        return tagQuery.tagId(name).map(id -> listQuery.byTag(id, cursor)).orElse(new CardPage(List.of(), null));
    }

    @GetMapping("/api/tags")
    @ResponseBody
    public List<TagListingQuery.TagCount> topTags(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return tagQuery.top(Math.max(1, Math.min(100, limit)));
    }

    @GetMapping("/api/tags/suggest")
    @ResponseBody
    public List<TagListingQuery.Suggestion> suggest(@RequestParam(value = "q", required = false) String q) {
        return tagQuery.suggest(currentUserProvider.current(), q);
    }
}
