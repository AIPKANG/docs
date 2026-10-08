package com.team.blog.teaser.web;

import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.error.RateLimitedException;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.teaser.application.TeaserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** AI 티저(035): 글 화면의 [AI로 만들기]·[저장]. 제안은 한 번만 보이는 값으로 글 화면에 돌려준다. 스크립트 없이 폼. */
@Controller
@ConditionalOnProperty(name = "blog.teaser.enabled", havingValue = "true", matchIfMissing = true)
public class TeaserController {

    private final TeaserService teasers;
    private final CurrentUserProvider currentUserProvider;
    private final JdbcTemplate jdbc;
    private final MessageSource messages;

    public TeaserController(TeaserService teasers, CurrentUserProvider currentUserProvider, JdbcTemplate jdbc,
                            MessageSource messages) {
        this.teasers = teasers;
        this.currentUserProvider = currentUserProvider;
        this.jdbc = jdbc;
        this.messages = messages;
    }

    @PostMapping("/posts/{id}/teaser/generate")
    public String generate(@PathVariable("id") long id, RedirectAttributes flash) {
        try {
            flash.addFlashAttribute("teaserDraft", teasers.generate(currentUserProvider.current(), id));
        } catch (PostContentException e) {
            flash.addFlashAttribute("teaserError", messages.getMessage("error." + e.getMessage(), null, e.getMessage(),
                    LocaleContextHolder.getLocale()));
        } catch (RateLimitedException e) {
            flash.addFlashAttribute("teaserError", "오늘은 더 만들 수 없어요. 내일 다시 해 보세요");
        }
        return "redirect:" + url(id) + "#teaser";
    }

    @PostMapping("/posts/{id}/teaser")
    public String save(@PathVariable("id") long id, @RequestParam(value = "teaser", required = false) String teaser) {
        teasers.save(currentUserProvider.current(), id, teaser);
        return "redirect:" + url(id) + "#teaser";
    }

    private String url(long id) {
        return jdbc.queryForObject("SELECT '/@' || m.handle || '/posts/' || p.id FROM post p JOIN member m ON m.id = p.author_id WHERE p.id = ?",
                String.class, id);
    }
}
