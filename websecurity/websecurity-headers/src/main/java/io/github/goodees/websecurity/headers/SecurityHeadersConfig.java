package io.github.goodees.websecurity.headers;

import java.net.URI;

/**
 * Configuration for standard security headers
 * @author patrik
 */
public interface SecurityHeadersConfig {

    /**
     * Sensible defaults of security headers. {@linkplain #isHstsEnabled(java.lang.String) HSTS} is disabled,
     * {@linkplain #getAntiClickJackingOption() X-Frame-Options} is Same-Origin.
     */
    static final SecurityHeadersConfig DEFAULT_CONFIG = new DefaultSecurityHeadersConfig();
    
    /**
     * Value for X-Frame-Options header
     * @see <a href="https://www.owasp.org/index.php/Clickjacking_Defense_Cheat_Sheet#Defending_with_X-Frame-Options_Response_Headers">OWASP reference on X-Frame-Options</a>
     * @return the value of the header that should be  
     */
    XFrameOption getAntiClickJackingOption();

    /**
     * URI in case {@link #getAntiClickJackingOption()} returns {@code ALLOW_FROM}.
     * @return the allow-from value
     */
    URI getAntiClickJackingUri();

    /**
     * Determine, whether X-Frame-Options header should be sent
     * @return true, when header should be sent
     * @see <a href="https://www.owasp.org/index.php/Clickjacking_Defense_Cheat_Sheet#Defending_with_X-Frame-Options_Response_Headers">OWASP reference on X-Frame-Options</a>
     */
    boolean isAntiClickJackingEnabled();

    /**
     * Determine whether X-Content-Type-Options=nosniff should be sent.
     * @return 
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Content-Type-Options">Documentation on X-Content-Type-Options</a>
     */
    boolean isBlockContentTypeSniffingEnabled();

    /**
     * Indicate, whether header Strict-Transport-Security should be sent out given domain of the request.
     * Since this header have impact on whether the browser should display a page (e. g. at localhost), the implementation
     * should check if the domain of the request is a live one.
     * @param domain the domain (serverName) of the request
     * @return true if HSTS header should be returned.
     * @see <a href="https://www.owasp.org/index.php/HTTP_Strict_Transport_Security_Cheat_Sheet">OWASP wiki on HSTS</a>
     */
    boolean isHstsEnabled(String domain);

    /**
     * When HSTS is enabled, signal if the also subdomains should be affected.
     * @return true, if HSTS header should include {@code includeSubdomains}
     */
    boolean isHstsIncludeSubDomains();
    
    /**
     * When HSTS enabled, the validity of the claim.
     * @return validity of the claim, in seconds
     */
    int getHstsMaxAgeSeconds();

    /**
     * Whether X-XSS-Protection header should be sent with value of {@code 1; mode=block}
     * @return true, if response should have header X-XSS-Protection
     * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-XSS-Protection">Documentation on X-XSS-Protection</a>
     */
    boolean isXssProtectionEnabled();

    /**
     * Values for X-Frame-Options.
     * @see <a href="https://www.owasp.org/index.php/Clickjacking_Defense_Cheat_Sheet#Defending_with_X-Frame-Options_Response_Headers">OWASP reference on X-Frame-Options</a>
     */
    enum XFrameOption {
        DENY("DENY"),
        SAME_ORIGIN("SAMEORIGIN"),
        ALLOW_FROM("ALLOW-FROM");


        final String headerValue;

        XFrameOption(String headerValue) {
            this.headerValue = headerValue;
        }

        public String getHeaderValue() {
            return headerValue;
        }
    }
    
}

