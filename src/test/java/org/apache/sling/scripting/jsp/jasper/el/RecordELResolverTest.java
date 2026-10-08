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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
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

/**
 * Most tests use a {@link FakeRecord}, so they run on every JVM. The tests at the end use real
 * records, which are skipped on JVMs without records.
 */
public class RecordELResolverTest {

    @ClassRule
    public static final TestRecords RECORDS = new TestRecords();

    private RecordELResolver resolver;
    private ELContext context;

    @Before
    public void setUp() {
        resolver = new RecordELResolver(RecordELResolverTest::fakeRecordAccessors);
        context = new ELContextImpl(resolver);
    }

    @Test
    public void getValueReturnsComponent() {
        assertEquals("Alice", resolver.getValue(context, new FakeRecord(), "name"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getValueReturnsPrimitiveComponent() {
        assertEquals(42, resolver.getValue(context, new FakeRecord(), "age"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getValueLeavesNonComponentsToOtherResolvers() {
        for (String property : Arrays.asList("greeting", "unknown", null)) {
            ELContext context = new ELContextImpl(resolver);

            assertNull(property, resolver.getValue(context, new FakeRecord(), property));
            assertFalse(property, context.isPropertyResolved());
        }
    }

    @Test
    public void getValueWrapsExceptionOfAccessor() {
        try {
            resolver.getValue(context, new FakeRecord(), "failing");
            fail("expected ELException");
        } catch (ELException e) {
            assertTrue(e.getCause() instanceof IllegalStateException);
            assertEquals("accessor failed", e.getCause().getMessage());
        }
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getValueWrapsInaccessibleAccessor() throws Exception {
        // public method of a package private class in another package
        Method inaccessible = Class.forName("java.util.Collections$UnmodifiableCollection").getDeclaredMethod("size");
        RecordELResolver resolver = new RecordELResolver(type -> Collections.singletonMap("size", inaccessible));

        try {
            resolver.getValue(context, Collections.unmodifiableList(Collections.emptyList()), "size");
            fail("expected ELException");
        } catch (ELException e) {
            assertTrue(e.getCause() instanceof IllegalAccessException);
        }
    }

    @Test
    public void getTypeReturnsComponentType() {
        assertEquals(String.class, resolver.getType(context, new FakeRecord(), "name"));
        assertEquals(int.class, resolver.getType(context, new FakeRecord(), "age"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void getTypeLeavesUnknownPropertyToOtherResolvers() {
        assertNull(resolver.getType(context, new FakeRecord(), "unknown"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void setValueIsNotAllowed() {
        FakeRecord record = new FakeRecord();

        try {
            resolver.setValue(context, record, "name", "Bob");
            fail("expected PropertyNotWritableException");
        } catch (PropertyNotWritableException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("'name'"));
        }
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void setValueLeavesUnknownPropertyToOtherResolvers() {
        resolver.setValue(context, new FakeRecord(), "unknown", "value");
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void isReadOnlyForComponent() {
        assertTrue(resolver.isReadOnly(context, new FakeRecord(), "name"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void isReadOnlyLeavesUnknownPropertyToOtherResolvers() {
        assertFalse(resolver.isReadOnly(context, new FakeRecord(), "unknown"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void getFeatureDescriptorsDescribesComponents() {
        Map<String, Object> types = new HashMap<String, Object>();
        for (Iterator<FeatureDescriptor> it = resolver.getFeatureDescriptors(context, new FakeRecord()); it.hasNext();) {
            FeatureDescriptor descriptor = it.next();
            assertEquals(Boolean.TRUE, descriptor.getValue(ELResolver.RESOLVABLE_AT_DESIGN_TIME));
            types.put(descriptor.getName(), descriptor.getValue(ELResolver.TYPE));
        }

        Map<String, Object> expected = new HashMap<String, Object>();
        expected.put("name", String.class);
        expected.put("age", int.class);
        expected.put("failing", String.class);
        assertEquals(expected, types);
    }

    @Test
    public void getCommonPropertyTypeOfRecord() {
        assertEquals(Object.class, resolver.getCommonPropertyType(context, new FakeRecord()));
    }

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
        resolver.getValue(null, new FakeRecord(), "name");
    }

    @Test(expected = NullPointerException.class)
    public void getTypeRequiresContext() {
        resolver.getType(null, new FakeRecord(), "name");
    }

    @Test(expected = NullPointerException.class)
    public void setValueRequiresContext() {
        resolver.setValue(null, new FakeRecord(), "name", "value");
    }

    @Test(expected = NullPointerException.class)
    public void isReadOnlyRequiresContext() {
        resolver.isReadOnly(null, new FakeRecord(), "name");
    }

    // real records, skipped on JVMs without records

    @Test
    public void recordComponentsAreResolved() throws Exception {
        RecordELResolver resolver = new RecordELResolver();
        Object person = RECORDS.person("Alice", 42);

        assertEquals("Alice", resolver.getValue(context, person, "name"));
        assertEquals(42, resolver.getValue(context, person, "age"));
        assertTrue(context.isPropertyResolved());
    }

    @Test
    public void recordGetterIsNotAComponent() throws Exception {
        RecordELResolver resolver = new RecordELResolver();

        assertNull(resolver.getValue(context, RECORDS.person("Alice", 42), "greeting"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void nonPublicRecordIsNotResolved() throws Exception {
        RecordELResolver resolver = new RecordELResolver();

        assertNull(resolver.getValue(context, RECORDS.create(TestRecords.HIDDEN, "s3cr3t"), "secret"));
        assertFalse(context.isPropertyResolved());
    }

    @Test
    public void classIsNotARecord() {
        assertTrue(RecordELResolver.findRecordAccessors(FakeRecord.class).isEmpty());
    }

    @Test
    public void isPublicRequiresPublicTypeAndEnclosingClasses() {
        assertTrue(RecordELResolver.isPublic(RecordELResolverTest.class));
        assertTrue(RecordELResolver.isPublic(FakeRecord.class));
        assertFalse(RecordELResolver.isPublic(Private.class));
        assertFalse(RecordELResolver.isPublic(Private.Nested.class));
    }

    @Test
    public void findMethod() throws Exception {
        assertEquals(Object.class.getMethod("toString"), RecordELResolver.findMethod("java.lang.Object", "toString"));
        assertNull(RecordELResolver.findMethod("java.lang.Object", "unknown"));
        assertNull(RecordELResolver.findMethod("java.lang.Unknown", "toString"));
    }

    /** A class with record style accessors, which {@link #fakeRecordAccessors} treats as a record. */
    public static class FakeRecord {
        public String name() {
            return "Alice";
        }

        public int age() {
            return 42;
        }

        public String failing() {
            throw new IllegalStateException("accessor failed");
        }

        public String getGreeting() {
            return "Hello Alice";
        }
    }

    public static class Bean {
        public String getName() {
            return "bean";
        }
    }

    private static class Private {
        public static class Nested {
        }
    }

    private static Map<String, Method> fakeRecordAccessors(Class<?> type) {
        if (type != FakeRecord.class) {
            return Collections.emptyMap();
        }
        Map<String, Method> accessors = new HashMap<String, Method>();
        for (String component : Arrays.asList("name", "age", "failing")) {
            try {
                accessors.put(component, FakeRecord.class.getMethod(component));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
        return accessors;
    }
}
