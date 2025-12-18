package com.footstone.sqlguard.scanner.mybatis.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Information about a Mapper interface method
 */
public class MethodInfo {
    
    private final String name;
    private final String returnType;
    private final Map<String, ParameterInfo> parameters = new HashMap<>();
    private final List<ParameterInfo> parameterList = new ArrayList<>();
    private PaginationType paginationType = PaginationType.NONE;
    
    public MethodInfo(String name, String returnType) {
        this.name = name;
        this.returnType = returnType;
    }
    
    public String getName() {
        return name;
    }
    
    public String getReturnType() {
        return returnType;
    }
    
    public void addParameter(ParameterInfo parameterInfo) {
        parameters.put(parameterInfo.getName(), parameterInfo);
        parameterList.add(parameterInfo);
    }
    
    public ParameterInfo getParameter(String name) {
        return parameters.get(name);
    }
    
    public List<ParameterInfo> getParameters() {
        return new ArrayList<>(parameterList);
    }
    
    public boolean hasPagination() {
        return paginationType != PaginationType.NONE;
    }
    
    public PaginationType getPaginationType() {
        return paginationType;
    }
    
    public void setPaginationType(PaginationType paginationType) {
        this.paginationType = paginationType;
    }
}





