package com.footstone.sqlguard.scanner.mybatis.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information extracted from a Mapper interface
 */
public class MapperInterfaceInfo {
    
    private final String namespace;
    private final Map<String, MethodInfo> methods = new HashMap<>();
    
    public MapperInterfaceInfo(String namespace) {
        this.namespace = namespace;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public void addMethod(MethodInfo methodInfo) {
        methods.put(methodInfo.getName(), methodInfo);
    }
    
    public MethodInfo getMethod(String name) {
        return methods.get(name);
    }
    
    public List<MethodInfo> getMethods() {
        return new ArrayList<>(methods.values());
    }
    
    public boolean hasMethod(String name) {
        return methods.containsKey(name);
    }
}















