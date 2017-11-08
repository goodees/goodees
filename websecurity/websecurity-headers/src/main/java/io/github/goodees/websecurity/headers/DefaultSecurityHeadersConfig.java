package io.github.goodees.websecurity.headers;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of SecurityHeadersConfig, useful for subclassing and producing in the hosting application.
 */
public class DefaultSecurityHeadersConfig implements SecurityHeadersConfig {
    
    
    private boolean hstsEnabled = false;
    private int hstsMaxAgeSeconds = (int) TimeUnit.HOURS.toSeconds(36);
    private boolean hstsIncludeSubDomains = false;
    private String hstsHeaderValue;

    // Click-jacking protection
    private boolean antiClickJackingEnabled = true;
    private XFrameOption antiClickJackingOption = XFrameOption.SAME_ORIGIN;
    private URI antiClickJackingUri;

    // Block content sniffing
    private boolean blockContentTypeSniffingEnabled = true;

    // Cross-site scripting filter protection
    private boolean xssProtectionEnabled = true;

    @Override
    public boolean isHstsEnabled(String domain) {
        return hstsEnabled;
    }

    @Override
    public int getHstsMaxAgeSeconds() {
        return hstsMaxAgeSeconds;
    }

    @Override
    public boolean isHstsIncludeSubDomains() {
        return hstsIncludeSubDomains;
    }

    @Override
    public boolean isAntiClickJackingEnabled() {
        return antiClickJackingEnabled;
    }

    @Override
    public XFrameOption getAntiClickJackingOption() {
        return antiClickJackingOption;
    }

    @Override
    public boolean isBlockContentTypeSniffingEnabled() {
        return blockContentTypeSniffingEnabled;
    }

    @Override
    public boolean isXssProtectionEnabled() {
        return xssProtectionEnabled;
    }

    @Override
    public URI getAntiClickJackingUri() {
        return antiClickJackingUri;
    }

    public void setAntiClickJackingUri(URI antiClickJackingUri) {
        this.antiClickJackingUri = antiClickJackingUri;
    }

    public void setHstsEnabled(boolean hstsEnabled) {
        this.hstsEnabled = hstsEnabled;
    }

    public void setHstsMaxAgeSeconds(int hstsMaxAgeSeconds) {
        this.hstsMaxAgeSeconds = hstsMaxAgeSeconds;
    }

    public void setHstsIncludeSubDomains(boolean hstsIncludeSubDomains) {
        this.hstsIncludeSubDomains = hstsIncludeSubDomains;
    }

    public void setAntiClickJackingEnabled(boolean antiClickJackingEnabled) {
        this.antiClickJackingEnabled = antiClickJackingEnabled;
    }

    public void setAntiClickJackingOption(XFrameOption antiClickJackingOption) {
        this.antiClickJackingOption = antiClickJackingOption;
    }

    public void setBlockContentTypeSniffingEnabled(boolean blockContentTypeSniffingEnabled) {
        this.blockContentTypeSniffingEnabled = blockContentTypeSniffingEnabled;
    }

    public void setXssProtectionEnabled(boolean xssProtectionEnabled) {
        this.xssProtectionEnabled = xssProtectionEnabled;
    }

    @Override
    public String toString() {
        return "SecurityFiltersConfig{" + "hstsEnabled=" + hstsEnabled + ", hstsMaxAgeSeconds=" + hstsMaxAgeSeconds + 
                ", hstsIncludeSubDomains=" + hstsIncludeSubDomains + ", hstsHeaderValue=" + hstsHeaderValue + 
                ", antiClickJackingEnabled=" + antiClickJackingEnabled + ", antiClickJackingOption=" + 
                antiClickJackingOption + ", antiClickJackingUri=" + antiClickJackingUri + 
                ", blockContentTypeSniffingEnabled=" + blockContentTypeSniffingEnabled + ", xssProtectionEnabled=" + 
                xssProtectionEnabled + '}';
    }
    
    
}
