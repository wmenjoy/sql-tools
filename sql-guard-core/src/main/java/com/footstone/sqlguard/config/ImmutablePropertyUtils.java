package com.footstone.sqlguard.config;

import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.beans.IntrospectionException;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Custom PropertyUtils to handle immutable configuration classes with final fields.
 *
 * <p>This class provides special property handling for configuration classes that use
 * final fields for immutability. It allows YAML deserialization to work with classes
 * that don't have setters for final fields by using constructor-based initialization.</p>
 *
 * <p><strong>Supported Classes:</strong></p>
 * <ul>
 *   <li>{@link BlacklistFieldsConfig} - Immutable fields configuration</li>
 * </ul>
 */
public class ImmutablePropertyUtils extends PropertyUtils {

    /**
     * Creates an ImmutablePropertyUtils with default settings.
     */
    public ImmutablePropertyUtils() {
        super();
        setSkipMissingProperties(true);
    }

    @Override
    protected Set<Property> createPropertySet(Class<?> type, BeanAccess bAccess) {
        Set<Property> properties = super.createPropertySet(type, bAccess);
        
        // For BlacklistFieldsConfig, make the 'fields' property writable even though it's final
        if (BlacklistFieldsConfig.class.isAssignableFrom(type)) {
            Set<Property> modifiableProperties = new TreeSet<>();
            for (Property property : properties) {
                if ("fields".equals(property.getName())) {
                    // Create a writable version of the fields property
                    modifiableProperties.add(new WritableFieldsProperty(property));
                } else {
                    modifiableProperties.add(property);
                }
            }
            return modifiableProperties;
        }
        
        return properties;
    }

    /**
     * Wrapper property that makes the 'fields' property writable for BlacklistFieldsConfig.
     */
    private static class WritableFieldsProperty extends Property {
        private final Property delegate;

        WritableFieldsProperty(Property delegate) {
            super(delegate.getName(), delegate.getType());
            this.delegate = delegate;
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            return delegate.getActualTypeArguments();
        }

        @Override
        public void set(Object object, Object value) throws Exception {
            // Use reflection to set the final field
            if (object instanceof BlacklistFieldsConfig && value instanceof Collection) {
                java.lang.reflect.Field field = BlacklistFieldsConfig.class.getDeclaredField("fields");
                field.setAccessible(true);
                field.set(object, new java.util.HashSet<>((Collection<?>) value));
            }
        }

        @Override
        public Object get(Object object) {
            return delegate.get(object);
        }

        @Override
        public <A extends java.lang.annotation.Annotation> A getAnnotation(Class<A> annotationClass) {
            // Delegate to the original property
            try {
                java.lang.reflect.Method method = Property.class.getDeclaredMethod("getAnnotation", Class.class);
                method.setAccessible(true);
                return (A) method.invoke(delegate, annotationClass);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public java.util.List<java.lang.annotation.Annotation> getAnnotations() {
            // Delegate to the original property
            try {
                java.lang.reflect.Method method = Property.class.getDeclaredMethod("getAnnotations");
                method.setAccessible(true);
                return (java.util.List<java.lang.annotation.Annotation>) method.invoke(delegate);
            } catch (Exception e) {
                return java.util.Collections.emptyList();
            }
        }
    }
}
