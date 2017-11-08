/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.goodees.websecurity.headers;

import java.net.URI;
import javax.enterprise.context.ApplicationScoped;

/**
 *
 * @author patrik
 */
@ApplicationScoped
public class FromOriginClickJackConfig extends DefaultSecurityHeadersConfig {

    {
        setAntiClickJackingOption(SecurityHeadersConfig.XFrameOption.ALLOW_FROM);
        setAntiClickJackingUri(URI.create("http://otherhost.local"));
    }
    
}
