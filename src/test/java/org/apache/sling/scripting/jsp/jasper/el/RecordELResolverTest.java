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

import java.beans.FeatureDescriptor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotWritableException;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RecordELResolverTest {

    @ClassRule
    public static final TestRecords RECORDS = new TestRecords();

    private RecordELResolver resolver;
    private ELContext context;

    @Before
    public void setUp() {
        resolver = new RecordELResolver();
        context = new ELContextImpl(resolver);
    }

    // records

    @Test
    public void getValueReturnsComponent() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertEquals("Alice", resolver.getValue(context, person, "name"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getValueReturnsPrimitiveComponent() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertEquals(42, resolver.getValue(context, person, "age"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getValueOfNonPublicRecord() throws Exception {
        Object hidden = RECORDS.create(TestRecords.HIDDEN, "s3cr3t");

        assertEquals("s3cr3t", resolver.getValue(context, hidden, "secret"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getValueLeavesGetterToOtherResolvers() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertNull(resolver.getValue(context, person, "greeting"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void getValueLeavesUnknownPropertyToOtherResolvers() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertNull(resolver.getValue(context, person, "unknown"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void getValueLeavesNullPropertyToOtherResolvers() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertNull(resolver.getValue(context, person, null));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void getValueWrapsExceptionOfAccessor() throws Exception {
        Object failing = RECORDS.create(TestRecords.FAILING, "value");

        try {
            resolver.getValue(context, failing, "value");
            fail("expected ELException");
        } catch (ELException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertEquals("accessor failed", e.getCause().getMessage());
        }
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getTypeReturnsComponentType() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertEquals(String.class, resolver.getType(context, person, "name"));
        assertEquals(int.class, resolver.getType(context, person, "age"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getTypeLeavesUnknownPropertyToOtherResolvers() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertNull(resolver.getType(context, person, "unknown"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void setValueIsNotAllowed() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        try {
            resolver.setValue(context, person, "name", "Bob");
            fail("expected PropertyNotWritableException");
        } catch (PropertyNotWritableException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'name'"));
        }
        assertTrue(context.isPropertyResolved());
        assertEquals("Alice", resolver.getValue(context, person, "name"));
    }

    @Test
    public void setValueLeavesUnknownPropertyToOtherResolvers() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        resolver.setValue(context, person, "unknown", "value");
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void isReadOnlyForComponent() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertTrue(resolver.isReadOnly(context, person, "name"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void isReadOnlyLeavesUnknownPropertyToOtherResolvers() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertFalse(resolver.isReadOnly(context, person, "unknown"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void getFeatureDescriptorsDescribesComponents() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        Map<String, Object> types = new HashMap<String, Object>();
        for (Iterator<FeatureDescriptor> it = resolver.getFeatureDescriptors(context, person); it.hasNext();) {
            FeatureDescriptor descriptor = it.next();
            assertEquals(Boolean.TRUE, descriptor.getValue(ELResolver.RESOLVABLE_AT_DESIGN_TIME));
            types.put(descriptor.getName(), descriptor.getValue(ELResolver.TYPE));
        }

        Map<String, Object> expected = new HashMap<String, Object>();
        expected.put("name", String.class);
        expected.put("age", int.class);
        assertEquals(expected, types);
    }

    @Test
    public void getCommonPropertyTypeOfRecord() throws Exception {
        Object person = RECORDS.person("Alice", 42);

        assertEquals(Object.class, resolver.getCommonPropertyType(context, person));
    }

    // no records, these run on every JVM

    @Test
    public void nonRecordIsLeftToOtherResolvers() {
        Bean bean = new Bean();

        assertNull(resolver.getValue(context, bean, "name"));
        assertNull(resolver.getType(context, bean, "name"));
        resolver.setValue(context, bean, "name", "value");
        assertFalse(resolver.isReadOnly(context, bean, "name"));
        assertFalse(context.isPropertyResolved());
        assertNull(resolver.getFeatureDescriptors(context, bean));
        assertNull(resolver.getCommonPropertyType(context, bean));
    }

    @Test
    public void nullBaseIsLeftToOtherResolvers() {
        assertNull(resolver.getValue(context, null, "name"));
        assertNull(resolver.getType(context, null, "name"));
        resolver.setValue(context, null, "name", "value");
        assertFalse(resolver.isReadOnly(context, null, "name"));
        assertFalse(context.isPropertyResolved());
        assertNull(resolver.getFeatureDescriptors(context, null));
        assertNull(resolver.getCommonPropertyType(context, null));
    }

    @Test(expected = NullPointerException.class)
    public void getValueRequiresContext() {
        resolver.getValue(null, new Bean(), "name");
    }

    @Test(expected = NullPointerException.class)
    public void getTypeRequiresContext() {
        resolver.getType(null, new Bean(), "name");
    }

    @Test(expected = NullPointerException.class)
    public void setValueRequiresContext() {
        resolver.setValue(null, new Bean(), "name", "value");
    }

    @Test(expected = NullPointerException.class)
    public void isReadOnlyRequiresContext() {
        resolver.isReadOnly(null, new Bean(), "name");
    }

    public static class Bean {
        public String getName() {
            return "bean";
        }
    }
}
