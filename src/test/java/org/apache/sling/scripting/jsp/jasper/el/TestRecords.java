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

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * Provides Java records to tests. The project is compiled for Java 8, so records can't be part of
 * the test sources; they are compiled when running on a JVM that supports them (Java 16+). Tests
 * creating a record are skipped on older JVMs.
 * <p>
 * Use as a {@link org.junit.ClassRule}.
 */
public class TestRecords extends ExternalResource {

    /** A public record with an additional bean getter, which is not a record component. */
    public static final String PERSON = "records.Person";

    /** A package private record. */
    public static final String HIDDEN = "records.Hidden";

    /** A record whose accessor throws an {@link IllegalStateException}. */
    public static final String FAILING = "records.Failing";

    private static final Map<String, String> SOURCES = new LinkedHashMap<String, String>();

    static {
        SOURCES.put(PERSON, "package records;\n"
                + "public record Person(String name, int age) {\n"
                + "    public String getGreeting() { return \"Hello \" + name; }\n"
                + "}\n");
        SOURCES.put(HIDDEN, "package records;\n"
                + "record Hidden(String secret) {}\n");
        SOURCES.put(FAILING, "package records;\n"
                + "public record Failing(String value) {\n"
                + "    public String value() { throw new IllegalStateException(\"accessor failed\"); }\n"
                + "}\n");
    }

    private final TemporaryFolder folder = new TemporaryFolder();

    private URLClassLoader classLoader;

    @Override
    protected void before() throws Throwable {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (!recordsSupported() || compiler == null) {
            return;
        }
        folder.create();
        File sources = folder.newFolder("src");
        File classes = folder.newFolder("classes");
        List<String> arguments = new ArrayList<String>();
        arguments.add("-d");
        arguments.add(classes.getPath());
        for (Map.Entry<String, String> source : SOURCES.entrySet()) {
            File file = new File(sources, source.getKey().substring(source.getKey().indexOf('.') + 1) + ".java");
            Files.write(file.toPath(), source.getValue().getBytes(StandardCharsets.UTF_8));
            arguments.add(file.getPath());
        }
        assertEquals("compiling the test records failed", 0,
                compiler.run(null, null, null, arguments.toArray(new String[0])));
        classLoader = new URLClassLoader(new URL[] {classes.toURI().toURL()}, getClass().getClassLoader());
    }

    @Override
    protected void after() {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (java.io.IOException e) {
                // ignore, only the temporary folder is affected
            }
            classLoader = null;
        }
        folder.delete();
    }

    /**
     * Creates an instance of the given record using its canonical constructor. Skips the calling
     * test if records aren't supported.
     */
    public Object create(String className, Object... components) throws Exception {
        assumeTrue("records require Java 16+ running on a JDK", classLoader != null);
        Constructor<?> constructor = classLoader.loadClass(className).getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(components);
    }

    public Object person(String name, int age) throws Exception {
        return create(PERSON, name, age);
    }

    private static boolean recordsSupported() {
        try {
            Class.class.getMethod("isRecord");
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
