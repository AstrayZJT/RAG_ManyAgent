package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(20)
public class BingHtmlSearchProvider implements SearchProvider {

    private final int maxResults;
    private final int timeoutMs;
    private final String userAgent;

    public BingHtmlSearchProvider(AgentProperties agentProperties) {
        this.maxResults = agentProperties.search().maxExternalResultsPerQuery();
        this.timeoutMs = agentProperties.search().timeoutMs();
        this.userAgent = agentProperties.search().userAgent();
    }

    @Override
    public String name() {
        return "bing-html";
    }

    @Override
    public List<SearchHit> search(String originalQuery, String searchQuery) {
        try {
            Document document = Jsoup.connect("https://cn.bing.com/search")
                    .data("q", searchQuery)
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://www.bing.com/")
                    .get();
            return parse(document, originalQuery);
        } catch (IOException exception) {
            return List.of();
        }
    }

    List<SearchHit> parse(Document document, String query) {
        List<SearchHit> hits = new ArrayList<>();
        for (Element result : document.select("li.b_algo")) {
            Element link = result.selectFirst("h2 a[href]");
            if (link == null) {
                continue;
            }
            String title = SearchUrlSupport.normalizeWhitespace(link.text());
            String url = SearchUrlSupport.normalizeUrl(SearchUrlSupport.decodeRedirectUrl(link.attr("href")));
            if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                continue;
            }
            String snippet = SearchUrlSupport.normalizeWhitespace(
                    result.select(".b_caption p, .b_caption .b_lineclamp2").text()
            );
            hits.add(new SearchHit(title, url, snippet, query));
            if (hits.size() >= maxResults) {
                break;
            }
        }
        return hits;
    }
}
