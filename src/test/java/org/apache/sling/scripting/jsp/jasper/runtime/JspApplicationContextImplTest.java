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
package org.apache.sling.scripting.jsp.jasper.runtime;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.PropertyNotWritableException;
import javax.el.ValueExpression;
import javax.servlet.jsp.JspContext;

import org.apache.el.ExpressionFactoryImpl;
import org.apache.sling.scripting.jsp.jasper.el.TestRecords;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Tests expressions evaluated against the resolvers used for JSP pages.
 */
public class JspApplicationContextImplTest {

    @ClassRule
    public static final TestRecords RECORDS = new TestRecords();

    private final ExpressionFactory factory = new ExpressionFactoryImpl();

    private ELContext context;

    @Before
    public void setUp() {
        context = new JspApplicationContextImpl().createELContext(mock(JspContext.class));
    }

    @Test
    public void recordComponent() throws Exception {
        assertEquals("Alice", eval(RECORDS.person("Alice", 42), "${r.name}"));
    }

    @Test
    public void recordGetterIsResolvedAsBeanProperty() throws Exception {
        assertEquals("Hello Alice", eval(RECORDS.person("Alice", 42), "${r.greeting}"));
    }

    @Test(expected = PropertyNotWritableException.class)
    public void recordComponentCannotBeSet() throws Exception {
        expression(RECORDS.person("Alice", 42), "${r.name}").setValue(context, "Bob");
    }

    @Test
    public void beanProperty() {
        assertEquals("bean", eval(new Bean(), "${r.name}"));
    }

    public static class Bean {
        public String getName() {
            return "bean";
        }
    }

    private Object eval(Object base, String expression) {
        return expression(base, expression).getValue(context);
    }

    private ValueExpression expression(Object base, String expression) {
        context.getVariableMapper().setVariable("r", factory.createValueExpression(base, Object.class));
        return factory.createValueExpression(context, expression, Object.class);
    }
}
