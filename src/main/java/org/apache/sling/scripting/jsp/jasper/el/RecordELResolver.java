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

package org.apache.sling.scripting.jsp.jasper.el;

import java.beans.FeatureDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.ELResolver;
import javax.el.PropertyNotFoundException;
import javax.el.PropertyNotWritableException;

/**
 * Resolves the components of a Java record (Java 16+) by invoking their
 * accessor methods, so that <code>${record.name}</code> calls
 * <code>record.name()</code>. Records are read-only.
 * <p>
 * The record API is accessed reflectively, so this class can be compiled for
 * and loaded on Java versions without records; it then never resolves anything.
 * <p>
 * Properties that are not record components are left unresolved, so that
 * regular getters on a record are still handled by the
 * {@link javax.el.BeanELResolver}. Like with the
 * {@link javax.el.BeanELResolver}, only public records (nested in public
 * classes) are resolved.
 */
public final class RecordELResolver extends ELResolver {

	// the record API, null when running on a Java version without records
	private static final Method IS_RECORD = findMethod("java.lang.Class", "isRecord");
	private static final Method GET_RECORD_COMPONENTS = findMethod("java.lang.Class", "getRecordComponents");
	private static final Method GET_NAME = findMethod("java.lang.reflect.RecordComponent", "getName");
	private static final Method GET_ACCESSOR = findMethod("java.lang.reflect.RecordComponent", "getAccessor");

	/** Record component accessors per class, keyed by component name; empty for non-record classes. */
	private final ClassValue<Map<String, Method>> accessors;

	public RecordELResolver() {
		this(RecordELResolver::findRecordAccessors);
	}

	/**
	 * @param accessorLookup returns the accessors of the components of a class,
	 *            keyed by component name; empty if the class isn't a record
	 */
	RecordELResolver(final Function<Class<?>, Map<String, Method>> accessorLookup) {
		this.accessors = new ClassValue<Map<String, Method>>() {
			@Override
			protected Map<String, Method> computeValue(Class<?> type) {
				return accessorLookup.apply(type);
			}
		};
	}

	public Object getValue(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		Method accessor = getAccessor(base, property);
		if (accessor == null) {
			return null;
		}

		context.setPropertyResolved(true);
		try {
			return accessor.invoke(base);
		} catch (InvocationTargetException e) {
			throw new ELException(e.getCause());
		} catch (Exception e) {
			throw new ELException(e);
		}
	}

	public Class<?> getType(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		Method accessor = getAccessor(base, property);
		if (accessor == null) {
			return null;
		}

		context.setPropertyResolved(true);
		return accessor.getReturnType();
	}

	public void setValue(ELContext context, Object base, Object property,
			Object value) throws NullPointerException,
			PropertyNotFoundException, PropertyNotWritableException,
			ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (getAccessor(base, property) != null) {
			context.setPropertyResolved(true);
			throw new PropertyNotWritableException("Record component '"
					+ property + "' of " + base.getClass().getName()
					+ " is not writable");
		}
	}

	public boolean isReadOnly(ELContext context, Object base, Object property)
			throws NullPointerException, PropertyNotFoundException, ELException {
		if (context == null) {
			throw new NullPointerException();
		}

		if (getAccessor(base, property) != null) {
			context.setPropertyResolved(true);
			return true;
		}
		return false;
	}

	public Iterator<FeatureDescriptor> getFeatureDescriptors(ELContext context, Object base) {
		if (base == null) {
			return null;
		}
		Map<String, Method> components = accessors.get(base.getClass());
		if (components.isEmpty()) {
			return null;
		}
		List<FeatureDescriptor> descriptors = new ArrayList<FeatureDescriptor>();
		for (Map.Entry<String, Method> entry : components.entrySet()) {
			FeatureDescriptor descriptor = new FeatureDescriptor();
			descriptor.setName(entry.getKey());
			descriptor.setDisplayName(entry.getKey());
			descriptor.setShortDescription("");
			descriptor.setExpert(false);
			descriptor.setHidden(false);
			descriptor.setPreferred(true);
			descriptor.setValue(TYPE, entry.getValue().getReturnType());
			descriptor.setValue(RESOLVABLE_AT_DESIGN_TIME, Boolean.TRUE);
			descriptors.add(descriptor);
		}
		return descriptors.iterator();
	}

	public Class<?> getCommonPropertyType(ELContext context, Object base) {
		if (base != null && !accessors.get(base.getClass()).isEmpty()) {
			return Object.class;
		}
		return null;
	}

	private Method getAccessor(Object base, Object property) {
		if (base == null || property == null) {
			return null;
		}
		return accessors.get(base.getClass()).get(property.toString());
	}

	static Map<String, Method> findRecordAccessors(Class<?> type) {
		// the record API was added at once, so the other methods exist as well
		if (IS_RECORD == null) {
			return Collections.emptyMap();
		}
		try {
			if (!((Boolean) IS_RECORD.invoke(type)).booleanValue() || !isPublic(type)) {
				return Collections.emptyMap();
			}
			Object[] components = (Object[]) GET_RECORD_COMPONENTS.invoke(type);
			Map<String, Method> accessors = new HashMap<String, Method>();
			for (Object component : components) {
				accessors.put((String) GET_NAME.invoke(component), (Method) GET_ACCESSOR.invoke(component));
			}
			return Collections.unmodifiableMap(accessors);
		} catch (ReflectiveOperationException e) {
			return Collections.emptyMap();
		}
	}

	/** Whether the type and all classes it's nested in are public, so its accessors can be invoked. */
	static boolean isPublic(Class<?> type) {
		for (Class<?> c = type; c != null; c = c.getEnclosingClass()) {
			if (!Modifier.isPublic(c.getModifiers())) {
				return false;
			}
		}
		return true;
	}

	static Method findMethod(String className, String methodName) {
		try {
			return Class.forName(className).getMethod(methodName);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}
}
