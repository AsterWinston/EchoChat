package me.aster.echochat.common.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * DFA（确定性有限自动机）敏感词过滤器。
 * 初始化后线程安全。将匹配到的敏感词替换为***。
 * @author AsterWinston
 */
@Slf4j
public class SensitiveWordFilter {

    private final Map<Character, Object> root = new HashMap<>(16);
    private static final String REPLACEMENT = "***";

    /**
     * 根据敏感词集合构建DFA字典树。每个词逐字符插入嵌套的Map结构中，
     * 在叶子节点处以结束标记（{@code '#'} &rarr; {@code true}）标识。
     *
     * @param words 要填充到字典树中的敏感词集合；
     *              null或空条目将被静默跳过
     */
    public SensitiveWordFilter(Set<String> words) {
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            Map<Character, Object> node = root;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                @SuppressWarnings("unchecked")
                Map<Character, Object> next = (Map<Character, Object>) node.computeIfAbsent(c, k -> new HashMap<>(16));
                node = next;
            }
            node.put('#', Boolean.TRUE);
        }
    }

    /**
     * 静态工厂方法，从classpath资源文件（每行一个词，UTF-8编码）加载敏感词
     * 并返回预构建的过滤器。
     *
     * @param resourcePath classpath资源路径，例如 {@code "sensitive_words.txt"}
 * @return 使用资源中的词填充的 {@code SensitiveWordFilter}，
 *         如果资源未找到则返回空过滤器；如果读取过程中发生错误则返回已加载的部分内容
     */
    public static SensitiveWordFilter fromClasspath(String resourcePath) {
        Set<String> words = new HashSet<>();
        try (InputStream is = SensitiveWordFilter.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        words.add(trimmed);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load sensitive word list: {}", resourcePath, e);
        }
        return new SensitiveWordFilter(words);
    }

    /**
     * 使用DFA字典树扫描文本，将每个匹配到的敏感词替换为 {@value #REPLACEMENT}。
     *
     * @param text 待过滤的输入文本；可以为null或空
     * @return 敏感词已被替换的过滤后文本，如果文本为null、空或未加载字典树则返回原文
     */
    public String filter(String text) {
        if (text == null || text.isEmpty() || root.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        int len = text.length();
        int i = 0;

        while (i < len) {
            Map<Character, Object> node = root;
            int matchEnd = -1;
            int j = i;

            while (j < len) {
                char c = text.charAt(j);
                @SuppressWarnings("unchecked")
                Map<Character, Object> next = (Map<Character, Object>) node.get(c);
                if (next == null) {
                    break;
                }
                node = next;
                j++;
                if (node.containsKey('#')) {
                    matchEnd = j;
                }
            }

            if (matchEnd > 0) {
                result.append(REPLACEMENT);
                i = matchEnd;
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 检查给定文本中是否存在任何敏感词。
     *
     * @param text 待检查的文本；可以为null或空
     * @return 如果至少找到一个敏感词则返回 {@code true}，
     *         否则返回 {@code false}（包括文本为null/空的情况）
     */
    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty() || root.isEmpty()) {
            return false;
        }
        return !filter(text).equals(text);
    }
}