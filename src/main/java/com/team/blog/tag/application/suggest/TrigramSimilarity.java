package com.team.blog.tag.application.suggest;

import java.util.HashSet;
import java.util.Set;

/** 3글자 단위 Jaccard 유사도(34 §5-1 ②). 긴 글도 1ms 안팎. */
public final class TrigramSimilarity {

    private TrigramSimilarity() {
    }

    public static double jaccard(String a, String b) {
        Set<String> x = grams(a);
        Set<String> y = grams(b);
        if (x.isEmpty() && y.isEmpty()) {
            return 1.0;
        }
        Set<String> inter = new HashSet<>(x);
        inter.retainAll(y);
        int union = x.size() + y.size() - inter.size();
        return union == 0 ? 0 : (double) inter.size() / union;
    }

    private static Set<String> grams(String s) {
        Set<String> grams = new HashSet<>();
        int[] cps = s.codePoints().toArray();
        for (int i = 0; i + 3 <= cps.length; i++) {
            grams.add(new String(cps, i, 3));
        }
        return grams;
    }
}
