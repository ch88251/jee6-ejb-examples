package com.example.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * One HTTP request/response pair for servlet unit tests. The request,
 * response and session are Mockito mocks, backed by plain maps for parameters
 * and session attributes. The response body is captured as a string.
 * <p>
 * A session can be shared across requests ({@link #nextRequest()}), to
 * test servlets that keep state in it, like the cart.
 */
final class HttpFixture {

    static final String CONTEXT_PATH = "/jee6-ejb-examples";

    final Map<String, String> params = new HashMap<String, String>();
    final Map<String, Object> sessionAttributes;
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final HttpServletResponse response = mock(HttpServletResponse.class);
    final HttpSession session = mock(HttpSession.class);
    private final StringWriter body = new StringWriter();

    HttpFixture() {
        this(new HashMap<String, Object>());
    }

    private HttpFixture(Map<String, Object> sessionAttributes) {
        this.sessionAttributes = sessionAttributes;
        try {
            lenient().when(request.getParameter(anyString())).thenAnswer(inv -> params.get(inv.<String>getArgument(0)));
            lenient().when(request.getContextPath()).thenReturn(CONTEXT_PATH);
            lenient().when(request.getSession()).thenReturn(session);
            lenient().when(response.getWriter()).thenReturn(new PrintWriter(body, true));

            lenient().when(session.getAttribute(anyString()))
                    .thenAnswer(inv -> sessionAttributes.get(inv.<String>getArgument(0)));
            lenient().doAnswer(inv -> sessionAttributes.put(inv.getArgument(0), inv.getArgument(1)))
                    .when(session).setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
            lenient().doAnswer(inv -> sessionAttributes.remove(inv.<String>getArgument(0)))
                    .when(session).removeAttribute(anyString());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** A new request/response in the same HTTP session. */
    HttpFixture nextRequest() {
        return new HttpFixture(sessionAttributes);
    }

    HttpFixture param(String name, String value) {
        params.put(name, value);
        return this;
    }

    /** The HTML written so far. */
    String html() {
        return body.toString();
    }
}
