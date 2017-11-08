package io.github.goodees.websecurity.headers;

import java.io.File;
import java.net.URI;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author patrik
 */
@RunWith(Arquillian.class)
public class SecurityHeadersIT {
    @Deployment(name = "default")
    public static WebArchive defaultHostApp() {
        return ShrinkWrap.create(WebArchive.class)
                .addClass(HelloServlet.class)
                .addAsLibraries(new File("target").listFiles((p,f) -> f.endsWith(".jar")));
    }
    
    @Deployment(name = "fromorigin")
    public static WebArchive defaultHstsHostApp() {
        return ShrinkWrap.create(WebArchive.class)
                .addClass(HelloServlet.class)
                .addClass(FromOriginClickJackConfig.class)
                .addAsLibraries(new File("target").listFiles((p,f) -> f.endsWith(".jar")));
    }
    
    @Deployment(name = "disabled")
    public static WebArchive disabledWebXml() {
        return ShrinkWrap.create(WebArchive.class)
                .addClass(HelloServlet.class)
                .addClass(FromOriginClickJackConfig.class)
                .addAsLibraries(new File("target").listFiles((p,f) -> f.endsWith(".jar")))
                .addAsWebInfResource("disable-filter-web.xml","web.xml");
        
    }    
    
    @Deployment(name = "disabledProg")
    public static WebArchive disabledProgrammatically() {
        return ShrinkWrap.create(WebArchive.class)
                .addClass(HelloServlet.class)
                .addClass(DisablingListener.class)
                .addAsLibraries(new File("target").listFiles((p,f) -> f.endsWith(".jar")));
    }    
    
    @ArquillianResource
    URI baseUri;
    
    @Test @OperateOnDeployment("default") @RunAsClient
    public void without_config_default_settings_are_used() {
        WebTarget target = ClientBuilder.newClient().target(baseUri);
        Response response = target.request().get();
        assertEquals("SAMEORIGIN", response.getHeaderString("X-Frame-Options"));
        assertEquals("nosniff", response.getHeaderString("X-Content-Type-Options"));
        assertEquals("1; mode=block", response.getHeaderString("X-XSS-Protection"));
        assertNull(response.getHeaderString("Strict-Transport-Security"));
    }
    
    // one needs https request to test hsts
    
    @Test @OperateOnDeployment("fromorigin") @RunAsClient
    public void with_custom_configuration_allow_from_is_sent() {
        WebTarget target = ClientBuilder.newClient().target(baseUri);
        Response response = target.request().get();
        assertEquals("ALLOW-FROM http://otherhost.local", response.getHeaderString("X-Frame-Options"));
        assertEquals("nosniff", response.getHeaderString("X-Content-Type-Options"));
        assertEquals("1; mode=block", response.getHeaderString("X-XSS-Protection"));
        assertEquals(null, response.getHeaderString("Strict-Transport-Security"));
    }
    
    @Test @OperateOnDeployment("disabled") @RunAsClient
    public void when_disabled_in_webxml_the_filter_is_disabled() {
        WebTarget target = ClientBuilder.newClient().target(baseUri);
        Response response = target.request().get();
        assertNull(response.getHeaderString("X-Frame-Options"));
        assertNull(response.getHeaderString("X-Content-Type-Options"));
        assertNull(response.getHeaderString("X-XSS-Protection"));
        assertNull(response.getHeaderString("Strict-Transport-Security"));
    }    
    
    @Test @OperateOnDeployment("disabledProg") @RunAsClient
    public void when_disabled_programmatically_the_filter_is_disabled() {
        WebTarget target = ClientBuilder.newClient().target(baseUri);
        Response response = target.request().get();
        assertNull(response.getHeaderString("X-Frame-Options"));
        assertNull(response.getHeaderString("X-Content-Type-Options"));
        assertNull(response.getHeaderString("X-XSS-Protection"));
        assertNull(response.getHeaderString("Strict-Transport-Security"));
    }     
}
