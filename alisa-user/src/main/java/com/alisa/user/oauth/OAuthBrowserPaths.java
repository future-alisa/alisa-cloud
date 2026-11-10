package com.alisa.user.oauth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.HtmlUtils;

/** Uses the servlet context established by the trusted proxy, never raw headers. */
public final class OAuthBrowserPaths {
    private OAuthBrowserPaths() {}
    private static String prefix() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) return "";
        return checkedPrefix(servlet.getRequest().getContextPath());
    }
    private static String checkedPrefix(String prefix) {
        if (prefix.isEmpty()) return "";
        if (!prefix.matches("(?:/[A-Za-z0-9._~-]+)+")) throw new IllegalStateException("Invalid OAuth context path");
        for (String segment : prefix.split("/"))
            if (segment.equals(".") || segment.equals("..")) throw new IllegalStateException("Invalid OAuth context path");
        return prefix;
    }
    public static String path(String path) {
        if (!path.startsWith("/") || path.startsWith("//")) throw new IllegalArgumentException("Expected a local absolute path");
        return prefix() + path;
    }
    public static String path(HttpServletRequest request,String path) {
        if (!path.startsWith("/") || path.startsWith("//")) throw new IllegalArgumentException("Expected a local absolute path");
        return checkedPrefix(request.getContextPath()) + path;
    }
    /** Chromium applies form-action to redirects after a form submission. */
    static String formCallbackSources(Collection<String> redirects) {
        return redirects.stream().map(OAuthBrowserPaths::formCallbackSource)
            .filter(source -> !source.isEmpty()).distinct().collect(Collectors.joining());
    }

    private static String formCallbackSource(String redirect) {
        if (OAuthApplications.SMALLOKR_NATIVE_CALLBACK.equals(redirect)) return " com.sunyubo.smallokr:";
        URI uri;
        try {
            uri = URI.create(redirect);
        } catch (IllegalArgumentException invalid) {
            return "";
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || host.contains("%")) return "";
        if (!"https".equals(uri.getScheme())
                && !("http".equals(uri.getScheme()) && Set.of("127.0.0.1", "localhost", "[::1]").contains(host))) return "";
        return " " + uri.getScheme() + "://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    }

    /** Only generated, HTML-escaped OAuth page markup enters this boundary. */
    public static String html(String generated) {
        String prefix = HtmlUtils.htmlEscape(prefix());
        return generated.replace("href='/oauth", "href='" + prefix + "/oauth")
            .replace("action='/oauth", "action='" + prefix + "/oauth")
            .replace("href=\"/oauth", "href=\"" + prefix + "/oauth")
            .replace("action=\"/oauth", "action=\"" + prefix + "/oauth");
    }
}
