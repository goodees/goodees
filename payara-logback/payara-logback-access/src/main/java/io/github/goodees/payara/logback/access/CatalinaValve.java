/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.goodees.payara.logback.access;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import ch.qos.logback.access.spi.IAccessEvent;
import org.apache.catalina.valves.ValveBase;

import ch.qos.logback.access.AccessConstants;
import ch.qos.logback.access.joran.JoranConfigurator;
import ch.qos.logback.access.spi.AccessContext;
import ch.qos.logback.access.spi.AccessEvent;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.WarnStatus;
import ch.qos.logback.core.util.OptionHelper;
import ch.qos.logback.core.util.StatusPrinter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Request;
import org.apache.catalina.Response;

/**
 * This class is an implementation of tomcat's Valve interface, by extending
 * ValveBase.
 *
 * <p>
 * For more information on using LogbackValve please refer to the online
 * documentation on <a
 * href="http://logback.qos.ch/access.html#tomcat">logback-acces and tomcat</a>.
 * 
 * <p>
 * The implementation has been reduced to just request handling.
 *
 * @author Ceki G&uuml;lc&uuml;
 * @author S&eacute;bastien Pennec
 * @author Patrik Dudits
 */
class CatalinaValve extends ValveBase {

    public final static String DEFAULT_CONFIG_FILE = "logback-access.xml";

    final AccessContext ctx = new AccessContext();
    
    String filename;
    boolean quiet;
    boolean alreadySetLogbackStatusManager = false;

    public CatalinaValve() {
        ctx.putObject(CoreConstants.EVALUATOR_MAP, new HashMap());
    }

    @Override
    public void start() throws LifecycleException {
        super.start();
        ctx.start();
        if (filename == null) {
            filename = OptionHelper.getSystemProperty("logbackAccess.configurationFile");
            if (filename == null) {
                filename = DEFAULT_CONFIG_FILE;
            }
            ctx.getStatusManager().add(new InfoStatus("filename property not set. Assuming [" + filename + "]", this));
        }
        // TODO: Support classpath config
        File configFile = new File(filename);
        if (configFile.exists()) {
            try {
                JoranConfigurator jc = new JoranConfigurator();
                jc.setContext(ctx);
                jc.doConfigure(filename);
            } catch (JoranException e) {
                // TODO can we do better than printing a stack trace on syserr?
                e.printStackTrace();
            }
        } else {
            ctx.getStatusManager().add(new WarnStatus("[" + filename + "] does not exist", this));
        }

        if (!quiet) {
            StatusPrinter.print(ctx.getStatusManager());
        }
        
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public void setQuiet(boolean quiet) {
        this.quiet = quiet;
    }

    @Override
    public String getInfo() {
        return "Logback Access Logging";
    }

    @Override
    public int invoke(Request rqst, Response rspns) throws IOException,
            ServletException {
        if (!isStarted()) {
            try {
                start();
            } catch (LifecycleException ex) {
                throw new ServletException(ex);
            }
        }
        rqst.setNote(CatalinaAdapter.REQUEST_TIME, System.currentTimeMillis());
        if (!alreadySetLogbackStatusManager) {
            alreadySetLogbackStatusManager = true;
            org.apache.catalina.Context tomcatContext = rqst.getContext();
            if (tomcatContext != null) {
                ServletContext sc = tomcatContext.getServletContext();
                if (sc != null) {
                    sc.setAttribute(AccessConstants.LOGBACK_STATUS_MANAGER_KEY, ctx.getStatusManager());
                }
            }
        }

        return INVOKE_NEXT;
    }

    @Override
    public void postInvoke(Request request, Response response)
            throws IOException, ServletException {
        final HttpServletRequest httpRequest = (HttpServletRequest) request.getRequest();
        try {
            CatalinaAdapter adapter = new CatalinaAdapter(request, response);
            IAccessEvent accessEvent = new AccessEvent(httpRequest, (HttpServletResponse) response.getResponse(),
                adapter);

            if (ctx.getFilterChainDecision(accessEvent) == FilterReply.DENY) {
                return;
            }

            // TODO better tion handling
            ctx.callAppenders(accessEvent);
        } finally {
            httpRequest.removeAttribute(AccessConstants.LOGBACK_STATUS_MANAGER_KEY);
        }
    }

}
