package me.aster.echochat.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SensitiveWordFilter} verifying DFA-based
 * sensitive word matching, replacement, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SensitiveWordFilter")
class SensitiveWordFilterTest {

    @Nested
    @DisplayName("filter")
    class Filter {

        @Test
        @DisplayName("should replace single sensitive word with ***")
        void shouldReplaceSingleSensitiveWord() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            String result = filter.filter("this is a badword here");
            assertThat(result).isEqualTo("this is a *** here");
        }

        @Test
        @DisplayName("should replace multiple different sensitive words")
        void shouldReplaceMultipleDifferentWords() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword", "spam", "attack"));
            String result = filter.filter("badword and spam lead to attack");
            assertThat(result).isEqualTo("*** and *** lead to ***");
        }

        @Test
        @DisplayName("should replace multiple occurrences of the same word")
        void shouldReplaceMultipleOccurrencesOfSameWord() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            String result = filter.filter("badword badword badword");
            assertThat(result).isEqualTo("*** *** ***");
        }

        @Test
        @DisplayName("should return original text when no sensitive words match")
        void shouldReturnOriginalTextWhenNoMatch() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            String result = filter.filter("this is clean text");
            assertThat(result).isEqualTo("this is clean text");
        }

        @Test
        @DisplayName("should match longest overlapping word in DFA trie")
        void shouldMatchLongestOverlappingWord() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("ab", "abc"));
            String result = filter.filter("abc abx");
            assertThat(result).isEqualTo("*** ***x");
        }

        @Test
        @DisplayName("should be case-sensitive when matching words")
        void shouldBeCaseSensitive() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            String result = filter.filter("Badword BADWORD");
            assertThat(result).isEqualTo("Badword BADWORD");
        }

        @Test
        @DisplayName("should handle Chinese characters as sensitive words")
        void shouldHandleChineseCharacters() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("敏感词", "违禁"));
            String result = filter.filter("这是一段包含敏感词的文本");
            assertThat(result).isEqualTo("这是一段包含***的文本");
        }

        @Test
        @DisplayName("should return null when input text is null")
        void shouldReturnNullWhenInputIsNull() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.filter(null)).isNull();
        }

        @Test
        @DisplayName("should return empty string when input text is empty")
        void shouldReturnEmptyWhenInputIsEmpty() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.filter("")).isEmpty();
        }

        @Test
        @DisplayName("should return original text when filter has no words")
        void shouldReturnOriginalTextWhenFilterHasNoWords() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Collections.emptySet());
            assertThat(filter.filter("some text")).isEqualTo("some text");
        }

        @Test
        @DisplayName("should return original text when filter is built with null or empty entries")
        void shouldSkipNullAndEmptyWords() {
            Set<String> words = new HashSet<>();
            words.add(null);
            words.add("");
            words.add("valid");
            SensitiveWordFilter filter = new SensitiveWordFilter(words);
            String result = filter.filter("this is valid text");
            assertThat(result).isEqualTo("this is *** text");
        }

        @Test
        @DisplayName("should handle word at the beginning of text")
        void shouldHandleWordAtBeginning() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.filter("badword at start")).isEqualTo("*** at start");
        }

        @Test
        @DisplayName("should handle word at the end of text")
        void shouldHandleWordAtEnd() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.filter("end with badword")).isEqualTo("end with ***");
        }

        @Test
        @DisplayName("should handle single character sensitive word")
        void shouldHandleSingleCharacterWord() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("x"));
            assertThat(filter.filter("a x b x c")).isEqualTo("a *** b *** c");
        }
    }

    @Nested
    @DisplayName("containsSensitiveWord")
    class ContainsSensitiveWord {

        @Test
        @DisplayName("should return true when text contains a sensitive word")
        void shouldReturnTrueWhenSensitiveWordFound() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.containsSensitiveWord("this is a badword here")).isTrue();
        }

        @Test
        @DisplayName("should return false when text has no sensitive words")
        void shouldReturnFalseWhenNoMatch() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.containsSensitiveWord("clean text")).isFalse();
        }

        @Test
        @DisplayName("should return false when input is null")
        void shouldReturnFalseWhenInputIsNull() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.containsSensitiveWord(null)).isFalse();
        }

        @Test
        @DisplayName("should return false when input is empty")
        void shouldReturnFalseWhenInputIsEmpty() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Set.of("badword"));
            assertThat(filter.containsSensitiveWord("")).isFalse();
        }

        @Test
        @DisplayName("should return false when filter has no words loaded")
        void shouldReturnFalseWhenFilterHasNoWords() {
            SensitiveWordFilter filter = new SensitiveWordFilter(Collections.emptySet());
            assertThat(filter.containsSensitiveWord("some text")).isFalse();
        }
    }

    @Nested
    @DisplayName("fromClasspath")
    class FromClasspath {

        @Test
        @DisplayName("should load words from classpath resource and filter correctly")
        void shouldLoadWordsFromClasspathAndFilter() {
            SensitiveWordFilter filter = SensitiveWordFilter.fromClasspath("sensitive_words_test.txt");
            String result = filter.filter("this is a badword and spam message");
            assertThat(result).isEqualTo("this is a *** and *** message");
        }

        @Test
        @DisplayName("should return empty filter when resource does not exist")
        void shouldReturnEmptyFilterWhenResourceNotFound() {
            SensitiveWordFilter filter = SensitiveWordFilter.fromClasspath("nonexistent_file.txt");
            assertThat(filter.filter("some text")).isEqualTo("some text");
            assertThat(filter.containsSensitiveWord("any text")).isFalse();
        }
    }

    @Nested
    @DisplayName("word set changes")
    class WordSetChanges {

        @Test
        @DisplayName("should stop filtering after removing word from set (new filter instance)")
        void shouldStopFilteringWhenWordRemoved() {
            SensitiveWordFilter filterWithWord = new SensitiveWordFilter(Set.of("badword", "spam"));
            assertThat(filterWithWord.filter("badword spam")).isEqualTo("*** ***");

            SensitiveWordFilter filterWithoutWord = new SensitiveWordFilter(Set.of("spam"));
            assertThat(filterWithoutWord.filter("badword spam")).isEqualTo("badword ***");
        }

        @Test
        @DisplayName("should start filtering after adding word to set (new filter instance)")
        void shouldStartFilteringWhenWordAdded() {
            SensitiveWordFilter filterBefore = new SensitiveWordFilter(Set.of("spam"));
            assertThat(filterBefore.filter("badword spam")).isEqualTo("badword ***");

            SensitiveWordFilter filterAfter = new SensitiveWordFilter(Set.of("spam", "badword"));
            assertThat(filterAfter.filter("badword spam")).isEqualTo("*** ***");
        }
    }
}
