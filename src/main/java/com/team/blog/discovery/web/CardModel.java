package com.team.blog.discovery.web;

import com.team.blog.post.application.CardDates;
import com.team.blog.post.application.CardPage;
import com.team.blog.post.application.PostCard;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ui.Model;

/** 목록 화면 공용 모델 채우기(카드·날짜 표시·다음 커서). */
final class CardModel {

    private CardModel() {
    }

    static void fill(Model model, CardPage page, Instant now) {
        Map<Long, String> dates = new HashMap<>();
        for (PostCard card : page.items()) {
            dates.put(card.id(), CardDates.label(card.firstPublicAt(), now));
        }
        model.addAttribute("cards", page.items());
        model.addAttribute("cardDates", dates);
        model.addAttribute("nextCursor", page.nextCursor());
    }
}
