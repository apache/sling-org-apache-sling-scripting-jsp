/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.jsp;

import java.io.IOException;
import java.util.Enumeration;

import javax.el.ELContext;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.ErrorData;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.tagext.BodyContent;

import org.apache.sling.api.scripting.SlingBindings;

public class SlingJspPageContext extends PageContext {

    private final PageContext wrapped;
    private final SlingBindings slingBindings;
    private final ELContext elContext;

    public SlingJspPageContext(PageContext wrapped, SlingBindings slingBindings) {
        this.wrapped = wrapped;
        this.slingBindings = slingBindings;
        elContext = wrapped.getELContext();
        elContext.putContext(JspContext.class, this);
    }

    @Override
    public void initialize(Servlet servlet, ServletRequest servletRequest, ServletResponse servletResponse, String s, boolean b, int i,
                           boolean b1) throws IOException, IllegalStateException, IllegalArgumentException {
        wrapped.initialize(servlet, servletRequest, servletResponse, s, b, i, b1);
    }

    @Override
    public void release() {
        wrapped.release();
    }

    @Override
    public HttpSession getSession() {
        return wrapped.getSession();
    }

    @Override
    public Object getPage() {
        return wrapped.getPage();
    }

    @Override
    public ServletRequest getRequest() {
        return wrapped.getRequest();
    }

    @Override
    public ServletResponse getResponse() {
        return wrapped.getResponse();
    }

    @Override
    public Exception getException() {
        return wrapped.getException();
    }

    @Override
    public ServletConfig getServletConfig() {
        return wrapped.getServletConfig();
    }

    @Override
    public ServletContext getServletContext() {
        return wrapped.getServletContext();
    }

    @Override
    public void forward(String s) throws ServletException, IOException {
        wrapped.forward(s);
    }

    @Override
    public void include(String s) throws ServletException, IOException {
        wrapped.include(s);
    }

    @Override
    public void include(String s, boolean b) throws ServletException, IOException {
        wrapped.include(s, b);
    }

    @Override
    public void handlePageException(Exception e) throws ServletException, IOException {
        wrapped.handlePageException(e);
    }

    @Override
    public void handlePageException(Throwable throwable) throws ServletException, IOException {
        wrapped.handlePageException(throwable);
    }

    @Override
    public void setAttribute(String s, Object o) {
        wrapped.setAttribute(s, o);
    }

    @Override
    public void setAttribute(String s, Object o, int i) {
        wrapped.setAttribute(s, o, i);
    }

    @Override
    public Object getAttribute(String s) {
        Object attribute = wrapped.getAttribute(s);
        if (attribute == null) {
            attribute = slingBindings.get(s);
        }
        return attribute;
    }

    @Override
    public Object getAttribute(String s, int i) {
        return wrapped.getAttribute(s, i);
    }

    @Override
    public Object findAttribute(String s) {
        Object attribute = wrapped.findAttribute(s);
        if (attribute == null) {
            attribute = slingBindings.get(s);
        }
        return attribute;
    }

    @Override
    public void removeAttribute(String s) {
        wrapped.removeAttribute(s);
    }

    @Override
    public void removeAttribute(String s, int i) {
        wrapped.removeAttribute(s, i);
    }

    @Override
    public int getAttributesScope(String s) {
        return wrapped.getAttributesScope(s);
    }

    @Override
    public Enumeration<String> getAttributeNamesInScope(int i) {
        return wrapped.getAttributeNamesInScope(i);
    }

    @Override
    public JspWriter getOut() {
        return wrapped.getOut();
    }

    @Override
    public ExpressionEvaluator getExpressionEvaluator() {
        return wrapped.getExpressionEvaluator();
    }

    @Override
    public ELContext getELContext() {
        return elContext;
    }

    @Override
    public VariableResolver getVariableResolver() {
        return wrapped.getVariableResolver();
    }

    @Override
    public BodyContent pushBody() {
        return wrapped.pushBody();
    }

    @Override
    public ErrorData getErrorData() {
        return wrapped.getErrorData();
    }
}
