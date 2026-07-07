package com.astray.insightflow.tool;

import com.astray.insightflow.config.AgentProperties;
import com.astray.insightflow.observe.service.ToolCallLogService;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

@Component
public class WebFetchTool {

    private final int maxCharacters;
    private final int minCharacters;
    private final int timeoutMs;
    private final String userAgent;
    private final ToolCallLogService toolCallLogService;

    public WebFetchTool(AgentProperties agentProperties, ToolCallLogService toolCallLogService) {
        this.maxCharacters = agentProperties.webpage().maxCharacters();
        this.minCharacters = agentProperties.webpage().minCharacters();
        this.timeoutMs = agentProperties.search().timeoutMs();
        this.userAgent = agentProperties.search().userAgent();
        this.toolCallLogService = toolCallLogService;
    }

    @Tool("Fetches and cleans webpage content by URL")
    public String fetch(String taskId, String nodeName, String url) {
        return fetchPage(taskId, nodeName, url).content();
    }

    public FetchedPage fetchPage(String taskId, String nodeName, String url) {
        Instant startedAt = Instant.now();
        Map<String, Object> input = Map.of("url", url);
        try {
            Connection.Response response = Jsoup.connect(normalizeUrl(url))
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://duckduckgo.com/")
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .maxBodySize(0)
                    .execute();

            Document document = response.parse();
            document.select("script, style, noscript, svg, canvas, iframe, form, nav, footer, header, aside").remove();
            document.select("[role=banner], [role=navigation], [role=complementary], [role=contentinfo]").remove();
            document.select(".footer, .header, .nav, .sidebar, .advertisement, .ads").remove();

            Element main = firstNonNull(
                    document.selectFirst("article"),
                    document.selectFirst("main"),
                    document.selectFirst(".article"),
                    document.selectFirst(".post"),
                    document.selectFirst(".content"),
                    document.body()
            );

            String content = normalizeWhitespace(main == null ? "" : main.text());
            if (content.length() < minCharacters && document.body() != null) {
                content = normalizeWhitespace(document.body().text());
            }

            String trimmed = content.length() > maxCharacters ? content.substring(0, maxCharacters) + "..." : content;
            String finalUrl = response.url() == null ? normalizeUrl(url) : response.url().toString();
            String title = StringUtils.hasText(document.title()) ? normalizeWhitespace(document.title()) : finalUrl;
            FetchedPage page = new FetchedPage(finalUrl, title, trimmed);

            toolCallLogService.logSuccess(taskId, nodeName, "WebFetchTool", startedAt, input, Map.of(
                    "url", page.finalUrl(),
                    "title", page.title(),
                    "content", page.content()
            ), Map.of(
                    "maxCharacters", maxCharacters,
                    "contentLength", page.content().length()
            ));
            return page;
        } catch (Exception exception) {
            toolCallLogService.logFailure(taskId, nodeName, "WebFetchTool", startedAt, input, exception);
            throw new IllegalStateException("Failed to fetch webpage: " + url, exception);
        }
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Web page url must not be blank");
        }
        return url.startsWith("//") ? "https:" + url : url.trim();
    }

    private String normalizeWhitespace(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        return Arrays.stream(values).filter(value -> value != null).findFirst().orElse(null);
    }

    public record FetchedPage(String finalUrl, String title, String content) {
    }
}
