package com.team.blog.note.web;

import static com.team.blog.shared.web.Redirects.seeOther;

import com.team.blog.account.application.BlogOwner;
import com.team.blog.account.application.BlogOwnerResolver;
import com.team.blog.note.application.NoteService;
import com.team.blog.post.application.CardDates;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** 짧은 기록 화면(033, 강성찬 개인 확장): 블로그별 모아 보기, 쓰기·지우기(스크립트 없이 폼). */
@Controller
@ConditionalOnProperty(name = "blog.notes.enabled", havingValue = "true", matchIfMissing = true)
public class NoteController {

    private final NoteService notes;
    private final BlogOwnerResolver owners;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public NoteController(NoteService notes, BlogOwnerResolver owners, CurrentUserProvider currentUserProvider, Clock clock) {
        this.notes = notes;
        this.owners = owners;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/@{handle}/notes")
    public String list(@PathVariable("handle") String handle, @RequestParam(value = "before", required = false) Long before,
                       Model model) {
        BlogOwner owner = owners.resolve(handle).orElseThrow(NotFoundException::new);
        Optional<CurrentUser> viewer = currentUserProvider.current();
        int size = notes.pageSize();
        List<NoteService.Note> page = notes.notes(owner.memberId(), viewer, before, size + 1);
        boolean more = page.size() > size;
        List<NoteService.Note> items = page.subList(0, Math.min(size, page.size()));
        model.addAttribute("owner", owner);
        model.addAttribute("notes", items);
        model.addAttribute("noteDates", dates(items));
        model.addAttribute("nextBefore", more ? items.get(items.size() - 1).id() : null);
        model.addAttribute("isOwner", viewer.map(v -> v.memberId() == owner.memberId()).orElse(false));
        return "note/list";
    }

    @PostMapping("/notes")
    public RedirectView write(@RequestParam("content") String content,
                              @RequestParam(value = "visibility", required = false) String visibility,
                              @RequestParam(value = "back", required = false) String back) {
        notes.write(currentUserProvider.current(), content, visibility);
        return seeOther(safe(back));
    }

    @PostMapping("/notes/{id}/delete")
    public RedirectView delete(@PathVariable("id") long id, @RequestParam(value = "back", required = false) String back) {
        notes.delete(currentUserProvider.current(), id);
        return seeOther(safe(back));
    }

    private Map<Long, String> dates(List<NoteService.Note> items) {
        Map<Long, String> m = new HashMap<>();
        items.forEach(n -> m.put(n.id(), CardDates.label(n.createdAt(), clock.instant())));
        return m;
    }

    private static String safe(String back) {
        return back != null && back.startsWith("/") && !back.startsWith("//") && !back.contains("\\") ? back : "/";
    }
}
