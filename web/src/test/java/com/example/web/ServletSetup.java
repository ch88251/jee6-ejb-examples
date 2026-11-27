package com.example.web;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

/**
 * Initializes a servlet the way Tomcat would after {@link AppContextListener}
 * has run: its {@link ServletContext} holds the given service under the
 * interface's name.
 */
final class ServletSetup {

    private ServletSetup() {
    }

    static <S extends HttpServlet, T> S init(S servlet, Class<T> type, T service) throws ServletException {
        ServletContext context = mock(ServletContext.class);
        lenient().when(context.getAttribute(type.getName())).thenReturn(service);
        ServletConfig config = mock(ServletConfig.class);
        lenient().when(config.getServletContext()).thenReturn(context);
        servlet.init(config);
        return servlet;
    }
}
