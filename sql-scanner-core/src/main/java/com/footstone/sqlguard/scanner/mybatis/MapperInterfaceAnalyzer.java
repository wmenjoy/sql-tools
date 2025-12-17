package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.MapperInterfaceInfo;
import com.footstone.sqlguard.scanner.mybatis.model.MethodInfo;
import com.footstone.sqlguard.scanner.mybatis.model.ParameterInfo;
import com.footstone.sqlguard.scanner.mybatis.model.PaginationType;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;

import java.util.Optional;

/**
 * Analyzer for Mapper interfaces using JavaParser
 * 
 * Extracts:
 * - Parameter types
 * - Pagination parameters
 * - Return types
 */
public class MapperInterfaceAnalyzer {
    
    /**
     * Analyze a Mapper interface from Java source code
     * 
     * @param javaCode Java source code
     * @return MapperInterfaceInfo
     */
    public MapperInterfaceInfo analyze(String javaCode) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(javaCode);
            
            // Find the interface declaration
            ClassOrInterfaceDeclaration interfaceDecl = cu.findFirst(ClassOrInterfaceDeclaration.class)
                .filter(ClassOrInterfaceDeclaration::isInterface)
                .orElseThrow(() -> new IllegalArgumentException("No interface found in Java code"));
            
            // Get fully qualified name
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
            String interfaceName = interfaceDecl.getNameAsString();
            String namespace = packageName.isEmpty() ? interfaceName : packageName + "." + interfaceName;
            
            MapperInterfaceInfo info = new MapperInterfaceInfo(namespace);
            
            // Analyze each method
            for (MethodDeclaration method : interfaceDecl.getMethods()) {
                MethodInfo methodInfo = analyzeMethod(method);
                info.addMethod(methodInfo);
            }
            
            return info;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze Mapper interface", e);
        }
    }
    
    /**
     * Analyze a single method
     */
    private MethodInfo analyzeMethod(MethodDeclaration method) {
        String methodName = method.getNameAsString();
        String returnType = extractBaseType(method.getType().asString());
        
        MethodInfo methodInfo = new MethodInfo(methodName, returnType);
        
        // Analyze parameters
        int index = 0;
        for (Parameter param : method.getParameters()) {
            String paramType = param.getType().asString();
            String paramName = extractParameterName(param);
            
            ParameterInfo paramInfo = new ParameterInfo(paramName, paramType, index);
            methodInfo.addParameter(paramInfo);
            
            // Check for pagination
            PaginationType paginationType = detectPaginationType(paramType, returnType);
            if (paginationType != PaginationType.NONE) {
                methodInfo.setPaginationType(paginationType);
            }
            
            index++;
        }
        
        return methodInfo;
    }
    
    /**
     * Extract parameter name from @Param annotation or variable name
     */
    private String extractParameterName(Parameter param) {
        // Try to find @Param annotation
        Optional<AnnotationExpr> paramAnnotation = param.getAnnotationByName("Param");
        
        if (paramAnnotation.isPresent()) {
            AnnotationExpr annotation = paramAnnotation.get();
            
            // @Param("name")
            if (annotation instanceof SingleMemberAnnotationExpr) {
                SingleMemberAnnotationExpr singleMember = (SingleMemberAnnotationExpr) annotation;
                return singleMember.getMemberValue().asStringLiteralExpr().asString();
            }
            
            // @Param(value = "name")
            if (annotation instanceof NormalAnnotationExpr) {
                NormalAnnotationExpr normalAnnotation = (NormalAnnotationExpr) annotation;
                for (MemberValuePair pair : normalAnnotation.getPairs()) {
                    if ("value".equals(pair.getNameAsString())) {
                        return pair.getValue().asStringLiteralExpr().asString();
                    }
                }
            }
        }
        
        // Fall back to variable name
        return param.getNameAsString();
    }
    
    /**
     * Detect pagination type from parameter or return type
     */
    private PaginationType detectPaginationType(String paramType, String returnType) {
        // MyBatis RowBounds
        if (paramType.contains("RowBounds")) {
            return PaginationType.MYBATIS_ROWBOUNDS;
        }
        
        // MyBatis-Plus IPage (return type)
        if (returnType.contains("IPage")) {
            return PaginationType.MYBATIS_PLUS_IPAGE;
        }
        
        // MyBatis-Plus Page (parameter)
        if (paramType.contains("Page")) {
            return PaginationType.MYBATIS_PLUS_PAGE;
        }
        
        return PaginationType.NONE;
    }
    
    /**
     * Extract base type from a type string (remove generics)
     * 
     * Examples:
     * - "List<User>" -> "List"
     * - "Map<String, Object>" -> "Map"
     * - "User" -> "User"
     */
    private String extractBaseType(String type) {
        int genericStart = type.indexOf('<');
        if (genericStart > 0) {
            return type.substring(0, genericStart);
        }
        return type;
    }
}

