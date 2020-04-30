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
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.NamingException;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingIOException;
import org.apache.sling.api.SlingServletException;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.servlets.resolver.bundle.tracker.BundledRenderUnit;
import org.apache.sling.scripting.jsp.jasper.compiler.JspRuntimeContext;
import org.apache.sling.scripting.jsp.jasper.runtime.AnnotationProcessor;
import org.apache.sling.scripting.jsp.jasper.runtime.HttpJspBase;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;

@Component(service = {PrecompiledJSPRunner.class})
public class PrecompiledJSPRunner {

    private final ConcurrentHashMap<HttpJspBase, Object> locks = new ConcurrentHashMap<>();

    boolean callPrecompiledJSP(JspRuntimeContext.JspFactoryHandler jspFactoryHandler, JspServletConfig jspServletConfig,
                               SlingBindings bindings) {
        boolean found = false;
        HttpJspBase jsp = null;
        try {
            jspFactoryHandler.incUsage();
            BundledRenderUnit bundledRenderUnit = (BundledRenderUnit) bindings.get(BundledRenderUnit.VARIABLE);
            if (bundledRenderUnit != null && bundledRenderUnit.getUnit() instanceof HttpJspBase) {
                found = true;
                jsp = (HttpJspBase) bundledRenderUnit.getUnit();
                if (jsp.getServletConfig() == null) {
                    Object lock = locks.computeIfAbsent(jsp, key -> new Object());
                    synchronized (lock) {
                        if (jsp.getServletConfig() == null) {
                            PrecompiledServletConfig servletConfig = new PrecompiledServletConfig(jspServletConfig, bundledRenderUnit);
                            AnnotationProcessor annotationProcessor =
                                    (AnnotationProcessor) jspServletConfig.getServletContext()
                                            .getAttribute(AnnotationProcessor.class.getName());
                            if (annotationProcessor != null) {
                                annotationProcessor.processAnnotations(jsp);
                                annotationProcessor.postConstruct(jsp);
                            }
                            jsp.init(servletConfig);
                        }
                    }
                }
                jsp.service(bindings.getRequest(), bindings.getResponse());

            }
        } catch (IllegalAccessException | InvocationTargetException | NamingException e) {
            throw new SlingException("Unable to process annotations for servlet " + jsp.getClass().getName() + ".", e);
        } catch (NoClassDefFoundError ignored) {
            // wave your hands like we don't care - we're missing support for precompiled JSPs
        } catch (IOException e) {
            throw new SlingIOException(e);
        } catch (ServletException e) {
            throw new SlingServletException(e);
        } finally {
            jspFactoryHandler.decUsage();
            if (jsp != null) {
                locks.remove(jsp);
            }
        }
        return found;
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
                        JavaEscapeHelper.unescapeAll(jsp.getClass().getPackage().getName()) + "/" + JavaEscapeHelper.unescapeAll(jsp.getClass().getSimpleName());
                servletName = bundle.getSymbolicName() + ": " + originalName;
            }
            return servletName;
        }
    }
}
