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
@Order(10)
public class SogouHtmlSearchProvider implements SearchProvider {

    private final int maxResults;
    private final int timeoutMs;
    private final String userAgent;

    public SogouHtmlSearchProvider(AgentProperties agentProperties) {
        this.maxResults = agentProperties.search().maxExternalResultsPerQuery();
        this.timeoutMs = agentProperties.search().timeoutMs();
        this.userAgent = agentProperties.search().userAgent();
    }

    @Override
    public String name() {
        return "sogou-html";
    }

    @Override
    public List<SearchHit> search(String originalQuery, String searchQuery) {
        try {
            Document document = Jsoup.connect("https://www.sogou.com/web")
                    .data("query", searchQuery)
                    .data("ie", "utf8")
                    .userAgent(userAgent)
                    .timeout(timeoutMs)
                    .referrer("https://www.sogou.com/")
                    .followRedirects(true)
                    .get();
            return parse(document, originalQuery);
        } catch (IOException exception) {
            return List.of();
        }
    }

    List<SearchHit> parse(Document document, String query) {
        List<SearchHit> hits = new ArrayList<>();
        for (Element result : document.select("div.vrwrap")) {
            Element link = result.selectFirst("h3.vr-title a[href], h3 a[href]");
            if (link == null) {
                continue;
            }
            String title = SearchUrlSupport.normalizeWhitespace(link.text());
            String url = resolveResultUrl(result, link);
            if (!StringUtils.hasText(title) || !StringUtils.hasText(url)) {
                continue;
            }
            String snippet = SearchUrlSupport.normalizeWhitespace(
                    result.select(".fz-mid.space-txt, .b_caption p, p.star-wiki").text()
            );
            if (!StringUtils.hasText(snippet)) {
                snippet = SearchUrlSupport.normalizeWhitespace(result.select(".citeLinkClass").text());
            }
            hits.add(new SearchHit(title, url, snippet, query));
            if (hits.size() >= maxResults) {
                break;
            }
        }
        return hits;
    }

    private String resolveResultUrl(Element result, Element link) {
        Element canonicalElement = result.selectFirst(".r-sech[data-url], .ext_query[data-url], .result_list[data-url], [data-url]");
        if (canonicalElement != null && StringUtils.hasText(canonicalElement.attr("data-url"))) {
            return SearchUrlSupport.normalizeUrl(canonicalElement.attr("data-url"));
        }
        Element citeLink = result.selectFirst("a.citeLinkClass[href]");
        if (citeLink != null) {
            String decoded = SearchUrlSupport.decodeRedirectUrl(citeLink.attr("href"));
            if (StringUtils.hasText(decoded)) {
                return SearchUrlSupport.normalizeUrl(decoded);
            }
        }
        return SearchUrlSupport.normalizeUrl(SearchUrlSupport.resolveSogouUrl(link.attr("href")));
    }
}
