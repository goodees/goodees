package io.github.goodees.payara.logback.access;

import ch.qos.logback.access.spi.ServerAdapter;
import java.util.HashMap;
import java.util.Map;
import org.apache.catalina.Request;
import org.apache.catalina.Response;
import org.apache.catalina.connector.ResponseFacade;

/**
 * Bridge between Payara's Catalina and Logback Access.
 */
class CatalinaAdapter implements ServerAdapter {
    static final String REQUEST_TIME = "AccessLog.requestStartTime";

    Request request;
    Response response;

    public CatalinaAdapter(Request tomcatRequest, Response tomcatResponse) {
        this.request = tomcatRequest;
        this.response = tomcatResponse;
    }

    @Override
    public long getContentLength() {
        return response.getContentLength();
    }

    private ResponseFacade httpResp() {
        return (ResponseFacade) response.getResponse();
    }

    @Override
    public int getStatusCode() {
        return httpResp().getStatus();
    }

    @Override
    public long getRequestTimestamp() {
        Object l = request.getNote(REQUEST_TIME);
        if (l != null && l instanceof Long) {
            return (Long) l;
        } else {
            return System.currentTimeMillis();
        }
    }

    @Override
    public Map<String, String> buildResponseHeaderMap() {
        Map<String, String> responseHeaderMap = new HashMap<String, String>();
        for (String key : httpResp().getHeaderNames()) {
            String value = httpResp().getHeader(key);
            responseHeaderMap.put(key, value);
        }
        return responseHeaderMap;
    }
}