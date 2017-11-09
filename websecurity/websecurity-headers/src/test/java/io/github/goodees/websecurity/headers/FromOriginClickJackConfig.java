package io.github.goodees.websecurity.headers;

import java.net.URI;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 */
@ApplicationScoped
public class FromOriginClickJackConfig extends DefaultSecurityHeadersConfig {

    {
        setAntiClickJackingOption(SecurityHeadersConfig.XFrameOption.ALLOW_FROM);
        setAntiClickJackingUri(URI.create("http://otherhost.local"));
    }
    
}
