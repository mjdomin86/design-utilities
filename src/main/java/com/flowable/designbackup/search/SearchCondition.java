package com.flowable.designbackup.search;

import java.util.regex.Pattern;

/**
 * Matching condition applied to model or element content.
 * EQUALS matches the term as an exact token (e.g. an attribute value or word),
 * not the whole document.
 */
public enum SearchCondition {

    CONTAINS {
        @Override
        public boolean matches(String content, String term) {
            return content.contains(term);
        }
    },
    NOT_CONTAINS {
        @Override
        public boolean matches(String content, String term) {
            return !content.contains(term);
        }
    },
    EQUALS {
        @Override
        public boolean matches(String content, String term) {
            return tokenPattern(term).matcher(content).find();
        }
    };

    public abstract boolean matches(String content, String term);

    private static Pattern tokenPattern(String term) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(term) + "(?![\\w.-])");
    }
}
