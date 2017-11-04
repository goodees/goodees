package io.github.goodees.payara.logback.access;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Request;
import org.apache.catalina.Response;
import org.glassfish.web.valve.GlassFishValve;

/**
 * Due to the way VirtualServer.addValve is implemented we cannot implement
 * Tomcat interface if we want lifecycle support.
 * @author Patrik Dudits
 */
public class Logger implements GlassFishValve, Lifecycle {
    private final CatalinaValve delegate;

    public Logger() {
        this.delegate = new CatalinaValve();
    }

    @Override
    public void start() throws LifecycleException {
        delegate.start();
    }

    @Override
    public int invoke(Request rqst, Response rspns) throws IOException, ServletException {
        return delegate.invoke(rqst, rspns);
    }

    @Override
    public void postInvoke(Request request, Response response) throws IOException, ServletException {
        delegate.postInvoke(request, response);
    }

    @Override
    public void addLifecycleListener(LifecycleListener listener) {
        delegate.addLifecycleListener(listener);
    }

    @Override
    public void stop() throws LifecycleException {
        delegate.stop();
    }

    @Override
    public String getInfo() {
        return delegate.getInfo();
    }

    @Override
    public List<LifecycleListener> findLifecycleListeners() {
        return delegate.findLifecycleListeners();
    }

    @Override
    public void removeLifecycleListener(LifecycleListener listener) {
        delegate.removeLifecycleListener(listener);
    }

}
