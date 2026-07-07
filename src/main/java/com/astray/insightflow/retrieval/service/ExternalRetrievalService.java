package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.retrieval.domain.EvidenceRecord;
import com.astray.insightflow.retrieval.domain.EvidenceSourceType;
import com.astray.insightflow.retrieval.model.Evidence;
import com.astray.insightflow.retrieval.persistence.EvidenceRecordRepository;
import com.astray.insightflow.tool.WebFetchTool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExternalRetrievalService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final Pattern ENGLISH_TOKEN_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+._-]{1,}");
    private static final String[] POWER_GRID_SITES = {
            "nea.gov.cn", "stategrid.com.cn", "sgcc.com.cn", "csg.cn", "cec.org.cn"
    };
    private static final String[] DOMAIN_HINTS = {
            "电网", "国家电网", "南方电网", "特高压", "输电", "配电网", "电力市场",
            "调度", "新能源", "储能", "光伏", "风电", "竞品", "市场份额", "融资",
            "专利", "营收", "毛利", "客户", "自动化", "数字电网", "虚拟电厂"
    };
    private static final String[] GENERIC_TOKENS = {
            "请", "调研", "分析", "研究", "生成", "一份", "报告", "中文", "带引用",
            "置信度", "关于", "中国", "历史", "现状", "未来", "趋势", "结构", "说明"
    };

    private final WebFetchTool webFetchTool;
    private final EvidenceRecordRepository evidenceRecordRepository;
    private final int maxExternalResultsPerQuery;
    private final int maxExternalPages;
    private final int timeoutMs;
    private final String userAgent;

    public ExternalRetrievalService(WebFetchTool webFetchTool,
                                    EvidenceRecordRepository evidenceRecordRepository,
                                    AgentProperties agentProperties) {
        this.webFetchTool = webFetchTool;
        this.evidenceRecordRepository = evidenceRecordRepository;
        this.maxExternalResultsPerQuery = agentProperties.search().maxExternalResultsPerQuery();
        this.maxExternalPages = agentProperties.search().maxExternalPages();
        this.timeoutMs = agentProperties.search().timeoutMs();
        this.userAgent = agentProperties.search().userAgent();
    }

    @Transactional
    public List<Evidence> search(String taskId, List<String> queries, boolean enabled) {
        if (!enabled || queries == null || queries.isEmpty()) {
            evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.EXTERNAL);
            return List.of();
        }

        Map<String, SearchHit> deduplicatedHits = new LinkedHashMap<>();
        for (String query : queries) {
            if (!StringUtils.hasText(query)) {
                continue;
            }
            for (String searchQuery : expandSearchQueries(query.trim())) {
                List<SearchHit> hits = searchExternalSources(query.trim(), searchQuery);
                for (SearchHit hit : hits) {
                    if (!isRelevantHit(query, hit.title(), hit.snippet(), hit.normalizedUrl())) {
                        continue;
                    }
                    deduplicatedHits.putIfAbsent(hit.normalizedUrl(), hit);
                    if (deduplicatedHits.size() >= maxExternalPages * 2) {
                        break;
                    }
                }
                if (deduplicatedHits.size() >= maxExternalPages * 2) {
                    break;
                }
            }
            if (deduplicatedHits.size() >= maxExternalPages * 2) {
                break;
            }
        }

        List<Evidence> result = new ArrayList<>();
        int rank = 0;
        for (SearchHit hit : deduplicatedHits.values()) {
            if (result.size() >= maxExternalPages) {
                break;
            }

            String title = hit.title();
            String url = hit.normalizedUrl();
            String snippet = hit.snippet();
            try {
                WebFetchTool.FetchedPage page = webFetchTool.fetchPage(taskId, "retrieveExternal", url);
                if (StringUtils.hasText(page.title())) {
                    title = page.title();
                }
                if (StringUtils.hasText(page.finalUrl())) {
                    url = page.finalUrl();
                }
                if (StringUtils.hasText(page.content())) {
                    snippet = page.content();
                }
            } catch (Exception ignored) {
                if (!StringUtils.hasText(snippet)) {
                    continue;
                }
            }

            if (!StringUtils.hasText(snippet)) {
                continue;
            }
            if (!isRelevantHit(hit.query(), title, snippet, url)) {
                continue;
            }

            Evidence evidence = new Evidence();
            evidence.setId(UUID.randomUUID().toString());
            evidence.setTitle(truncate(title, 220));
            evidence.setSnippet(truncate(snippet, 3800));
            evidence.setUrl(url);
            evidence.setSourceType(EvidenceSourceType.EXTERNAL);
            evidence.setScore(score(hit.query(), title, snippet, url, rank));
            result.add(evidence);
            rank++;
        }

        evidenceRecordRepository.deleteByTaskIdAndSourceType(taskId, EvidenceSourceType.EXTERNAL);
        evidenceRecordRepository.saveAll(result.stream().map(evidence -> toRecord(taskId, evidence)).toList());
        return result;
    }

    private List<String> expandSearchQueries(String query) {
        List<String> expanded = new ArrayList<>();
        expanded.add(query);
        if (isPowerGridQuery(query)) {
            for (String site : POWER_GRID_SITES) {
                expanded.add("site:" + site + " " + query);
            }
        }
        return expanded;
    }

    private List<SearchHit> searchExternalSources(String originalQuery, String searchQuery) {
        List<SearchHit> hits = searchBing(originalQuery, searchQuery);
        if (!hits.isEmpty()) {
            return hits;
        }
        return searchDuckDuckGo(originalQuery, searchQuery);
    }

    List<SearchHit> searchBing(String originalQuery, String searchQuery) {
        try {
            Document document = Jsoup.connect("https://cn.bing.com/search")
                    .data("q", searchQuery)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://www.bing.com/")
                    .get();

            return parseBingResults(document, originalQuery);
        } catch (IOException exception) {
            return List.of();
        }
    }

    List<SearchHit> parseBingResults(Document document, String query) {
        List<SearchHit> hits = new ArrayList<>();
        for (Element result : document.select("li.b_algo")) {
            Element link = result.selectFirst("h2 a[href]");
            if (link == null) {
                continue;
            }

            String title = normalizeWhitespace(link.text());
            String url = decodeRedirectUrl(link.attr("href"));
            if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                continue;
            }

            String snippet = normalizeWhitespace(result.select(".b_caption p, .b_caption .b_lineclamp2").text());
            String normalizedUrl = normalizeUrl(url);
            if (!StringUtils.hasText(normalizedUrl)) {
                continue;
            }

            hits.add(new SearchHit(title, normalizedUrl, snippet, query));
            if (hits.size() >= maxExternalResultsPerQuery) {
                break;
            }
        }
        return hits;
    }

    private List<SearchHit> searchDuckDuckGo(String originalQuery, String searchQuery) {
        try {
            Document document = Jsoup.connect("https://html.duckduckgo.com/html/")
                    .data("q", searchQuery)
                    .data("kl", "cn-zh")
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://duckduckgo.com/")
                    .get();

            List<SearchHit> hits = new ArrayList<>();
            for (Element result : document.select(".result")) {
                Element link = result.selectFirst("a.result__a");
                if (link == null) {
                    continue;
                }
                String title = normalizeWhitespace(link.text());
                String url = decodeRedirectUrl(link.attr("href"));
                if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                    continue;
                }

                String snippet = normalizeWhitespace(result.select(".result__snippet").text());
                String normalizedUrl = normalizeUrl(url);
                if (!StringUtils.hasText(normalizedUrl)) {
                    continue;
                }

                hits.add(new SearchHit(title, normalizedUrl, snippet, originalQuery));
                if (hits.size() >= maxExternalResultsPerQuery) {
                    break;
                }
            }
            return hits;
        } catch (IOException exception) {
            return List.of();
        }
    }

    private String decodeRedirectUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return null;
        }
        String candidate = href.startsWith("//") ? "https:" + href : href;
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() != null && (uri.getHost().contains("duckduckgo.com") || uri.getHost().contains("bing.com"))) {
                String query = uri.getRawQuery();
                if (query != null) {
                    for (String part : query.split("&")) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2 && ("uddg".equals(kv[0]) || "u".equals(kv[0]) || "url".equals(kv[0]))) {
                            return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                        }
                    }
                }
            }
            return candidate;
        } catch (URISyntaxException exception) {
            return candidate;
        }
    }

    private String normalizeUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return rawUrl.trim();
            }
            String normalizedScheme = StringUtils.hasText(scheme) ? scheme.toLowerCase(Locale.ROOT) : "https";
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            String query = uri.getQuery();
            URI normalized = new URI(normalizedScheme, uri.getUserInfo(), normalizedHost, uri.getPort(), path, query, null);
            return normalized.toString();
        } catch (URISyntaxException exception) {
            return rawUrl.trim();
        }
    }

    boolean isRelevantHit(String query, String title, String snippet, String url) {
        List<QuerySignal> signals = extractSignals(query);
        if (signals.isEmpty()) {
            return true;
        }
        String titleText = normalizeWhitespace(title).toLowerCase(Locale.ROOT);
        String snippetText = normalizeWhitespace(snippet).toLowerCase(Locale.ROOT);
        String urlText = normalizeWhitespace(url).toLowerCase(Locale.ROOT);

        int strongMatches = 0;
        int domainMatches = 0;
        double score = 0D;
        for (QuerySignal signal : signals) {
            if (contains(titleText, signal.text())) {
                score += signal.strong() ? 1.2D : 0.5D;
                strongMatches += signal.strong() ? 1 : 0;
                domainMatches += signal.domain() ? 1 : 0;
                continue;
            }
            if (contains(snippetText, signal.text())) {
                score += signal.strong() ? 0.7D : 0.3D;
                strongMatches += signal.strong() ? 1 : 0;
                domainMatches += signal.domain() ? 1 : 0;
                continue;
            }
            if (contains(urlText, signal.text())) {
                score += signal.strong() ? 0.85D : 0.25D;
                strongMatches += signal.strong() ? 1 : 0;
                domainMatches += signal.domain() ? 1 : 0;
            }
        }
        return domainMatches >= 1 && (strongMatches >= 1 || score >= 1.4D);
    }

    private double score(String query, String title, String snippet, String url, int rank) {
        double score = 0.86D - (rank * 0.05D);
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".gov") || lower.contains(".edu")) {
            score += 0.08D;
        } else if (lower.contains(".org") || lower.contains("news") || lower.contains("press") || lower.contains("marklines")) {
            score += 0.04D;
        } else if (lower.contains("zhihu") || lower.contains("toutiao") || lower.contains("weibo")) {
            score -= 0.05D;
        }
        if (isRelevantHit(query, title, snippet, url)) {
            score += 0.06D;
        }
        return Math.max(0.42D, Math.min(0.95D, score));
    }

    private List<QuerySignal> extractSignals(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }
        String cleaned = normalizeWhitespace(query).toLowerCase(Locale.ROOT);
        Set<QuerySignal> signals = new LinkedHashSet<>();

        for (String hint : DOMAIN_HINTS) {
            if (contains(cleaned, hint)) {
                signals.add(new QuerySignal(hint.toLowerCase(Locale.ROOT), true, true));
            }
        }

        for (String token : cleaned.split("[\\s，。！？；、/\\\\|()（）:：\"“”《》\\-–—]+")) {
            addSignal(signals, token, false, inferDomainToken(token));
            for (String fragment : token.split("(?:的|与|和|及|以及|围绕|针对|请|调研|分析|生成|报告|发展|现状|未来|趋势|结构|说明)")) {
                addSignal(signals, fragment, false, inferDomainToken(fragment));
            }
        }

        Matcher yearMatcher = YEAR_PATTERN.matcher(cleaned);
        while (yearMatcher.find()) {
            signals.add(new QuerySignal(yearMatcher.group(), true, false));
        }

        Matcher englishMatcher = ENGLISH_TOKEN_PATTERN.matcher(cleaned);
        while (englishMatcher.find()) {
            signals.add(new QuerySignal(englishMatcher.group().toLowerCase(Locale.ROOT), true, true));
        }

        return new ArrayList<>(signals);
    }

    private void addSignal(Set<QuerySignal> signals, String rawToken, boolean forceStrong, boolean domain) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        String token = rawToken.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(token) || isGenericToken(token) || token.length() < 2 || token.length() > 24) {
            return;
        }
        boolean strong = forceStrong || token.length() >= 4 || containsDigit(token);
        signals.add(new QuerySignal(token, strong, domain));
    }

    private boolean inferDomainToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches("20\\d{2}(年|([qQ][1-4])|([qQ][1-4]年))?")) {
            return false;
        }
        if (containsAny(normalized, DOMAIN_HINTS)) {
            return true;
        }
        return normalized.chars().anyMatch(Character::isLetter);
    }

    private boolean isPowerGridQuery(String query) {
        return containsAny(query == null ? "" : query.toLowerCase(Locale.ROOT), POWER_GRID_SITES) || containsAny(query, new String[]{
                "电网", "国家电网", "南方电网", "特高压", "输电", "配电网", "电力"
        });
    }

    private boolean containsAny(String text, String[] candidates) {
        if (!StringUtils.hasText(text) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isGenericToken(String token) {
        if (!StringUtils.hasText(token)) {
            return true;
        }
        for (String genericToken : GENERIC_TOKENS) {
            if (genericToken.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isDigit(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String text, String token) {
        return StringUtils.hasText(text) && StringUtils.hasText(token) && text.contains(token.toLowerCase(Locale.ROOT));
    }

    private String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private EvidenceRecord toRecord(String taskId, Evidence evidence) {
        EvidenceRecord record = new EvidenceRecord();
        record.setId(evidence.getId());
        record.setTaskId(taskId);
        record.setSourceType(evidence.getSourceType());
        record.setTitle(evidence.getTitle());
        record.setSnippet(evidence.getSnippet());
        record.setUrl(evidence.getUrl());
        record.setDocumentId(evidence.getDocumentId());
        record.setChunkId(evidence.getChunkId());
        record.setScore(evidence.getScore());
        record.setCreatedAt(Instant.now());
        return record;
    }

    record SearchHit(String title, String normalizedUrl, String snippet, String query) {
    }

    record QuerySignal(String text, boolean strong, boolean domain) {
    }
}
