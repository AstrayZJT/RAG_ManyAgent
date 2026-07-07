package com.astray.insightflow.retrieval.service;

import com.astray.insightflow.config.AgentProperties;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRetrievalServiceTests {

    private final ExternalRetrievalService service = new ExternalRetrievalService(
            null,
            null,
            new AgentProperties(
                    new AgentProperties.Search(5, 4, 8, 15000, "InsightFlow-Test"),
                    new AgentProperties.Webpage(8000, 240)
            )
    );

    @Test
    void parseBingResultsExtractsSearchHits() {
        String html = """
                <html>
                  <body>
                    <ol>
                      <li class="b_algo">
                        <h2><a href="https://example.com/a">Example A</a></h2>
                        <div class="b_caption"><p>Alpha snippet</p></div>
                      </li>
                      <li class="b_algo">
                        <h2><a href="https://example.com/b">Example B</a></h2>
                        <div class="b_caption"><p>Beta snippet</p></div>
                      </li>
                    </ol>
                  </body>
                </html>
                """;

        List<ExternalRetrievalService.SearchHit> hits = service.parseBingResults(Jsoup.parse(html), "test query");

        assertEquals(2, hits.size());
        assertEquals("Example A", hits.get(0).title());
        assertEquals("https://example.com/a", hits.get(0).normalizedUrl());
        assertEquals("Alpha snippet", hits.get(0).snippet());
        assertEquals("test query", hits.get(0).query());
    }

    @Test
    void parseSogouResultsExtractsSearchHits() {
        String html = """
                <html>
                  <body>
                    <div class="vrwrap">
                      <h3 class="vr-title">
                        <a href="/link?url=abc">国家电网公司历史沿革</a>
                      </h3>
                      <div class="fz-mid space-txt">2002年国务院实施电力体制改革，组建国家电网公司。</div>
                    </div>
                    <div class="vrwrap">
                      <h3 class="vr-title">
                        <a href="https://baike.sogou.com/v167368.htm">国家电网有限公司</a>
                      </h3>
                      <p class="star-wiki">近20多年来，国家电网建成多项特高压输电工程。</p>
                    </div>
                  </body>
                </html>
                """;

        List<ExternalRetrievalService.SearchHit> hits = service.parseSogouResults(Jsoup.parse(html), "国家电网 发展历史");

        assertEquals(2, hits.size());
        assertEquals("国家电网公司历史沿革", hits.get(0).title());
        assertEquals("https://www.sogou.com/link?url=abc", hits.get(0).normalizedUrl());
        assertTrue(hits.get(0).snippet().contains("2002年"));
        assertEquals("国家电网 发展历史", hits.get(0).query());
    }

    @Test
    void isRelevantHitKeepsTopicAnchoredPowerGridResults() {
        boolean relevant = service.isRelevantHit(
                "中国电网 特高压 输电 发展历史",
                "国家电网持续推进特高压工程建设",
                "国家电网披露跨区输电与特高压线路最新进展。",
                "https://www.stategrid.com.cn/article"
        );

        assertTrue(relevant);
    }

    @Test
    void isRelevantHitRejectsGenericNoiseResults() {
        boolean relevant = service.isRelevantHit(
                "关于加快建设全国统一电力市场体系的指导意见 电网 调度",
                "关于（汉语词语）_百度百科",
                "关于是常用介词，用于引进行为涉及的对象或范围。",
                "https://baike.baidu.com/item/%E5%85%B3%E4%BA%8E/81002"
        );

        assertFalse(relevant);
    }

    @Test
    void isRelevantHitDoesNotTreatBareYearAsTopicAnchor() {
        boolean relevant = service.isRelevantHit(
                "中国电网 2020 年 发展历史",
                "2020年国内十大新闻_百度百科",
                "系统梳理该年度中国在政治、经济、科技领域的重大事件。",
                "https://baike.baidu.com/item/2020%E5%B9%B4%E5%9B%BD%E5%86%85%E5%8D%81%E5%A4%A7%E6%96%B0%E9%97%BB/55630783"
        );

        assertFalse(relevant);
    }

    @Test
    void parseSogouResultsPrefersCanonicalDataUrlWhenPresent() {
        String html = """
                <html>
                  <body>
                    <div class="vrwrap">
                      <h3 class="vr-title">
                        <a href="/link?url=abc">Redirected Title</a>
                      </h3>
                      <div class="r-sech ext_query result_list" data-url="https://example.com/final-article"></div>
                      <div class="fz-mid space-txt">Canonical snippet</div>
                    </div>
                  </body>
                </html>
                """;

        List<ExternalRetrievalService.SearchHit> hits = service.parseSogouResults(Jsoup.parse(html), "demo query");

        assertEquals(1, hits.size());
        assertEquals("Redirected Title", hits.get(0).title());
        assertEquals("https://example.com/final-article", hits.get(0).normalizedUrl());
        assertEquals("Canonical snippet", hits.get(0).snippet());
    }
}
