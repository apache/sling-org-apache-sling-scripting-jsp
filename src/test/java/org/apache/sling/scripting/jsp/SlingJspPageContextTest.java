/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.scripting.jsp;

import javax.el.ELContext;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.jsp.PageContext;

import java.io.IOException;
import java.io.Writer;

import org.apache.sling.api.scripting.SlingBindings;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SlingJspPageContextTest {

    private PageContext wrapped;
    private SlingJspPageContext underTest;

    @Before
    public void setUp() {
        wrapped = mock(PageContext.class);
        ELContext elContext = mock(ELContext.class);
        when(wrapped.getELContext()).thenReturn(elContext);
        when(wrapped.getAttribute("testValue1")).thenReturn(1);
        when(wrapped.findAttribute("testValue2")).thenReturn(2);
        SlingBindings slingBindings = new SlingBindings();
        slingBindings.put("testValue1", 3);
        slingBindings.put("testValue2", 4);
        slingBindings.put("testValue3", 5);
        underTest = new SlingJspPageContext(wrapped, slingBindings);
    }

    @Test
    public void pushBody1() {
        underTest.pushBody();
        verify(wrapped).pushBody();
    }

    @Test
    public void pushBody2() {
        Writer writer = mock(Writer.class);
        underTest.pushBody(writer);
        verify(wrapped).pushBody(writer);
    }

    @Test
    public void popBody() {
        underTest.popBody();
        verify(wrapped).popBody();
    }

    @Test
    public void popBody2() {
        underTest.popBody();
        verify(wrapped).popBody();
    }

    @Test
    public void initialize() throws IOException {
        Servlet servlet = mock(Servlet.class);
        ServletRequest servletRequest = mock(ServletRequest.class);
        ServletResponse servletResponse = mock(ServletResponse.class);
        underTest.initialize(servlet, servletRequest, servletResponse, "string", true, 0, true);
        verify(wrapped).initialize(servlet, servletRequest, servletResponse, "string", true, 0, true);
    }

    @Test
    public void release() {
        underTest.release();
        verify(wrapped).release();
    }

    @Test
    public void getSession() {
        underTest.getSession();
        verify(wrapped).getSession();
    }

    @Test
    public void getPage() {
        underTest.getPage();
        verify(wrapped).getPage();
    }

    @Test
    public void getRequest() {
        underTest.getRequest();
        verify(wrapped).getRequest();
    }

    @Test
    public void getResponse() {
        underTest.getResponse();
        verify(wrapped).getResponse();
    }

    @Test
    public void getException() {
        underTest.getException();
        verify(wrapped).getException();
    }

    @Test
    public void getServletConfig() {
        underTest.getServletConfig();
        verify(wrapped).getServletConfig();
    }

    @Test
    public void getServletContext() {
        underTest.getServletContext();
        verify(wrapped).getServletContext();
    }

    @Test
    public void forward() throws ServletException, IOException {
        underTest.forward("string");
        verify(wrapped).forward("string");
    }

    @Test
    public void include1() throws ServletException, IOException {
        underTest.include("string");
        verify(wrapped).include("string");
    }

    @Test
    public void include2() throws ServletException, IOException {
        underTest.include("string", true);
        verify(wrapped).include("string", true);
    }

    @Test
    public void handlePageException1() throws ServletException, IOException {
        Exception e = mock(Exception.class);
        underTest.handlePageException(e);
        verify(wrapped).handlePageException(e);
    }

    @Test
    public void handlePageException2() throws ServletException, IOException {
        Throwable t = mock(Throwable.class);
        underTest.handlePageException(t);
        verify(wrapped).handlePageException(t);
    }

    @Test
    public void setAttribute1() {
        Object o = mock(Object.class);
        underTest.setAttribute("string", o);
        verify(wrapped).setAttribute("string", o);
    }

    @Test
    public void setAttribute2() {
        Object o = mock(Object.class);
        underTest.setAttribute("string", o, 1);
        verify(wrapped).setAttribute("string", o, 1);
    }

    @Test
    public void getAttribute1() {
        assertEquals(1, underTest.getAttribute("testValue1"));
        assertEquals(5, underTest.getAttribute("testValue3"));
        assertNull(underTest.getAttribute("none"));
    }

    @Test
    public void getAttribute2() {
        underTest.getAttribute("string", 1);
        verify(wrapped).getAttribute("string", 1);
    }

    @Test
    public void findAttribute() {
        assertEquals(2, underTest.findAttribute("testValue2"));
        assertEquals(5, underTest.findAttribute("testValue3"));
        assertNull(underTest.findAttribute("none"));
    }

    @Test
    public void removeAttribute1() {
        underTest.removeAttribute("string");
        verify(wrapped).removeAttribute("string");
    }

    @Test
    public void removeAttribute2() {
        underTest.removeAttribute("string", 1);
        verify(wrapped).removeAttribute("string", 1);
    }

    @Test
    public void getAttributesScope() {
        underTest.getAttributesScope("string");
        verify(wrapped).getAttributesScope("string");
    }

    @Test
    public void getAttributeNamesInScope() {
        underTest.getAttributeNamesInScope(1);
        verify(wrapped).getAttributeNamesInScope(1);
    }

    @Test
    public void getOut() {
        underTest.getOut();
        verify(wrapped).getOut();
    }

    @Test
    public void getExpressionEvaluator() {
        underTest.getExpressionEvaluator();
        verify(wrapped).getExpressionEvaluator();
    }

    @Test
    public void getELContext() {
        assertEquals(wrapped.getELContext(), underTest.getELContext());
        // once here, once in the implementation
        verify(wrapped, times(2)).getELContext();
    }

    @Test
    public void getVariableResolver() {
        underTest.getVariableResolver();
        verify(wrapped).getVariableResolver();
    }

    @Test
    public void getErrorData() {
        underTest.getErrorData();
        verify(wrapped).getErrorData();
    }
}
