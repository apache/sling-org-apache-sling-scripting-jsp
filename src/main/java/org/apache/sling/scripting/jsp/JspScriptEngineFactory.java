/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.scripting.jsp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.servlet.ServletContext;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.observation.ExternalResourceChangeListener;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.api.resource.path.Path;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.api.scripting.SlingScriptHelper;
import org.apache.sling.commons.classloader.ClassLoaderWriter;
import org.apache.sling.commons.classloader.ClassLoaderWriterListener;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.commons.compiler.JavaCompiler;
import org.apache.sling.scripting.api.AbstractScriptEngineFactory;
import org.apache.sling.scripting.api.AbstractSlingScriptEngine;
import org.apache.sling.scripting.api.resource.ScriptingResourceResolverProvider;
import org.apache.sling.scripting.jsp.jasper.compiler.JspRuntimeContext;
import org.apache.sling.scripting.jsp.jasper.compiler.JspRuntimeContext.JspFactoryHandler;
import org.apache.sling.scripting.jsp.jasper.runtime.AnnotationProcessor;
import org.apache.sling.scripting.jsp.jasper.runtime.JspApplicationContextImpl;
import org.apache.sling.scripting.jsp.jasper.servlet.JspServletWrapper;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.api.scripting.SlingBindings.SLING;

/**
 * The JSP engine (a.k.a Jasper).
 *
 */
@Component(service = {javax.script.ScriptEngineFactory.class,ResourceChangeListener.class,ClassLoaderWriterListener.class},
           property = {
                   "extensions=jsp",
                   "extensions=jspf",
                   "extensions=jspx",
                   "names=jsp",
                   "names=JSP",
                   Constants.SERVICE_VENDOR + "=The Apache Software Foundation",
                   Constants.SERVICE_DESCRIPTION + "=JSP Script Handler",
                   ResourceChangeListener.CHANGES + "=CHANGED",
                   ResourceChangeListener.CHANGES + "=REMOVED",
                   ResourceChangeListener.PATHS + "=glob:**/*.jsp",
                   ResourceChangeListener.PATHS + "=glob:**/*.jspf",
                   ResourceChangeListener.PATHS + "=glob:**/*.jspx",
                   ResourceChangeListener.PATHS + "=glob:**/*.tld",
                   ResourceChangeListener.PATHS + "=glob:**/*.tag"
           })
@Designate(ocd = JspScriptEngineFactory.Config.class)
public class JspScriptEngineFactory
    extends AbstractScriptEngineFactory
    implements ResourceChangeListener,ExternalResourceChangeListener, ClassLoaderWriterListener {

    @ObjectClassDefinition(name = "Apache Sling JSP Script Handler",
            description = "The JSP Script Handler supports development of JSP " +
                 "scripts to render response content on behalf of ScriptComponents. Internally " +
                 "Jasper 6.0.14 JSP Engine is used together with the Eclipse Java Compiler to " +
                 "compile generated Java code into Java class files. Some settings of Jasper " +
                 "may be configured as shown below. Note that JSP scripts are expected in the " +
                 "JCR repository and generated Java source and class files will be written to " +
                 "the JCR repository below the configured Compilation Location.")
    public @interface Config {

        @AttributeDefinition(name = "Target Version",
                description = "The target JVM version for the compiled classes. If " +
                              "left empty, the default version, 1.6., is used. If the value \"auto\" is used, the " +
                              "current vm version will be used.")
        String jasper_compilerTargetVM() default JspServletOptions.AUTOMATIC_VERSION;

        @AttributeDefinition(name = "Source Version",
                description = "The JVM version for the java/JSP source. If " +
                              "left empty, the default version, 1.6., is used. If the value \"auto\" is used, the " +
                              "current vm version will be used.")
        String jasper_compilerSourceVM() default JspServletOptions.AUTOMATIC_VERSION;

        @AttributeDefinition(name = "Generate Debug Info",
                description = "Should the class file be compiled with " +
                         "debugging information? true or false, default true.")
        boolean jasper_classdebuginfo() default true;

        @AttributeDefinition(name = "Tag Pooling",
                description = "Determines whether tag handler pooling is " +
                        "enabled. true or false, default true.")
        boolean jasper_enablePooling() default true;

        @AttributeDefinition(name = "Plugin Class-ID",
                description = "The class-id value to be sent to Internet " +
                      "Explorer when using <jsp:plugin> tags. Default " +
                      "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93.")
        String jasper_ieClassId() default "clsid:8AD9C840-044E-11D1-B3E9-00805F499D93";

        @AttributeDefinition(name = "Char Array Strings",
                description = "Should text strings be generated as " +
                      "char arrays, to improve performance in some cases? Default false.")
        boolean jasper_genStringAsCharArray() default false;

        @AttributeDefinition(name = "Keep Generated Java",
                description = "Should we keep the generated Java source " +
                    "code for each page instead of deleting it? true or false, default true.")
        boolean jasper_keepgenerated() default true;

        @AttributeDefinition(name = "Mapped Content",
                description = "Should we generate static content with one " +
                   "print statement per input line, to ease debugging? true or false, default true.")
        boolean jasper_mappedfile() default true;

        @AttributeDefinition(name = "Trim Spaces",
                description = "Should white spaces in template text between " +
                       "actions or directives be trimmed ?, default false.")
        boolean jasper_trimSpaces() default false;

        @AttributeDefinition(name = "Display Source Fragments",
                description = "Should we include a source fragment " +
                        "in exception messages, which could be displayed to the developer")
        boolean jasper_displaySourceFragments() default false;

        @AttributeDefinition(name = "Default Session Value",
                description = "Should a session be created by default for every " +
                    "JSP page? Warning - this behavior may produce unintended results and changing " +
                    "it will not impact previously-compiled pages.")
        boolean default_is_session() default true;
    }

    /** Default logger */
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final Object BINDINGS_NOT_SWAPPED = new Object();

    private ServletContext slingServletContext;

    private volatile PrecompiledJSPRunner precompiledJSPRunner;

    @Reference
    private ClassLoaderWriter classLoaderWriter;

    @Reference
    private JavaCompiler javaCompiler;

    @Reference
    private ScriptingResourceResolverProvider scriptingResourceResolverProvider;

    private DynamicClassLoaderManager dynamicClassLoaderManager;

    private ClassLoader dynamicClassLoader;

    /** The io provider for reading and writing. */
    private SlingIOProvider ioProvider;

    private SlingTldLocationsCache tldLocationsCache;

    private JspRuntimeContext jspRuntimeContext;

    private JspServletOptions options;

    private JspServletConfig servletConfig;

    /** The handler for the jsp factories. */
    private JspFactoryHandler jspFactoryHandler;

    public static final String[] SCRIPT_TYPE = { "jsp", "jspf", "jspx" };

    public static final String[] NAMES = { "jsp", "JSP" };

    public JspScriptEngineFactory() {
        setExtensions(SCRIPT_TYPE);
        setNames(NAMES);
    }

    /**
     * @see javax.script.ScriptEngineFactory#getScriptEngine()
     */
    @Override
    public ScriptEngine getScriptEngine() {
        return new JspScriptEngine();
    }

    /**
     * @see javax.script.ScriptEngineFactory#getLanguageName()
     */
    @Override
    public String getLanguageName() {
        return "Java Server Pages";
    }

    /**
     * @see javax.script.ScriptEngineFactory#getLanguageVersion()
     */
    @Override
    public String getLanguageVersion() {
        return "2.1";
    }

    /**
     * @see javax.script.ScriptEngineFactory#getParameter(String)
     */
    @Override
    public Object getParameter(final String name) {
        if ("THREADING".equals(name)) {
            return "STATELESS";
        }

        return super.getParameter(name);
    }

    private JspServletWrapper getJspWrapper(final String scriptName) {
        JspRuntimeContext rctxt = this.getJspRuntimeContext();

    	JspServletWrapper wrapper = rctxt.getWrapper(scriptName);
        if (wrapper != null) {
            if ( wrapper.isValid() ) {
                return wrapper;
            }
            synchronized ( this ) {
                rctxt = this.getJspRuntimeContext();
                wrapper = rctxt.getWrapper(scriptName);
                if ( wrapper != null ) {
                    if ( wrapper.isValid() ) {
                        return wrapper;
                    }
                    this.renewJspRuntimeContext();
                    rctxt = this.getJspRuntimeContext();
                }
            }
        }

        wrapper = new JspServletWrapper(servletConfig, options,
                scriptName, false, rctxt);
        wrapper = rctxt.addWrapper(scriptName, wrapper);

        return wrapper;
    }

    // ---------- SCR integration ----------------------------------------------

    /**
     * Activate this component
     */
    @Activate
    protected void activate(final BundleContext bundleContext,
            final Config config,
            final Map<String, Object> properties) {
        // set the current class loader as the thread context loader for
        // the setup of the JspRuntimeContext
        final ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(this.dynamicClassLoader);

        try {
            this.jspFactoryHandler = JspRuntimeContext.initFactoryHandler();

            this.tldLocationsCache = new SlingTldLocationsCache(bundleContext);

            // prepare some classes
            ioProvider = new SlingIOProvider(this.classLoaderWriter, this.javaCompiler);

            // return options which use the jspClassLoader
            options = new JspServletOptions(slingServletContext, ioProvider,
                    properties, tldLocationsCache, config.default_is_session());

            JspServletContext jspServletContext = new JspServletContext(ioProvider,
                slingServletContext, tldLocationsCache);

            servletConfig = new JspServletConfig(jspServletContext, options.getProperties());

            this.precompiledJSPRunner = new PrecompiledJSPRunner(options);

        } finally {
            // make sure the context loader is reset after setting up the
            // JSP runtime context
            Thread.currentThread().setContextClassLoader(old);
        }

        // check for changes in jasper config
        this.checkJasperConfig();

        logger.info("Activating Apache Sling Script Engine for JSP with options {}", options.getProperties());
        logger.debug("IMPORTANT: Do not modify the generated servlet classes directly");
    }

    /**
     * Activate this component
     */
    @Deactivate
    protected void deactivate(final BundleContext bundleContext) {
        logger.info("Deactivating Apache Sling Script Engine for JSP");
        if ( this.precompiledJSPRunner != null ) {
            this.precompiledJSPRunner.cleanup();
            this.precompiledJSPRunner = null;
        }
        if ( this.tldLocationsCache != null ) {
            this.tldLocationsCache.deactivate(bundleContext);
            this.tldLocationsCache = null;
        }
        if (jspRuntimeContext != null) {
            this.destroyJspRuntimeContext(this.jspRuntimeContext);
            jspRuntimeContext = null;
        }

        ioProvider = null;
        this.jspFactoryHandler.destroy();
        this.jspFactoryHandler = null;
    }

    private static final String CONFIG_PATH = "/jsp.config";

    /**
     * Check if the jasper configuration changed.
     */
    private void checkJasperConfig() {
        boolean changed = false;
        InputStream is = null;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            is = this.classLoaderWriter.getInputStream(CONFIG_PATH);
            byte[] buffer = new byte[1024];
            int length = 0;
            while ( ( length = is.read(buffer)) != -1 ) {
                baos.write(buffer, 0, length);
            }
            final String oldKey = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            changed = !oldKey.equals(this.servletConfig.getConfigKey());
            if ( changed ) {
                logger.info("Removing all class files due to jsp configuration change");
            }
        } catch ( final IOException notFound ) {
            changed = true;
        } finally {
            if ( is != null ) {
                try {
                    is.close();
                } catch ( final IOException ignore) {
                    // ignore
                }
            }
        }
        if ( changed ) {
            try (OutputStream os = this.classLoaderWriter.getOutputStream(CONFIG_PATH)) {
                os.write(this.servletConfig.getConfigKey().getBytes(StandardCharsets.UTF_8));
            } catch (final IOException ignore) {
                // ignore
            }
            this.classLoaderWriter.delete("/org/apache/jsp");
        }
    }

    @Reference(target="(name=org.apache.sling)")
    protected void bindSlingServletContext(final ServletContext context) {
        this.slingServletContext = context;
    }

    /**
     * Unbinds the Sling ServletContext and removes any known servlet context
     * attributes preventing the bundles's class loader from being collected.
     *
     * @param slingServletContext The <code>ServletContext</code> to be unbound
     */
    protected void unbindSlingServletContext(
            final ServletContext slingServletContext) {

        // remove JspApplicationContextImpl from the servlet context,
        // otherwise a ClassCastException may be caused after this component
        // is recreated because the class loader of the
        // JspApplicationContextImpl class object is different from the one
        // stored in the servlet context same for the AnnotationProcessor
        // (which generally does not exist here)
        try {
            if (slingServletContext != null) {
                slingServletContext.removeAttribute(JspApplicationContextImpl.class.getName());
                slingServletContext.removeAttribute(AnnotationProcessor.class.getName());
            }
        } catch (NullPointerException npe) {
            // SLING-530, might be thrown on system shutdown in a servlet
            // container when using the Equinox servlet container bridge
            logger.debug(
                "unbindSlingServletContext: ServletContext might already be unavailable",
                npe);
        }

        if (this.slingServletContext == slingServletContext) {
            this.slingServletContext = null;
        }
    }

    /**
     * Bind the class load provider.
     *
     * @param rclp the new provider
     */
    @Reference(cardinality=ReferenceCardinality.MANDATORY, policy=ReferencePolicy.STATIC)
    protected void bindDynamicClassLoaderManager(final DynamicClassLoaderManager rclp) {
        if ( this.dynamicClassLoader != null ) {
            this.ungetClassLoader();
        }
        this.getClassLoader(rclp);
    }

    /**
     * Unbind the class loader provider.
     * @param rclp the old provider
     */
    protected void unbindDynamicClassLoaderManager(final DynamicClassLoaderManager rclp) {
        if ( this.dynamicClassLoaderManager == rclp ) {
            this.ungetClassLoader();
        }
    }

    /**
     * Get the class loader
     */
    private void getClassLoader(final DynamicClassLoaderManager rclp) {
        this.dynamicClassLoaderManager = rclp;
        this.dynamicClassLoader = rclp.getDynamicClassLoader();
    }

    /**
     * Unget the class loader
     */
    private void ungetClassLoader() {
        this.dynamicClassLoader = null;
        this.dynamicClassLoaderManager = null;
    }

    // ---------- Internal -----------------------------------------------------

    private class JspScriptEngine extends AbstractSlingScriptEngine {

        JspScriptEngine() {
            super(JspScriptEngineFactory.this);
        }

        /**
         * Call a JSP script
         * @param slingBindings The bindings
         */
        private void callJsp(final SlingBindings slingBindings) {
            SlingScriptHelper scriptHelper = slingBindings.getSling();
            if (scriptHelper == null) {
                throw new IllegalStateException(String.format("The %s variable is missing from the bindings.", SLING));
            }
            ResourceResolver resolver = scriptingResourceResolverProvider.getRequestScopedResourceResolver();
            if ( resolver == null ) {
                resolver = scriptHelper.getScript().getScriptResource().getResourceResolver();
            }
            final SlingIOProvider io = ioProvider;
            final JspFactoryHandler jspfh = jspFactoryHandler;
            // abort if JSP Support is shut down concurrently (SLING-2704)
            if (io == null || jspfh == null) {
                throw new RuntimeException("callJsp: JSP Script Engine seems to be shut down concurrently; not calling "+
                        scriptHelper.getScript().getScriptResource().getPath());
            }

            final ResourceResolver oldResolver = io.setRequestResourceResolver(resolver);
            jspfh.incUsage();
            try {
                final boolean contextHasPrecompiledJsp = precompiledJSPRunner
                    .callPrecompiledJSP(getJspRuntimeContext(), jspFactoryHandler, servletConfig, slingBindings);

                if (!contextHasPrecompiledJsp) {
                    final JspServletWrapper jsp = getJspWrapper(scriptHelper.getScript().getScriptResource().getPath());
                    jsp.service(slingBindings);
                }
            } finally {
                jspfh.decUsage();
                io.resetRequestResourceResolver(oldResolver);
            }
        }

        /**
         * Call the error page
         * @param slingBindings The bindings
         * @param scriptName The name of the script
         */
        private void callErrorPageJsp(final SlingBindings slingBindings, final String scriptName) {
            SlingScriptHelper scriptHelper = slingBindings.getSling();
            if (scriptHelper == null) {
                throw new IllegalStateException(String.format("The %s variable is missing from the bindings.", SLING));
            }
            ResourceResolver resolver = scriptingResourceResolverProvider.getRequestScopedResourceResolver();
            if ( resolver == null ) {
                resolver = scriptHelper.getScript().getScriptResource().getResourceResolver();
            }
            final SlingIOProvider io = ioProvider;
            final JspFactoryHandler jspfh = jspFactoryHandler;

            // abort if JSP Support is shut down concurrently (SLING-2704)
            if (io == null || jspfh == null) {
                throw new RuntimeException("callJsp: JSP Script Engine seems to be shut down concurrently; not calling "+
                        scriptHelper.getScript().getScriptResource().getPath());
            }

            final ResourceResolver oldResolver = io.setRequestResourceResolver(resolver);
            jspfh.incUsage();
            try {
                final JspServletWrapper errorJsp = getJspWrapper(scriptName);
                errorJsp.service(slingBindings);

                // The error page could be inside an include.
                final SlingHttpServletRequest request = slingBindings.getRequest();
                if (request != null) {
                    final Throwable t = (Throwable) request.getAttribute("javax.servlet.jsp.jspException");

                    final Object newException = request
                            .getAttribute("javax.servlet.error.exception");

                    // t==null means the attribute was not set.
                    if ((newException != null) && (newException == t)) {
                        request.removeAttribute("javax.servlet.error.exception");
                    }

                    // now clear the error code - to prevent double handling.
                    request.removeAttribute("javax.servlet.error.status_code");
                    request.removeAttribute("javax.servlet.error.request_uri");
                    request.removeAttribute("javax.servlet.error.status_code");
                    request.removeAttribute("javax.servlet.jsp.jspException");
                }
            } finally {
                jspfh.decUsage();
                io.resetRequestResourceResolver(oldResolver);
            }
        }

        @Override
        public Object eval(final Reader script, final ScriptContext context) throws ScriptException {
            Bindings props = context.getBindings(ScriptContext.ENGINE_SCOPE);
            SlingBindings slingBindings = new SlingBindings();
            slingBindings.putAll(props);
            SlingScriptHelper scriptHelper = (SlingScriptHelper) props.get(SLING);
            if (scriptHelper != null) {

                // set the current class loader as the thread context loader for
                // the compilation and execution of the JSP script
                ClassLoader old = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(dynamicClassLoader);

                SlingHttpServletRequest request = slingBindings.getRequest();
                Object oldSlingBindings = BINDINGS_NOT_SWAPPED;
                if (request != null) {
                    oldSlingBindings = request.getAttribute(SlingBindings.class.getName());
                    request.setAttribute(SlingBindings.class.getName(), slingBindings);
                }
                try {
                    callJsp(slingBindings);
                } catch (final SlingPageException sje) {
                    try {
                        callErrorPageJsp(slingBindings, sje.getErrorPage());
                    } catch (final Exception e) {
                        throw e;
                    }

                } finally {

                    // make sure the context loader is reset after setting up the
                    // JSP runtime context
                    Thread.currentThread().setContextClassLoader(old);
                    if (request != null && oldSlingBindings != BINDINGS_NOT_SWAPPED) {
                        request.setAttribute(SlingBindings.class.getName(), oldSlingBindings);
                    }
                }
            }
            return null;
        }
    }

    private void destroyJspRuntimeContext(final JspRuntimeContext jrc) {
        if (jrc != null) {
            try {
                jrc.destroy();
            } catch (final NullPointerException npe) {
                // SLING-530, might be thrown on system shutdown in a servlet
                // container when using the Equinox servlet container bridge
                logger.debug("deactivate: ServletContext might already be unavailable", npe);
            }
        }
    }

    private JspRuntimeContext getJspRuntimeContext() {
        if ( this.jspRuntimeContext == null ) {
            synchronized ( this ) {
                if ( this.jspRuntimeContext == null ) {
                    // Initialize the JSP Runtime Context
                    this.jspRuntimeContext = new JspRuntimeContext(slingServletContext,
                        options, ioProvider);
                }
            }
        }
        return this.jspRuntimeContext;
    }

    @Override
	public void onChange(final List<ResourceChange> changes) {
    	for(final ResourceChange change : changes){
            final JspRuntimeContext rctxt = this.jspRuntimeContext;
            if ( rctxt != null && rctxt.handleModification(change.getPath(), change.getType() == ChangeType.REMOVED) ) {
                renewJspRuntimeContext();
            }
    	}
    }

    /**
     * Renew the jsp runtime context.
     * A new context is created, the old context is destroyed in the background
     */
    private void renewJspRuntimeContext() {
        final JspRuntimeContext jrc;
        synchronized ( this ) {
            jrc = this.jspRuntimeContext;
            this.jspRuntimeContext = null;
        }
        final Thread t = new Thread() {
            @Override
            public void run() {
                destroyJspRuntimeContext(jrc);
            }
        };
        t.start();
    }

    @Override
    public void onClassLoaderClear(String context) {
        final JspRuntimeContext rctxt = this.jspRuntimeContext;
        if (rctxt != null && context != null) {
            Path path = new Path(context);
            if (path.matches("/org/apache/jsp")) {
                renewJspRuntimeContext();
            }
        }
    }
}
