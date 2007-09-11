/*
 * Copyright 2007 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.scripting.jsp;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.jcr.RepositoryException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import org.apache.jasper.JasperException;
import org.apache.jasper.Options;
import org.apache.jasper.compiler.JspRuntimeContext;
import org.apache.jasper.compiler.TldLocationsCache;
import org.apache.sling.component.Component;
import org.apache.sling.jcr.SlingRepository;
import org.apache.sling.jcr.classloader.RepositoryClassLoaderProvider;
import org.apache.sling.scripting.ComponentRenderer;
import org.apache.sling.scripting.ScriptHandler;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The JSP engine (a.k.a Jasper).
 * 
 * @scr.component immediate="false" label="%jsphandler.name"
 *                description="%jsphandler.description"
 * @scr.property name="service.description" value="JSP Script Handler"
 * @scr.property name="service.vendor" value="The Apache Software Foundation" *
 * @scr.property name="jasper.checkInterval" value="300" type="Integer"
 * @scr.property name="jasper.classdebuginfo" value="true" type="Boolean"
 * @scr.property name="jasper.development" value="true" type="Boolean"
 * @scr.property name="jasper.enablePooling" value="true" type="Boolean"
 * @scr.property name="jasper.ieClassId"
 *               value="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"
 * @scr.property name="jasper.genStringAsCharArray" value="false" type="Boolean"
 * @scr.property name="jasper.keepgenerated" value="true" type="Boolean"
 * @scr.property name="jasper.mappedfile" value="true" type="Boolean"
 * @scr.property name="jasper.modificationTestInterval" value="4" type="Integer"
 * @scr.property name="jasper.reloading" value="false" type="Boolean"
 * @scr.property name="jasper.scratchdir" value="/classes"
 * @scr.property name="jasper.trimSpaces" value="false" type="Boolean"
 * @scr.service
 */
public class JspScriptHandler implements ScriptHandler {

    /** default log */
    private static final Logger log = LoggerFactory.getLogger(JspScriptHandler.class);

    private ComponentContext componentContext;

    /**
     * @scr.reference
     */
    private SlingRepository repository;

    /**
     * @scr.reference name="RepositoryClassLoaderProvider"
     *      interface="org.apache.sling.jcr.classloader.RepositoryClassLoaderProvider"
     */
    private ClassLoader jspClassLoader;

    private RepositoryOutputProvider outputProvider;

    private TldLocationsCacheSupport tldLocationsCache;

    private Map componentJspRuntimeContexts;

    public static final String SCRIPT_TYPE = "jsp";

    public String getType() {
        return SCRIPT_TYPE;
    }

    // /**
    // * Returns the {@link JspRuntimeContext} used by this instance.
    // */
    // protected JspRuntimeContext getRuntimeContext() {
    // return rctxt;
    // }
    //    
    // /**
    // * Returns the number of JSPs for which JspServletWrappers exist, i.e.,
    // * the number of JSPs that have been loaded into the webapp with which
    // * this JspScriptHandler is associated.
    // *
    // * <p>This info may be used for monitoring purposes.
    // *
    // * @return The number of JSPs that have been loaded into the webapp with
    // * which this JspScriptHandler is associated
    // */
    // public int getJspCount() {
    // return this.rctxt.getJspCount();
    // }
    //
    //
    // /**
    // * Resets the JSP reload counter.
    // *
    // * @param count Value to which to reset the JSP reload counter
    // */
    // public void setJspReloadCount(int count) {
    // this.rctxt.setJspReloadCount(count);
    // }
    //
    //
    // /**
    // * Gets the number of JSPs that have been reloaded.
    // *
    // * <p>This info may be used for monitoring purposes.
    // *
    // * @return The number of JSPs (in the webapp with which this
    // JspScriptHandler is
    // * associated) that have been reloaded
    // */
    // public int getJspReloadCount() {
    // return this.rctxt.getJspReloadCount();
    // }

    public ComponentRenderer getComponentRenderer(Component component,
            String scriptName) {
        return getJspWrapperAdapter(component, scriptName).getServletAdapter();
    }

    private JspServletWrapperAdapter getJspWrapperAdapter(Component component,
            String scriptName) {
        JspComponentContext jcc = getJspRuntimeContext(component);
        JspRuntimeContext rctxt = jcc.getRctxt();
        JspServletWrapperAdapter wrapper = (JspServletWrapperAdapter) rctxt.getWrapper(scriptName);
        if (wrapper == null) {
            synchronized (this) {
                wrapper = (JspServletWrapperAdapter) rctxt.getWrapper(scriptName);
                if (wrapper == null) {
                    // Check if the requested JSP page exists, to avoid
                    // creating unnecessary directories and files.
                    // TODO: implement the check !!
                    // if (null == servletContext.getResource(scriptName)) {
                    // response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    // jspUri);
                    // return;
                    // }
                    boolean isErrorPage = false; // exception != null;
                    try {
                        wrapper = new JspServletWrapperAdapter(
                            jcc.getServletConfig(), jcc.getOptions(),
                            scriptName, isErrorPage, rctxt);
                        rctxt.addWrapper(scriptName, wrapper);
                    } catch (JasperException je) {
                        // TODO: log
                        return null;
                    }
                }
            }
        }

        return wrapper;
    }

    // ---------- SCR integration ----------------------------------------------

    protected void activate(ComponentContext componentContext) {
        this.componentContext = componentContext;
        this.tldLocationsCache = new TldLocationsCacheSupport(
            componentContext.getBundleContext());
        this.outputProvider = new RepositoryOutputProvider(repository);
        this.componentJspRuntimeContexts = new HashMap();
    }

    protected void deactivate(ComponentContext componentContext) {
        if (log.isDebugEnabled()) {
            log.debug("JspScriptHandler.deactivate()");
        }

        for (Iterator ci = componentJspRuntimeContexts.values().iterator(); ci.hasNext();) {
            JspComponentContext jcc = (JspComponentContext) ci.next();
            jcc.getRctxt().setOutputProvider(null);
            jcc.getRctxt().destroy();
            ci.remove();
        }

        if (tldLocationsCache != null) {
            tldLocationsCache.shutdown(componentContext.getBundleContext());
            tldLocationsCache = null;
        }

        outputProvider.dispose();
        this.componentContext = null;
    }

    protected void bindRepository(SlingRepository repository) {
        this.repository = repository;
    }

    protected void unbindRepository(SlingRepository repository) {
        this.repository = null;
    }

    protected void bindRepositoryClassLoaderProvider(
            RepositoryClassLoaderProvider repositoryClassLoaderProvider) {
        try {
            this.jspClassLoader = repositoryClassLoaderProvider.getClassLoader("admin");
        } catch (RepositoryException re) {
            log.error("Cannot get JSP class loader", re);
        }
    }

    protected void unbindRepositoryClassLoaderProvider(
            RepositoryClassLoaderProvider repositoryClassLoaderProvider) {
        if (jspClassLoader != null) {
            repositoryClassLoaderProvider.ungetClassLoader(jspClassLoader);
            jspClassLoader = null;
        }
    }

    // ---------- Internal -----------------------------------------------------

    private JspComponentContext getJspRuntimeContext(Component component) {
        JspComponentContext rctxt = (JspComponentContext) componentJspRuntimeContexts.get(component.getId());
        if (rctxt == null) {
            rctxt = new JspComponentContext(component);
            componentJspRuntimeContexts.put(component.getId(), rctxt);
        }

        return rctxt;
    }

    private class JspComponentContext {

        private final ServletContext servletContext;

        private final ServletConfig servletConfig;

        private final Options options;

        private final JspRuntimeContext rctxt;

        JspComponentContext(Component component) {
            servletContext = new JspServletContext(
                component.getComponentContext(), tldLocationsCache,
                outputProvider);
            servletConfig = new JspServletConfig(servletContext);

            // return options which use the jspClassLoader
            TldLocationsCache tlc = tldLocationsCache.getTldLocationsCache(servletContext);
            options = new JspServletOptions(servletConfig, outputProvider,
                jspClassLoader, tlc);

            // set the current class loader as the thread context loader for
            // the setup of the JspRuntimeContext
            ClassLoader old = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(jspClassLoader);

            try {
                // Initialize the JSP Runtime Context
                rctxt = new JspRuntimeContext(servletContext, options);

            } finally {
                // make sure the context loader is reset after setting up the
                // JSP runtime context
                Thread.currentThread().setContextClassLoader(old);
            }

            // by default access the repository
            rctxt.setOutputProvider(outputProvider);

            if (log.isDebugEnabled()) {
                log.debug("Scratch dir for the JSP engine is: {}",
                    options.getScratchDir().toString());
                log.debug("IMPORTANT: Do not modify the generated servlets");
            }
        }

        public ServletConfig getServletConfig() {
            return servletConfig;
        }

        public ServletContext getServletContext() {
            return servletContext;
        }

        public Options getOptions() {
            return options;
        }

        public JspRuntimeContext getRctxt() {
            return rctxt;
        }
    }

    private class JspServletConfig implements ServletConfig {
        private final ServletContext servletContext;

        private String servletName;
        private Dictionary properties;

        JspServletConfig(ServletContext servletContext) {
            this.servletContext = servletContext;
            
            Dictionary props = componentContext.getProperties();
            
            // set the servlet name
            servletName = (String) props.get(Constants.SERVICE_DESCRIPTION);
            if (servletName == null) {
                servletName = "JSP Script Handler";
            }

            // copy the "jasper." properties
            properties = new Properties();
            for (Enumeration ke = props.keys(); ke.hasMoreElements();) {
                String key = (String) ke.nextElement();
                if (key.startsWith("jasper.")) {
                    properties.put(key.substring("jasper.".length()),
                        String.valueOf(props.get(key)));
                }
            }
            
        }

        public String getInitParameter(String name) {
            Object prop = getProperties().get(name);
            return (prop == null) ? null : String.valueOf(prop);
        }

        public Enumeration getInitParameterNames() {
            return getProperties().keys();
        }

        public ServletContext getServletContext() {
            return servletContext;
        }

        public String getServletName() {
            return servletName;
        }

        private Dictionary getProperties() {
            return properties;
        }
    }
}