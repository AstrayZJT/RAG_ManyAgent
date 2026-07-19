package com.astray.insightflow.retrieval.service;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class SearchUrlSupport {

    private SearchUrlSupport() {
    }

    static String resolveSogouUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return null;
        }
        String trimmed = href.trim();
        if (trimmed.startsWith("//")) {
            trimmed = "https:" + trimmed;
        }
        if (trimmed.startsWith("/")) {
            trimmed = "https://www.sogou.com" + trimmed;
        }
        return decodeRedirectUrl(trimmed);
    }

    static String decodeRedirectUrl(String href) {
        if (!StringUtils.hasText(href)) {
            return null;
        }
        String candidate = href.startsWith("//") ? "https:" + href : href;
        try {
            URI uri = new URI(candidate);
            if (uri.getHost() != null
                    && uri.getHost().contains("sogou.com")
                    && StringUtils.hasText(uri.getPath())
                    && uri.getPath().contains("/link")) {
                String redirected = queryParameter(uri, "url", "u", "target");
                if (StringUtils.hasText(redirected)
                        && (redirected.startsWith("http://") || redirected.startsWith("https://"))) {
                    return redirected;
                }
            }
            if (uri.getHost() != null
                    && (uri.getHost().contains("duckduckgo.com") || uri.getHost().contains("bing.com"))) {
                String redirected = queryParameter(uri, "uddg", "u", "url");
                if (StringUtils.hasText(redirected)) {
                    return redirected;
                }
            }
            return candidate;
        } catch (URISyntaxException exception) {
            return candidate;
        }
    }

    static String normalizeUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String host = uri.getHost();
            if (!StringUtils.hasText(host)) {
                return rawUrl.trim();
            }
            String scheme = StringUtils.hasText(uri.getScheme()) ? uri.getScheme().toLowerCase(Locale.ROOT) : "https";
            URI normalized = new URI(
                    scheme,
                    uri.getUserInfo(),
                    host.toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    uri.getPath() == null ? "" : uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return normalized.toString();
        } catch (URISyntaxException exception) {
            return rawUrl.trim();
        }
    }

    static String normalizeWhitespace(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private static String queryParameter(URI uri, String... names) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        for (String part : uri.getRawQuery().split("&")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            for (String name : names) {
                if (name.equals(keyValue[0])) {
                    return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
