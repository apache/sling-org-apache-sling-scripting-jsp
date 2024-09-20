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

import javax.naming.NamingException;
import javax.servlet.ServletException;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingServletException;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.jsp.jasper.Options;
import org.apache.sling.scripting.jsp.jasper.compiler.JspRuntimeContext;
import org.apache.sling.scripting.jsp.jasper.runtime.AnnotationProcessor;
import org.apache.sling.scripting.jsp.jasper.runtime.HttpJspBase;
import org.apache.sling.scripting.jsp.jasper.servlet.JspServletWrapper;
import org.apache.sling.scripting.spi.bundle.BundledRenderUnit;
import org.osgi.framework.Bundle;

public class PrecompiledJSPRunner {

    private final Options options;

    private final ConcurrentHashMap<HttpJspBase, JspHolder> holders = new ConcurrentHashMap<>();

    public PrecompiledJSPRunner(final Options options) {
        this.options = options;
    }

    boolean callPrecompiledJSP(
            JspRuntimeContext runtimeContext,
            JspRuntimeContext.JspFactoryHandler jspFactoryHandler,
            JspServletConfig jspServletConfig,
            SlingBindings bindings) {
        boolean found = false;
        final BundledRenderUnit bundledRenderUnit = (BundledRenderUnit) bindings.get(BundledRenderUnit.VARIABLE);
        if (bundledRenderUnit != null && bundledRenderUnit.getUnit() instanceof HttpJspBase) {
            found = true;
            final HttpJspBase jsp = (HttpJspBase) bundledRenderUnit.getUnit();
            final JspHolder holder = holders.computeIfAbsent(jsp, key -> new JspHolder());
            if (holder.wrapper == null) {
                synchronized (holder) {
                    if (holder.wrapper == null) {
                        try {
                            final PrecompiledServletConfig servletConfig =
                                    new PrecompiledServletConfig(jspServletConfig, bundledRenderUnit);
                            final AnnotationProcessor annotationProcessor = (AnnotationProcessor) jspServletConfig
                                    .getServletContext()
                                    .getAttribute(AnnotationProcessor.class.getName());
                            if (annotationProcessor != null) {
                                annotationProcessor.processAnnotations(jsp);
                                annotationProcessor.postConstruct(jsp);
                            }

                            final JspServletWrapper wrapper = new JspServletWrapper(
                                    servletConfig,
                                    this.options,
                                    bundledRenderUnit.getPath(),
                                    false,
                                    runtimeContext,
                                    jsp);
                            jsp.init(servletConfig);

                            holder.wrapper = wrapper;
                        } catch (final ServletException se) {
                            throw new SlingServletException(se);
                        } catch (IllegalAccessException | InvocationTargetException | NamingException e) {
                            throw new SlingException(
                                    "Unable to process annotations for servlet "
                                            + jsp.getClass().getName() + ".",
                                    e);
                        } catch (NoClassDefFoundError ignored) {
                            // wave your hands like we don't care - we're missing support for precompiled JSPs
                        }
                    }
                }
            }

            holder.wrapper.service(bindings);
        }
        return found;
    }

    public void cleanup() {
        final Set<JspHolder> holders = new HashSet<>(this.holders.values());
        this.holders.clear();
        try {
            for (final JspHolder h : holders) {
                if (h.wrapper != null) {
                    h.wrapper.destroy(true);
                }
            }
        } catch (final Throwable t) {
            // we ignore this exception as this might be called during shutdown
        }
    }

    private static class PrecompiledServletConfig extends JspServletConfig {

        private final BundledRenderUnit bundledRenderUnit;
        private String servletName;

        PrecompiledServletConfig(JspServletConfig jspServletConfig, BundledRenderUnit bundledRenderUnit) {
            super(jspServletConfig.getServletContext(), new HashMap<>(jspServletConfig.getProperties()));
            this.bundledRenderUnit = bundledRenderUnit;
        }

        @Override
        public String getServletName() {
            if (servletName == null && bundledRenderUnit.getUnit() != null) {
                Bundle bundle = bundledRenderUnit.getBundle();
                Object jsp = bundledRenderUnit.getUnit();
                String originalName =
                        JavaEscapeHelper.unescapeAll(jsp.getClass().getPackage().getName()) + "/"
                                + JavaEscapeHelper.unescapeAll(jsp.getClass().getSimpleName());
                servletName = bundle.getSymbolicName() + ": " + originalName;
            }
            return servletName;
        }
    }

    public static final class JspHolder {

        public volatile JspServletWrapper wrapper;
    }
}
