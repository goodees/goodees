/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.goodees.websecurity.headers;

import io.github.goodees.websecurity.common.filter.HttpFilter;
import io.github.goodees.websecurity.headers.SecurityHeadersConfig.XFrameOption;
import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A filter that adds websecurity headers to the responses. By default, it binds to all the responses. To customize the
 * values, produce an instance of {@link SecurityHeadersConfig}. To customize the mapping, change the configuration of
 * filter {@code DefaultSecurityHeadersFilter}, which is also exposed as {@link #FILTER_NAME} to allow for programmatic
 * configuration.
 * 
 */
@WebFilter(urlPatterns = "/*", filterName=SecurityHeadersFilter.FILTER_NAME)
public class SecurityHeadersFilter extends HttpFilter {
    public static final String FILTER_NAME = "DefaultSecurityHeadersFilter";
    private static final Logger logger = LoggerFactory.getLogger(SecurityHeadersFilter.class);

    @Inject
    Instance<SecurityHeadersConfig> appConfig;
    
    SecurityHeadersConfig config;

    // HSTS
    private static final String HSTS_HEADER_NAME = "Strict-Transport-Security";
    private String hstsHeaderValue;

    // Click-jacking protection
    private static final String ANTI_CLICK_JACKING_HEADER_NAME = "X-Frame-Options";
    private String antiClickJackingHeaderValue;

    // Block content sniffing
    private static final String BLOCK_CONTENT_TYPE_SNIFFING_HEADER_NAME = "X-Content-Type-Options";
    private static final String BLOCK_CONTENT_TYPE_SNIFFING_HEADER_VALUE = "nosniff";

    // Cross-site scripting filter protection
    private static final String XSS_PROTECTION_HEADER_NAME = "X-XSS-Protection";
    private static final String XSS_PROTECTION_HEADER_VALUE = "1; mode=block";

    @PostConstruct
    void init() {
        if (!appConfig.isAmbiguous() && !appConfig.isUnsatisfied()) {
            config = appConfig.get();
        } else {
            config = SecurityHeadersConfig.DEFAULT_CONFIG;
            if (appConfig.isAmbiguous()) {
                logger.warn("More than one instance of SecurityHeadersConfig is produced. Falling back to default value");
            }
        }
        // Build HSTS header value
        StringBuilder hstsValue = new StringBuilder("max-age=");
        hstsValue.append(config.getHstsMaxAgeSeconds());
        if (config.isHstsIncludeSubDomains()) {
            hstsValue.append(";includeSubDomains");
        }
        hstsHeaderValue = hstsValue.toString();

        // Anti click-jacking
        StringBuilder cjValue = new StringBuilder(config.getAntiClickJackingOption().headerValue);
        if (config.getAntiClickJackingOption() == XFrameOption.ALLOW_FROM) {
            cjValue.append(' ');
            cjValue.append(config.getAntiClickJackingUri());
        }
        antiClickJackingHeaderValue = cjValue.toString();
    }

    @Override
    public boolean doFilter(HttpServletRequest request, HttpServletResponse httpResponse,
            FilterChain chain) throws IOException, ServletException {

        if (httpResponse.isCommitted()) {
            throw new ServletException("Response already committed");
        }

        // HSTS
        if (request.isSecure() && config.isHstsEnabled(request.getServerName())) {
            httpResponse.setHeader(HSTS_HEADER_NAME, hstsHeaderValue);
        }

        // anti click-jacking
        if (config.isAntiClickJackingEnabled()) {
            httpResponse.setHeader(ANTI_CLICK_JACKING_HEADER_NAME, antiClickJackingHeaderValue);
        }

        // Block content type sniffing
        if (config.isBlockContentTypeSniffingEnabled()) {
            httpResponse.setHeader(BLOCK_CONTENT_TYPE_SNIFFING_HEADER_NAME,
                    BLOCK_CONTENT_TYPE_SNIFFING_HEADER_VALUE);
        }

        // cross-site scripting filter protection
        if (config.isXssProtectionEnabled()) {
            httpResponse.setHeader(XSS_PROTECTION_HEADER_NAME, XSS_PROTECTION_HEADER_VALUE);
        }
        return true; // invoke chain
    }
}
