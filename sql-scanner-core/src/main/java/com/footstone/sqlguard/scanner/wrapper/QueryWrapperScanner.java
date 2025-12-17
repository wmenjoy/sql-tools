package com.footstone.sqlguard.scanner.wrapper;

import com.footstone.sqlguard.scanner.model.WrapperUsage;
import com.footstone.sqlguard.scanner.parser.WrapperScanner;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Scanner implementation for detecting MyBatis-Plus QueryWrapper usage in Java source files.
 *
 * <p>This scanner recursively traverses a project directory to find all Java files
 * under src/main/java and detects instantiations of QueryWrapper, LambdaQueryWrapper,
 * UpdateWrapper, and LambdaUpdateWrapper.</p>
 *
 * <p><strong>Detection Strategy:</strong></p>
 * <ul>
 *   <li>Parse Java files using JavaParser</li>
 *   <li>Find all ObjectCreationExpr AST nodes</li>
 *   <li>Match type names against known wrapper types</li>
 *   <li>Extract method context and line numbers</li>
 *   <li>Mark all usages as needing runtime checks</li>
 * </ul>
 *
 * <p><strong>Filtering Rules:</strong></p>
 * <ul>
 *   <li>Only scan files under src/main/java (exclude test files)</li>
 *   <li>Skip target/, build/, .git/ directories</li>
 *   <li>Continue processing on parse errors (log warnings)</li>
 * </ul>
 *
 * @see WrapperScanner
 * @see WrapperUsage
 */
public class QueryWrapperScanner implements WrapperScanner {

  private static final Logger log = LoggerFactory.getLogger(QueryWrapperScanner.class);

  /**
   * MyBatis-Plus wrapper package prefix.
   */
  private static final String MP_WRAPPER_PACKAGE = "com.baomidou.mybatisplus.core.conditions";

  /**
   * Set of MyBatis-Plus wrapper simple type names to detect.
   */
  private static final Set<String> WRAPPER_SIMPLE_NAMES = new HashSet<>(Arrays.asList(
      "QueryWrapper",
      "LambdaQueryWrapper",
      "UpdateWrapper",
      "LambdaUpdateWrapper"
  ));

  /**
   * Directories to exclude from scanning.
   */
  private static final Set<String> EXCLUDED_DIRS = new HashSet<>(Arrays.asList(
      "target",
      "build",
      ".git",
      ".svn",
      "node_modules"
  ));

  @Override
  public List<WrapperUsage> scan(File projectRoot) throws IOException {
    if (!projectRoot.exists()) {
      throw new IOException("Project root does not exist: " + projectRoot.getAbsolutePath());
    }

    if (!projectRoot.isDirectory()) {
      throw new IOException("Project root is not a directory: " + projectRoot.getAbsolutePath());
    }

    log.info("Starting wrapper scan on project root: {}", projectRoot.getAbsolutePath());

    List<WrapperUsage> allUsages = new ArrayList<>();

    // Recursively find all Java files under src/main/java
    try (Stream<Path> paths = Files.walk(projectRoot.toPath())) {
      paths
          .filter(this::isJavaSourceFile)
          .forEach(path -> {
            try {
              List<WrapperUsage> usages = scanJavaFile(path.toFile());
              allUsages.addAll(usages);
            } catch (Exception e) {
              log.warn("Failed to parse Java file: {} - {}", path, e.getMessage());
            }
          });
    }

    log.info("Wrapper scan completed. Found {} wrapper usages.", allUsages.size());
    return allUsages;
  }

  /**
   * Checks if a path is a Java source file that should be scanned.
   *
   * @param path the path to check
   * @return true if the path is a Java source file under src/main/java
   */
  private boolean isJavaSourceFile(Path path) {
    if (!Files.isRegularFile(path)) {
      return false;
    }

    String pathStr = path.toString();

    // Must be a .java file
    if (!pathStr.endsWith(".java")) {
      return false;
    }

    // Must be under src/main/java
    if (!pathStr.contains("src/main/java")) {
      return false;
    }

    // Exclude files in excluded directories
    for (String excludedDir : EXCLUDED_DIRS) {
      if (pathStr.contains(File.separator + excludedDir + File.separator)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Scans a single Java file for wrapper usages.
   *
   * @param javaFile the Java file to scan
   * @return list of wrapper usages found in the file
   * @throws IOException if file cannot be read or parsed
   */
  private List<WrapperUsage> scanJavaFile(File javaFile) throws IOException {
    List<WrapperUsage> usages = new ArrayList<>();

    // Parse the Java file
    CompilationUnit cu = StaticJavaParser.parse(javaFile);

    // Find all object creation expressions
    List<ObjectCreationExpr> objectCreations = cu.findAll(ObjectCreationExpr.class);

    for (ObjectCreationExpr expr : objectCreations) {
      // Check if it's a MyBatis-Plus wrapper type
      if (isMyBatisPlusWrapper(expr)) {
        String typeName = expr.getType().getNameAsString();
        
        // Extract enclosing method/context name
        String methodName = extractEnclosingMethod(expr);

        // Get line number
        int lineNumber = expr.getBegin()
            .map(pos -> pos.line)
            .orElse(-1);

        // Create WrapperUsage
        WrapperUsage usage = new WrapperUsage(
            javaFile.getAbsolutePath(),
            methodName,
            lineNumber,
            typeName,
            true  // Always needs runtime check
        );

        usages.add(usage);
        log.debug("Detected {} at {}:{} in context {}",
            typeName, javaFile.getName(), lineNumber, methodName);
      }
    }

    return usages;
  }

  /**
   * Checks if an object creation expression is a MyBatis-Plus wrapper.
   *
   * <p>This method performs two-level verification:</p>
   * <ol>
   *   <li>Quick check: simple name must match known wrapper types</li>
   *   <li>Package verification: attempt symbol resolution to verify package</li>
   * </ol>
   *
   * <p>If symbol resolution fails (missing dependencies), falls back to simple
   * name matching with a warning log.</p>
   *
   * @param expr the object creation expression to check
   * @return true if the expression creates a MyBatis-Plus wrapper
   */
  private boolean isMyBatisPlusWrapper(ObjectCreationExpr expr) {
    String simpleName = expr.getType().getNameAsString();

    // Quick check: simple name must match
    if (!WRAPPER_SIMPLE_NAMES.contains(simpleName)) {
      return false;
    }

    // Try symbol resolution for package verification
    try {
      // Resolve the type - returns ResolvedType which we need to cast
      com.github.javaparser.resolution.types.ResolvedType resolvedType = expr.getType().resolve();
      
      // Check if it's a reference type (class/interface)
      if (resolvedType.isReferenceType()) {
        ResolvedReferenceType refType = resolvedType.asReferenceType();
        String qualifiedName = refType.getQualifiedName();
        boolean isMyBatisPlusPackage = qualifiedName.startsWith(MP_WRAPPER_PACKAGE);
        
        if (!isMyBatisPlusPackage) {
          log.debug("Skipping custom wrapper class: {}", qualifiedName);
        }
        
        return isMyBatisPlusPackage;
      } else {
        // Not a reference type, shouldn't happen for wrapper classes
        log.warn("Resolved type for {} is not a reference type, using simple name matching", simpleName);
        return true;
      }
    } catch (UnsolvedSymbolException e) {
      // Symbol resolution failed (missing dependencies on classpath)
      // Fall back to simple name matching with warning
      log.warn("Symbol resolution failed for {}, using simple name matching: {}",
          simpleName, e.getMessage());
      return true; // Assume MyBatis-Plus wrapper
    } catch (UnsupportedOperationException e) {
      // Some JavaParser operations not supported
      log.warn("Symbol resolution not supported for {}, using simple name matching: {}",
          simpleName, e.getMessage());
      return true; // Assume MyBatis-Plus wrapper
    } catch (Exception e) {
      // Catch any other resolution errors
      log.warn("Unexpected error during symbol resolution for {}, using simple name matching: {}",
          simpleName, e.getMessage());
      return true; // Assume MyBatis-Plus wrapper
    }
  }

  /**
   * Extracts the enclosing method or context name for a wrapper instantiation.
   *
   * <p>This method determines the context where a wrapper is created:</p>
   * <ul>
   *   <li>Method: returns method name</li>
   *   <li>Constructor: returns "&lt;init&gt;"</li>
   *   <li>Static initializer: returns "&lt;static&gt;"</li>
   *   <li>Instance initializer: returns "&lt;instance_init&gt;"</li>
   *   <li>Field initializer: returns "fieldName_initializer"</li>
   *   <li>Unknown: returns "unknown"</li>
   * </ul>
   *
   * <p>For nested contexts (e.g., lambda within method), returns the outermost
   * method/constructor/initializer context.</p>
   *
   * @param expr the object creation expression
   * @return the enclosing method or context name
   */
  private String extractEnclosingMethod(ObjectCreationExpr expr) {
    // Try method declaration first
    java.util.Optional<MethodDeclaration> method = expr.findAncestor(MethodDeclaration.class);
    if (method.isPresent()) {
      return method.get().getNameAsString();
    }

    // Try constructor
    java.util.Optional<ConstructorDeclaration> constructor = expr.findAncestor(ConstructorDeclaration.class);
    if (constructor.isPresent()) {
      return "<init>";
    }

    // Try initializer block (static or instance)
    java.util.Optional<InitializerDeclaration> initializer = expr.findAncestor(InitializerDeclaration.class);
    if (initializer.isPresent()) {
      return initializer.get().isStatic() ? "<static>" : "<instance_init>";
    }

    // Try field declaration (field initializer)
    java.util.Optional<FieldDeclaration> field = expr.findAncestor(FieldDeclaration.class);
    if (field.isPresent()) {
      // Get the first variable name in the field declaration
      String fieldName = field.get().getVariable(0).getNameAsString();
      return fieldName + "_initializer";
    }

    // Fallback for unknown context
    return "unknown";
  }
}

