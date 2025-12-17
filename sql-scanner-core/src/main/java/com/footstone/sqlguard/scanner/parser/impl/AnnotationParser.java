package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parser for extracting SQL statements from MyBatis annotation-based mappers.
 *
 * <p>AnnotationParser analyzes Java source files to find SQL annotations
 * (@Select, @Update, @Delete, @Insert) and extracts SQL statements along with
 * their metadata (line numbers, mapper IDs, SQL types).</p>
 *
 * <p><strong>Supported Annotations:</strong></p>
 * <ul>
 *   <li>@Select - SELECT queries</li>
 *   <li>@Update - UPDATE statements</li>
 *   <li>@Delete - DELETE statements</li>
 *   <li>@Insert - INSERT statements</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe as it maintains
 * no mutable state.</p>
 *
 * @see SqlParser
 * @see SqlEntry
 */
public class AnnotationParser implements SqlParser {

  private static final Logger logger = LoggerFactory.getLogger(AnnotationParser.class);

  /**
   * Mapping of MyBatis SQL annotation names to SqlCommandType.
   */
  private static final Map<String, SqlCommandType> SQL_ANNOTATIONS;
  
  static {
    Map<String, SqlCommandType> map = new HashMap<>();
    map.put("Select", SqlCommandType.SELECT);
    map.put("Update", SqlCommandType.UPDATE);
    map.put("Delete", SqlCommandType.DELETE);
    map.put("Insert", SqlCommandType.INSERT);
    SQL_ANNOTATIONS = map;
  }

  /**
   * Parses a Java source file to extract SQL entries from MyBatis annotations.
   *
   * @param file the Java source file to parse
   * @return list of SQL entries found in annotations
   * @throws IOException if file cannot be read
   * @throws ParseException if Java source is malformed
   */
  @Override
  public List<SqlEntry> parse(File file) throws IOException, ParseException {
    if (file == null || !file.exists()) {
      throw new IOException("File does not exist: " + file);
    }

    List<SqlEntry> entries = new ArrayList<>();

    try {
      // Parse Java source file
      CompilationUnit cu = StaticJavaParser.parse(file);

      // Get package name
      String packageName = cu.getPackageDeclaration()
          .map(pd -> pd.getNameAsString())
          .orElse("");

      // Find all class/interface declarations
      List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

      for (ClassOrInterfaceDeclaration classDecl : classes) {
        String className = classDecl.getNameAsString();
        String namespace = packageName.isEmpty() ? className : packageName + "." + className;

        // Find all method declarations
        List<MethodDeclaration> methods = classDecl.findAll(MethodDeclaration.class);

        for (MethodDeclaration method : methods) {
          String methodName = method.getNameAsString();
          String mapperId = namespace + "." + methodName;

          // Process all annotations on the method
          for (AnnotationExpr annotation : method.getAnnotations()) {
            Optional<SqlCommandType> sqlTypeOpt = getSqlCommandType(annotation);

            if (sqlTypeOpt.isPresent()) {
              SqlCommandType sqlType = sqlTypeOpt.get();
              String sql = extractSqlFromAnnotation(annotation);

              if (sql != null && !sql.trim().isEmpty()) {
                int lineNumber = annotation.getBegin()
                    .map(pos -> pos.line)
                    .orElse(1);

                try {
                  SqlEntry entry = new SqlEntry(
                      SourceType.ANNOTATION,
                      file.getAbsolutePath(),
                      mapperId,
                      sqlType,
                      sql,
                      lineNumber
                  );
                  entries.add(entry);
                  logger.debug("Extracted SQL from {}: {} at line {}", 
                      mapperId, sqlType, lineNumber);
                } catch (IllegalArgumentException e) {
                  logger.warn("Failed to create SqlEntry for {} at line {}: {}", 
                      mapperId, lineNumber, e.getMessage());
                }
              } else {
                logger.warn("Empty SQL found in {} annotation at {}", 
                    annotation.getNameAsString(), mapperId);
              }
            }
          }
        }
      }

      logger.info("Parsed {} SQL entries from {}", entries.size(), file.getName());
      return entries;

    } catch (com.github.javaparser.ParseProblemException e) {
      throw new ParseException("Failed to parse Java file: " + file.getName() + 
          " - " + e.getMessage(), 0);
    } catch (Exception e) {
      throw new IOException("Error reading file: " + file.getName(), e);
    }
  }

  /**
   * Determines if an annotation is a SQL annotation and returns its command type.
   *
   * @param annotation the annotation to check
   * @return Optional containing SqlCommandType if it's a SQL annotation, empty otherwise
   */
  private Optional<SqlCommandType> getSqlCommandType(AnnotationExpr annotation) {
    String annotationName = annotation.getNameAsString();
    
    // Handle simple names (Select, Update, etc.)
    SqlCommandType type = SQL_ANNOTATIONS.get(annotationName);
    if (type != null) {
      return Optional.of(type);
    }

    // Handle fully qualified names (org.apache.ibatis.annotations.Select)
    if (annotationName.contains(".")) {
      String simpleName = annotationName.substring(annotationName.lastIndexOf('.') + 1);
      type = SQL_ANNOTATIONS.get(simpleName);
      if (type != null) {
        return Optional.of(type);
      }
    }

    return Optional.empty();
  }

  /**
   * Extracts SQL string from annotation value.
   *
   * <p>Handles both single-member annotations (@Select("SQL")) and
   * normal annotations with named parameters (@Select(value="SQL")).</p>
   *
   * @param annotation the annotation to extract SQL from
   * @return the SQL string, or null if not found
   */
  private String extractSqlFromAnnotation(AnnotationExpr annotation) {
    if (annotation.isSingleMemberAnnotationExpr()) {
      // @Select("SQL") - single value
      return extractSqlValue(annotation.asSingleMemberAnnotationExpr().getMemberValue());
    } else if (annotation.isNormalAnnotationExpr()) {
      // @Select(value="SQL", ...) - named parameters
      return annotation.asNormalAnnotationExpr().getPairs().stream()
          .filter(p -> p.getNameAsString().equals("value"))
          .map(p -> extractSqlValue(p.getValue()))
          .filter(sql -> sql != null)
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  /**
   * Extracts SQL value from an expression (string literal or array).
   *
   * @param expr the expression to extract SQL from
   * @return the SQL string, or null if cannot extract
   */
  private String extractSqlValue(com.github.javaparser.ast.expr.Expression expr) {
    if (expr.isStringLiteralExpr()) {
      // Single string: "SELECT * FROM user"
      return expr.asStringLiteralExpr().asString();
    } else if (expr.isArrayInitializerExpr()) {
      // String array: {"SELECT *", "FROM user"}
      return expr.asArrayInitializerExpr().getValues().stream()
          .filter(com.github.javaparser.ast.expr.Expression::isStringLiteralExpr)
          .map(e -> e.asStringLiteralExpr().asString())
          .reduce((s1, s2) -> s1 + " " + s2)
          .orElse(null);
    }
    return null;
  }
}

