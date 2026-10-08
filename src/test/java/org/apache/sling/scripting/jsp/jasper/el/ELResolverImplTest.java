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
package org.apache.sling.scripting.jsp.jasper.el;

import java.util.Collections;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;
import javax.el.ValueExpression;

import org.apache.el.ExpressionFactoryImpl;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests expressions evaluated against {@link ELResolverImpl#DefaultResolver}.
 */
public class ELResolverImplTest {

    @ClassRule
    public static final TestRecords RECORDS = new TestRecords();

    private final ExpressionFactory factory = new ExpressionFactoryImpl();

    private ELContext context;

    @Before
    public void setUp() {
        context = new ELContextImpl(ELResolverImpl.DefaultResolver);
    }

    @Test
    public void recordComponent() throws Exception {
        assertEquals("Alice", eval(RECORDS.person("Alice", 42), "${r.name}"));
    }

    @Test
    public void recordComponentInArithmetic() throws Exception {
        assertEquals(43L, eval(RECORDS.person("Alice", 42), "${r.age + 1}"));
    }

    @Test
    public void recordGetterIsResolvedAsBeanProperty() throws Exception {
        assertEquals("Hello Alice", eval(RECORDS.person("Alice", 42), "${r.greeting}"));
    }

    @Test(expected = PropertyNotFoundException.class)
    public void unknownRecordProperty() throws Exception {
        eval(RECORDS.person("Alice", 42), "${r.unknown}");
    }

    @Test
    public void recordComponentIsReadOnly() throws Exception {
        assertTrue(expression(RECORDS.person("Alice", 42), "${r.name}").isReadOnly(context));
    }

    @Test(expected = PropertyNotWritableException.class)
    public void recordComponentCannotBeSet() throws Exception {
        expression(RECORDS.person("Alice", 42), "${r.name}").setValue(context, "Bob");
    }

    @Test
    public void beanProperty() {
        assertEquals("bean", eval(new Bean(), "${r.name}"));
    }

    @Test
    public void mapEntry() {
        assertEquals("map", eval(Collections.singletonMap("name", "map"), "${r.name}"));
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
