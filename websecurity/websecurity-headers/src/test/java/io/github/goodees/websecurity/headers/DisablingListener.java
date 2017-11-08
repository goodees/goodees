package io.github.goodees.websecurity.headers;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/**
 *
 * @author patrik
 */
@WebListener()
public class DisablingListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        SecurityHeadersFilter.disableDefaultFilter(sce.getServletContext());
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }

}
