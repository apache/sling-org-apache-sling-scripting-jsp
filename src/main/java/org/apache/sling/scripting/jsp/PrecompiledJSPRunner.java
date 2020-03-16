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

import javax.naming.NamingException;
import javax.servlet.ServletException;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingIOException;
import org.apache.sling.api.SlingServletException;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.commons.compiler.source.JavaEscapeHelper;
import org.apache.sling.scripting.bundle.tracker.BundledRenderUnit;
import org.apache.sling.scripting.jsp.jasper.compiler.JspRuntimeContext;
import org.apache.sling.scripting.jsp.jasper.runtime.AnnotationProcessor;
import org.apache.sling.scripting.jsp.jasper.runtime.HttpJspBase;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        immediate = true,
        service = {}
        /*
         * this component will register itself as a service only if the org.apache.sling.scripting.bundle.tracker API is present
         */
        )
public class PrecompiledJSPRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrecompiledJSPRunner.class);

    private final ServiceRegistration<?> serviceRegistration;

    @Activate
    public PrecompiledJSPRunner(BundleContext bundleContext) {
        serviceRegistration = register(bundleContext);
    }

    @Deactivate
    public void deactivate() {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
        }
    }

    boolean callPrecompiledJSP(JspRuntimeContext.JspFactoryHandler jspFactoryHandler, JspServletConfig jspServletConfig,
                               SlingBindings bindings) {
        boolean found = false;
        BundledRenderUnit bundledRenderUnit = (BundledRenderUnit) bindings.get(BundledRenderUnit.VARIABLE);
        if (bundledRenderUnit != null && bundledRenderUnit.getUnit() instanceof HttpJspBase) {
            found = true;
            HttpJspBase jsp = (HttpJspBase) bundledRenderUnit.getUnit();
            PrecompiledServletConfig servletConfig = new PrecompiledServletConfig(jspServletConfig, bundledRenderUnit);
            try {
                jspFactoryHandler.incUsage();
                AnnotationProcessor annotationProcessor =
                        (AnnotationProcessor) jspServletConfig.getServletContext().getAttribute(AnnotationProcessor.class.getName());
                if (annotationProcessor != null) {
                    annotationProcessor.processAnnotations(jsp);
                    annotationProcessor.postConstruct(jsp);
                }
                if (jsp.getServletConfig() == null) {
                    jsp.init(servletConfig);
                }
                jsp.service(bindings.getRequest(), bindings.getResponse());
            } catch (IOException e) {
                throw new SlingIOException(e);
            } catch (ServletException e) {
                throw new SlingServletException(e);
            } catch (IllegalAccessException | InvocationTargetException | NamingException e) {
                throw new SlingException("Unable to process annotations for servlet " + servletConfig.getServletName() + ".", e);
            } finally {
                jspFactoryHandler.decUsage();
            }
        }
        return found;
    }

    private ServiceRegistration<?> register(BundleContext bundleContext) {
        try {
            PrecompiledJSPRunner.class.getClassLoader().loadClass("org.apache.sling.scripting.bundle.tracker.BundledRenderUnit");
            return bundleContext.registerService(PrecompiledJSPRunner.class, this, null);
        } catch (Exception e) {
            LOGGER.info("No support for precompiled scripts.");
        }
        return null;
    }

    public static class PrecompiledServletConfig extends JspServletConfig {

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
