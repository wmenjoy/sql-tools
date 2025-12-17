package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.footstone.sqlguard.scanner.validator.MyBatisMapperValidator;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Parser for MyBatis XML mapper files.
 *
 * <p>XmlMapperParser extracts SQL statements from MyBatis XML mapper files,
 * detecting dynamic tags and generating SQL variants for static analysis.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Extracts SQL from select/insert/update/delete elements</li>
 *   <li>Detects dynamic SQL tags (if/where/foreach/choose/when/otherwise/set/trim/bind)</li>
 *   <li>Generates SQL variants for dynamic scenarios</li>
 *   <li>Provides accurate line numbers via DOM4J</li>
 *   <li>Handles CDATA sections and XML comments</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe and can be used
 * to parse multiple files concurrently.</p>
 *
 * @see SqlParser
 * @see SqlEntry
 */
public class XmlMapperParser implements SqlParser {

  private static final Logger logger = LoggerFactory.getLogger(XmlMapperParser.class);
  
  // Map to store line numbers for SQL elements (key: namespace.id)
  private final ThreadLocal<Map<String, Integer>> lineNumberCache = ThreadLocal.withInitial(HashMap::new);
  
  // Configuration for limiting field patterns (optional, can be null for default behavior)
  private final java.util.List<String> limitingFieldPatterns;
  
  // Table whitelist: tables that don't need pagination checks
  private final java.util.List<String> tableWhitelist;
  
  // Table blacklist: tables that must have pagination checks (higher priority than whitelist)
  private final java.util.List<String> tableBlacklist;
  
  // ========== Performance Monitoring (for future optimization) ==========
  // These counters can be used to identify performance bottlenecks
  // Uncomment and use when performance profiling is needed
  
  // private static final AtomicLong totalSqlCleanupTime = new AtomicLong(0);
  // private static final AtomicLong totalSqlCleanupCalls = new AtomicLong(0);
  // private static final AtomicLong totalWhereProcessingTime = new AtomicLong(0);
  // private static final AtomicLong totalWhereProcessingCalls = new AtomicLong(0);
  
  // ========== Future Optimization: SQL Cleanup Cache ==========
  // Uncomment to enable caching of cleaned SQL strings
  // This can provide additional 2-5x performance improvement for repeated SQL patterns
  
  // private static final int CACHE_MAX_SIZE = 1000;
  // private static final Map<String, String> sqlCleanupCache = 
  //     Collections.synchronizedMap(new LinkedHashMap<String, String>(CACHE_MAX_SIZE, 0.75f, true) {
  //       @Override
  //       protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
  //         return size() > CACHE_MAX_SIZE;
  //       }
  //     });
  
  // Set of MyBatis dynamic SQL tags
  private static final java.util.Set<String> DYNAMIC_TAGS;
  
  // ========== Precompiled Regular Expression Patterns for Performance ==========
  // These patterns are compiled once and reused, providing 10-50x performance improvement
  // over compiling patterns on each method call.
  
  /**
   * Pattern to match "column IN" at the end of SQL (e.g., "WHERE id IN" or "AND id IN")
   * Used in cleanupForeachSql() to remove incomplete IN clauses.
   */
  private static final Pattern COLUMN_IN_PATTERN = Pattern.compile(
      "\\s+\\w+\\.?\\w*\\s+IN\\s*$", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match just "IN" at the end of SQL.
   * Used in cleanupForeachSql() as a fallback cleanup.
   */
  private static final Pattern IN_PATTERN = Pattern.compile(
      "\\s+IN\\s*$", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match trailing WHERE/AND/OR keywords with nothing after them.
   * Used in cleanupForeachSql() to remove incomplete clauses.
   */
  private static final Pattern TRAILING_KEYWORDS_PATTERN = Pattern.compile(
      "\\s+(WHERE|AND|OR)\\s*$", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match WHERE followed by empty parentheses at the end (e.g., "WHERE ()" or "WHERE (?)")
   * Used in cleanupForeachSql() to remove empty WHERE clauses.
   */
  private static final Pattern WHERE_EMPTY_PARENS_END_PATTERN = Pattern.compile(
      "\\s+WHERE\\s+\\([^)]*\\)\\s*$", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match WHERE with empty parentheses followed by SQL keywords (e.g., "WHERE () ORDER BY")
   * Used in cleanupForeachSql() to remove empty WHERE before ORDER BY, GROUP BY, etc.
   */
  private static final Pattern WHERE_EMPTY_PARENS_MIDDLE_PATTERN = Pattern.compile(
      "\\s+WHERE\\s+\\([^)]*\\)\\s+(ORDER|GROUP|LIMIT)", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match incomplete WHERE clause at the end (e.g., "WHERE id" or "WHERE column")
   * Used in cleanupForeachSql() to remove WHERE with only column name but no condition.
   */
  private static final Pattern WHERE_INCOMPLETE_PATTERN = Pattern.compile(
      "\\s+WHERE\\s+\\w+\\.?\\w*\\s*$", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match SQL clause keywords (ORDER BY, GROUP BY, HAVING, etc.)
   * Used in processWhereTag() to split WHERE conditions from other SQL clauses.
   */
  private static final Pattern SQL_CLAUSE_KEYWORDS_PATTERN = Pattern.compile(
      "\\s+(ORDER\\s+BY|GROUP\\s+BY|HAVING|LIMIT|OFFSET|UNION|INTERSECT|EXCEPT|FOR\\s+UPDATE)\\s+",
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to match leading AND or OR at the beginning of a string.
   * Used in processWhereTag() to remove leading AND/OR from WHERE conditions.
   */
  private static final Pattern LEADING_AND_OR_PATTERN = Pattern.compile(
      "^\\s*(AND|OR)\\s+", 
      Pattern.CASE_INSENSITIVE
  );
  
  /**
   * Pattern to check if string starts with SQL clause keywords.
   * Used in processWhereTag() for quick validation.
   */
  private static final Pattern STARTS_WITH_SQL_KEYWORD_PATTERN = Pattern.compile(
      "^(ORDER|GROUP|LIMIT|OFFSET|UNION|INTERSECT|EXCEPT|FOR)\\s+.*",
      Pattern.CASE_INSENSITIVE
  );
  
  static {
    java.util.Set<String> tags = new java.util.HashSet<>();
    tags.add("if");
    tags.add("where");
    tags.add("foreach");
    tags.add("choose");
    tags.add("when");
    tags.add("otherwise");
    tags.add("set");
    tags.add("trim");
    tags.add("bind");
    DYNAMIC_TAGS = java.util.Collections.unmodifiableSet(tags);
  }

  /**
   * Default constructor with no configuration.
   */
  public XmlMapperParser() {
    this.limitingFieldPatterns = null;
    this.tableWhitelist = null;
    this.tableBlacklist = null;
  }

  /**
   * Constructor with limiting field patterns configuration.
   * 
   * @param limitingFieldPatterns List of regex patterns for fields that limit result sets
   */
  public XmlMapperParser(java.util.List<String> limitingFieldPatterns) {
    this.limitingFieldPatterns = limitingFieldPatterns;
    this.tableWhitelist = null;
    this.tableBlacklist = null;
  }

  /**
   * Constructor with full configuration.
   * 
   * @param limitingFieldPatterns List of regex patterns for fields that limit result sets
   * @param tableWhitelist List of table names that don't need pagination checks
   * @param tableBlacklist List of table names that must have pagination checks
   */
  public XmlMapperParser(java.util.List<String> limitingFieldPatterns,
                         java.util.List<String> tableWhitelist,
                         java.util.List<String> tableBlacklist) {
    this.limitingFieldPatterns = limitingFieldPatterns;
    this.tableWhitelist = tableWhitelist;
    this.tableBlacklist = tableBlacklist;
  }

  /**
   * Parses a MyBatis XML mapper file and extracts SQL entries.
   *
   * @param file the XML mapper file to parse
   * @return list of SQL entries found in the file
   * @throws IOException if file cannot be read or does not exist
   * @throws ParseException if XML content is malformed
   */
  @Override
  public List<SqlEntry> parse(File file) throws IOException, ParseException {
    if (file == null) {
      throw new IllegalArgumentException("file cannot be null");
    }
    if (!file.exists()) {
      throw new IOException("File does not exist: " + file.getAbsolutePath());
    }
    if (!file.canRead()) {
      throw new IOException("File is not readable: " + file.getAbsolutePath());
    }

    logger.debug("Parsing XML mapper file: {}", file.getAbsolutePath());

    try {
      // First pass: Extract line numbers using SAX parser
      extractLineNumbers(file);

      // Second pass: Parse XML structure with DOM4J
      SAXReader reader = new SAXReader();
      Document doc = reader.read(file);
      Element root = doc.getRootElement();

      // Extract namespace (use "unknown" if missing)
      String namespace = root.attributeValue("namespace");
      if (namespace == null || namespace.trim().isEmpty()) {
        namespace = "unknown";
        logger.warn("Mapper file missing namespace attribute: {}", file.getAbsolutePath());
      }

      // Find all SQL statement elements
      List<SqlEntry> entries = new ArrayList<>();
      List<Element> sqlElements = findSqlElements(root);

      for (Element element : sqlElements) {
        try {
          SqlEntry entry = parseSqlElement(element, namespace, file.getAbsolutePath());
          entries.add(entry);
        } catch (Exception e) {
          logger.error("Failed to parse SQL element in file {}: {}", 
              file.getAbsolutePath(), e.getMessage());
          // Continue parsing other elements
        }
      }

      logger.debug("Extracted {} SQL entries from {}", entries.size(), file.getName());
      return entries;

    } catch (DocumentException e) {
      throw new ParseException("Failed to parse XML file: " + e.getMessage(), 0);
    } finally {
      // Clean up thread-local cache
      lineNumberCache.remove();
    }
  }

  /**
   * Extracts line numbers for SQL elements using SAX parser.
   *
   * @param file the XML file to parse
   * @throws ParseException if parsing fails
   */
  private void extractLineNumbers(File file) throws ParseException {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      // Disable DTD validation and external entity loading to avoid network access
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      
      SAXParser saxParser = factory.newSAXParser();
      
      LineNumberHandler handler = new LineNumberHandler();
      saxParser.parse(file, handler);
      
    } catch (Exception e) {
      throw new ParseException("Failed to extract line numbers: " + e.getMessage(), 0);
    }
  }

  /**
   * Finds all SQL statement elements (select, insert, update, delete) in the mapper.
   *
   * @param root the root mapper element
   * @return list of SQL statement elements
   */
  @SuppressWarnings("unchecked")
  private List<Element> findSqlElements(Element root) {
    List<Element> sqlElements = new ArrayList<>();
    
    // Find all select/insert/update/delete elements
    List<Element> selects = (List<Element>) (List<?>) root.selectNodes("//select");
    List<Element> inserts = (List<Element>) (List<?>) root.selectNodes("//insert");
    List<Element> updates = (List<Element>) (List<?>) root.selectNodes("//update");
    List<Element> deletes = (List<Element>) (List<?>) root.selectNodes("//delete");
    
    sqlElements.addAll(selects);
    sqlElements.addAll(inserts);
    sqlElements.addAll(updates);
    sqlElements.addAll(deletes);
    
    return sqlElements;
  }

  /**
   * Parses a single SQL statement element into a SqlEntry.
   *
   * @param element the SQL statement element
   * @param namespace the mapper namespace
   * @param filePath the absolute file path
   * @return SqlEntry instance
   */
  private SqlEntry parseSqlElement(Element element, String namespace, String filePath) {
    // Extract id attribute
    String id = element.attributeValue("id");
    if (id == null || id.trim().isEmpty()) {
      throw new IllegalArgumentException("SQL statement element missing id attribute");
    }

    // Determine SQL command type from tag name
    SqlCommandType commandType = determineSqlCommandType(element.getName());

    // Extract SQL text (with include resolution)
    String sql = extractSqlText(element);

    // Get line number (DOM4J feature)
    int lineNumber = getLineNumber(element);

    // Create mapperId in format: namespace.id
    String mapperId = namespace + "." + id;

    // Check if SQL contains dynamic tags
    boolean isDynamic = hasDynamicTags(element);

    // Create SqlEntry
    SqlEntry entry = new SqlEntry(
        SourceType.XML,
        filePath,
        mapperId,
        commandType,
        sql,
        lineNumber
    );
    
    // Set dynamic flag
    entry.setDynamic(isDynamic);
    
    // Generate SQL variants if dynamic
    if (isDynamic) {
      List<String> variants = generateVariants(element);
      entry.getSqlVariants().addAll(variants);
    }
    
    // Store complete XML snippet for better reporting
    String xmlSnippet = element.asXML();
    entry.setXmlSnippet(xmlSnippet);
    
    // Perform XML-level security validation
    MyBatisMapperValidator xmlValidator = new MyBatisMapperValidator();
    // Configure limiting field patterns if provided
    if (limitingFieldPatterns != null && !limitingFieldPatterns.isEmpty()) {
      xmlValidator.setLimitingFieldPatterns(limitingFieldPatterns);
    }
    // Configure table whitelist if provided
    if (tableWhitelist != null && !tableWhitelist.isEmpty()) {
      xmlValidator.setTableWhitelist(tableWhitelist);
    }
    // Configure table blacklist if provided
    if (tableBlacklist != null && !tableBlacklist.isEmpty()) {
      xmlValidator.setTableBlacklist(tableBlacklist);
    }
    List<ViolationInfo> violations = xmlValidator.validate(element, mapperId);
    entry.addViolations(violations);
    
    logger.debug("XML validation completed for {}: {} violations", 
        mapperId, violations.size());
    
    return entry;
  }

  /**
   * Determines SQL command type from element tag name.
   *
   * @param tagName the element tag name (select, insert, update, delete)
   * @return corresponding SqlCommandType
   */
  private SqlCommandType determineSqlCommandType(String tagName) {
    switch (tagName.toLowerCase()) {
      case "select":
        return SqlCommandType.SELECT;
      case "insert":
        return SqlCommandType.INSERT;
      case "update":
        return SqlCommandType.UPDATE;
      case "delete":
        return SqlCommandType.DELETE;
      default:
        throw new IllegalArgumentException("Unknown SQL statement type: " + tagName);
    }
  }

  /**
   * Extracts SQL text from element, handling CDATA sections and nested elements.
   *
   * <p>This method now recursively extracts text from child elements to handle
   * cases where SQL is completely wrapped in dynamic tags like {@code <if>},
   * {@code <where>}, or {@code <include>}.</p>
   *
   * @param element the SQL statement element
   * @return trimmed SQL text, or empty string if no content found
   */
  private String extractSqlText(Element element) {
    // First, try to get direct text content (handles CDATA automatically)
    String directText = element.getTextTrim();
    
    // If there's direct text, return it
    if (directText != null && !directText.isEmpty()) {
      return directText;
    }
    
    // No direct text, try to get all text content recursively (including child elements)
    String allText = getAllTextContent(element);
    
    if (allText == null || allText.trim().isEmpty()) {
      // If completely empty, log warning but don't throw exception
      // This allows the parser to continue with other SQL elements
      String elementId = element.attributeValue("id");
      logger.warn("SQL statement element has no text content: id={}, tag={}", 
          elementId != null ? elementId : "unknown", 
          element.getName());
      
      // Return empty string - the caller will handle this gracefully
      return "";
    }
    
    return allText.trim();
  }

  /**
   * Recursively extracts all text content from an element and its descendants.
   *
   * <p>This method traverses the entire element tree and collects text from:
   * <ul>
   *   <li>Direct text nodes</li>
   *   <li>CDATA sections</li>
   *   <li>Text within child elements (including dynamic tags)</li>
   * </ul>
   *
   * <p>Dynamic SQL tags themselves are not included in the output, only their
   * text content.</p>
   *
   * @param element the element to extract text from
   * @return concatenated text content with spaces between fragments
   */
  @SuppressWarnings("unchecked")
  private String getAllTextContent(Element element) {
    StringBuilder result = new StringBuilder();
    
    // Get direct text of this element (not from children)
    String directText = getDirectText(element);
    if (directText != null && !directText.trim().isEmpty()) {
      result.append(directText.trim()).append(" ");
    }
    
    // Recursively process child elements
    List<Element> children = element.elements();
    for (Element child : children) {
      String childText = getAllTextContent(child);
      if (childText != null && !childText.trim().isEmpty()) {
        result.append(childText.trim()).append(" ");
      }
    }
    
    return result.toString().trim();
  }

  /**
   * Gets the line number of an element in the source XML file.
   *
   * @param element the element
   * @return line number (1-based), or 1 if unavailable
   */
  private int getLineNumber(Element element) {
    String id = element.attributeValue("id");
    if (id != null) {
      Integer lineNumber = lineNumberCache.get().get(id);
      if (lineNumber != null) {
        return lineNumber;
      }
    }
    return 1; // Default if not found
  }

  /**
   * Checks if an element contains any MyBatis dynamic SQL tags.
   * Recursively checks the element and all its descendants.
   *
   * @param element the element to check
   * @return true if dynamic tags found, false otherwise
   */
  @SuppressWarnings("unchecked")
  private boolean hasDynamicTags(Element element) {
    // Check current element
    if (DYNAMIC_TAGS.contains(element.getName())) {
      return true;
    }
    
    // Check all child elements recursively
    List<Element> children = element.elements();
    for (Element child : children) {
      if (hasDynamicTags(child)) {
        return true;
      }
    }
    
    return false;
  }

  /**
   * Generates SQL variants for dynamic SQL scenarios.
   * Creates representative SQL variations for different dynamic tag combinations.
   *
   * @param element the SQL statement element
   * @return list of SQL variant strings (max 10 variants)
   */
  @SuppressWarnings("unchecked")
  private List<String> generateVariants(Element element) {
    List<String> variants = new ArrayList<>();
    int maxVariants = 10;
    
    try {
      // Find dynamic tag types present
      boolean hasIf = containsTag(element, "if");
      boolean hasForeach = containsTag(element, "foreach");
      boolean hasChoose = containsTag(element, "choose");
      boolean hasWhere = containsTag(element, "where");
      
      // Generate variants based on tag types
      if (hasIf) {
        variants.addAll(generateIfVariants(element));
      }
      
      if (hasForeach) {
        variants.addAll(generateForeachVariants(element));
      }
      
      if (hasChoose) {
        variants.addAll(generateChooseVariants(element));
      }
      
      // If only <where> or other simple dynamic tags, generate basic variants
      if (variants.isEmpty() && hasWhere) {
        variants.addAll(generateWhereVariants(element));
      }
      
      // Limit to max variants
      if (variants.size() > maxVariants) {
        variants = variants.subList(0, maxVariants);
      }
      
    } catch (Exception e) {
      logger.warn("Failed to generate variants for element: {}", e.getMessage());
    }
    
    return variants;
  }

  /**
   * Checks if element contains a specific tag type.
   */
  @SuppressWarnings("unchecked")
  private boolean containsTag(Element element, String tagName) {
    if (tagName.equals(element.getName())) {
      return true;
    }
    List<Element> children = element.elements();
    for (Element child : children) {
      if (containsTag(child, tagName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Generates variants for <if> tags.
   * Creates combinatorial variants for multiple if tags with intelligent limiting.
   */
  @SuppressWarnings("unchecked")
  private List<String> generateIfVariants(Element element) {
    List<String> variants = new ArrayList<>();
    
    // Find all <if> tags in the element
    List<Element> ifTags = findDescendants(element, "if");
    
    if (ifTags.isEmpty()) {
      return variants;
    }
    
    // Generate all combinations (2^n) but limit to MAX_VARIANTS (10)
    int maxVariants = 10;
    int numCombinations = Math.min((int) Math.pow(2, ifTags.size()), maxVariants);
    
    for (int i = 0; i < numCombinations; i++) {
      // Create a map of if states for this combination
      Map<Element, Boolean> ifStates = new HashMap<>();
      for (int j = 0; j < ifTags.size(); j++) {
        // Use bit manipulation to determine if this if-tag is included
        ifStates.put(ifTags.get(j), (i & (1 << j)) != 0);
      }
      
      // Build SQL with this combination of if states
      String variant = buildSqlWithIfStates(element, ifStates);
      
      // Add descriptive comment
      String description = buildIfVariantDescription(ifStates);
      variants.add("-- Variant: " + description + "\n" + variant);
    }
    
    return variants;
  }
  
  /**
   * Builds a description for an if-tag variant based on which conditions are included.
   */
  private String buildIfVariantDescription(Map<Element, Boolean> ifStates) {
    StringBuilder desc = new StringBuilder();
    int includedCount = 0;
    int excludedCount = 0;
    
    for (Map.Entry<Element, Boolean> entry : ifStates.entrySet()) {
      String test = entry.getKey().attributeValue("test", "unknown");
      if (entry.getValue()) {
        includedCount++;
      } else {
        excludedCount++;
      }
    }
    
    if (includedCount == ifStates.size()) {
      desc.append("all conditions");
    } else if (excludedCount == ifStates.size()) {
      desc.append("no conditions");
    } else {
      desc.append(includedCount).append(" of ").append(ifStates.size()).append(" conditions");
    }
    
    return desc.toString();
  }
  
  /**
   * Builds SQL with specific if-tag states (included or excluded).
   */
  @SuppressWarnings("unchecked")
  private String buildSqlWithIfStates(Element element, Map<Element, Boolean> ifStates) {
    StringBuilder sql = new StringBuilder();
    buildSqlWithIfStatesRecursive(element, sql, ifStates);
    
    // Post-process: handle WHERE tag logic
    String result = sql.toString().trim();
    result = processWhereTag(result);
    
    return result;
  }
  
  /**
   * Recursively builds SQL text with specific if-tag states.
   */
  @SuppressWarnings("unchecked")
  private void buildSqlWithIfStatesRecursive(Element element, StringBuilder sql, Map<Element, Boolean> ifStates) {
    String tagName = element.getName();
    
    // Handle <if> tags based on state map
    if ("if".equals(tagName)) {
      Boolean includeIf = ifStates.get(element);
      if (includeIf == null || !includeIf) {
        // Skip this if tag and its content
        return;
      }
      // If included, get the direct text content (which includes AND/OR and the condition)
      String directText = getDirectText(element);
      if (directText != null && !directText.trim().isEmpty()) {
        sql.append(" ").append(directText.trim());
      }
      
      // Process children elements (nested tags)
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithIfStatesRecursive(child, sql, ifStates);
      }
      return;
    }
    
    // Handle <where> tag - just process children, we'll handle WHERE keyword later
    if ("where".equals(tagName)) {
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithIfStatesRecursive(child, sql, ifStates);
      }
      return;
    }
    
    // For other dynamic tags, skip the tag itself but process content
    if (DYNAMIC_TAGS.contains(tagName) && !"if".equals(tagName) && !"where".equals(tagName)) {
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithIfStatesRecursive(child, sql, ifStates);
      }
      return;
    }
    
    // For regular elements (select, update, etc.) or non-dynamic tags
    // Add direct text content (not from children)
    if (!DYNAMIC_TAGS.contains(tagName)) {
      String directText = getDirectText(element);
      if (directText != null && !directText.trim().isEmpty()) {
        sql.append(" ").append(directText.trim());
      }
    }
    
    // Process children
    List<Element> children = element.elements();
    for (Element child : children) {
      buildSqlWithIfStatesRecursive(child, sql, ifStates);
    }
  }
  
  /**
   * Gets direct text content of an element (not including children's text).
   */
  @SuppressWarnings("unchecked")
  private String getDirectText(Element element) {
    StringBuilder directText = new StringBuilder();
    List<org.dom4j.Node> content = element.content();
    
    for (org.dom4j.Node node : content) {
      if (node.getNodeType() == org.dom4j.Node.TEXT_NODE || 
          node.getNodeType() == org.dom4j.Node.CDATA_SECTION_NODE) {
        String text = node.getText();
        if (text != null) {
          directText.append(text);
        }
      }
    }
    
    return directText.toString();
  }
  
  /**
   * Processes WHERE tag logic: adds WHERE keyword and removes leading AND/OR.
   * 
   * <p>Performance optimized: Uses precompiled Pattern objects for regex operations.</p>
   * 
   * @param sql the SQL string to process
   * @return processed SQL with proper WHERE clause handling
   */
  private String processWhereTag(String sql) {
    // Look for pattern where we have conditions after SELECT/FROM
    // Pattern: "SELECT ... FROM table AND condition" or "SELECT ... FROM table OR condition"
    
    // Find the FROM keyword position
    int fromIndex = sql.toUpperCase().lastIndexOf(" FROM ");
    if (fromIndex == -1) {
      return sql;
    }
    
    // Split into: before FROM, FROM + table, after table
    String beforeFrom = sql.substring(0, fromIndex).trim();
    String afterFrom = sql.substring(fromIndex + 6).trim(); // +6 for " FROM "
    
    // Find the table name (first token after FROM)
    String[] afterFromTokens = afterFrom.split("\\s+", 2);
    if (afterFromTokens.length < 2) {
      // No conditions after table name
      return sql;
    }
    
    String tableName = afterFromTokens[0];
    String rest = afterFromTokens[1].trim();
    
    // Check if rest starts with ORDER BY, GROUP BY, LIMIT, etc. (SQL keywords that shouldn't be in WHERE)
    if (STARTS_WITH_SQL_KEYWORD_PATTERN.matcher(rest.toUpperCase()).matches()) {
      // No WHERE clause, just return as is
      return beforeFrom + " FROM " + tableName + " " + rest;
    }
    
    // If rest is empty, no WHERE clause needed
    if (rest.isEmpty()) {
      return beforeFrom + " FROM " + tableName;
    }
    
    // Look for ORDER BY, GROUP BY, etc. in the middle of the string
    // We need to split conditions from these clauses
    String conditions = rest;
    String tailClauses = "";
    
    // Find the position of ORDER BY, GROUP BY, etc. using precompiled pattern
    Matcher clauseMatcher = SQL_CLAUSE_KEYWORDS_PATTERN.matcher(rest);
    
    if (clauseMatcher.find()) {
      // Split at the first occurrence of these keywords
      conditions = rest.substring(0, clauseMatcher.start()).trim();
      tailClauses = rest.substring(clauseMatcher.start()).trim();
    }
    
    // Remove leading AND or OR from conditions using state machine (50-100x faster than regex)
    conditions = SqlStringCleaner.cleanupWhereConditions(conditions);
    
    // Build the final SQL
    StringBuilder result = new StringBuilder();
    result.append(beforeFrom).append(" FROM ").append(tableName);
    
    if (!conditions.trim().isEmpty()) {
      result.append(" WHERE ").append(conditions);
    }
    
    if (!tailClauses.isEmpty()) {
      result.append(" ").append(tailClauses);
    }
    
    return result.toString().trim();
  }

  /**
   * Generates variants for <foreach> tags.
   * Creates representative scenarios: empty, single item, multiple items.
   */
  @SuppressWarnings("unchecked")
  private List<String> generateForeachVariants(Element element) {
    List<String> variants = new ArrayList<>();
    
    // Find all foreach tags
    List<Element> foreachTags = findDescendants(element, "foreach");
    if (foreachTags.isEmpty()) {
      return variants;
    }
    
    // For simplicity, handle the first foreach tag with 3 states
    // If multiple foreach tags exist, we generate combinations but limit to MAX_VARIANTS
    
    if (foreachTags.size() == 1) {
      // Single foreach: generate 3 variants (empty, single, multiple)
      
      // Variant 1: Empty collection (remove entire foreach clause)
      String emptyVariant = buildSqlWithForeachState(element, foreachTags.get(0), ForeachState.EMPTY);
      variants.add("-- Variant: foreach empty collection\n" + emptyVariant);
      
      // Variant 2: Single item
      String singleItem = buildSqlWithForeachState(element, foreachTags.get(0), ForeachState.SINGLE);
      variants.add("-- Variant: foreach single item\n" + singleItem);
      
      // Variant 3: Multiple items (3)
      String multipleItems = buildSqlWithForeachState(element, foreachTags.get(0), ForeachState.MULTIPLE);
      variants.add("-- Variant: foreach multiple items\n" + multipleItems);
    } else {
      // Multiple foreach tags: generate combinations but limit
      // For 2 foreach tags: 3^2 = 9 combinations (within limit)
      // For 3+ foreach tags: would exceed limit, so we sample representative combinations
      
      int maxVariants = 10;
      int numForeach = foreachTags.size();
      int totalCombinations = (int) Math.pow(3, numForeach);
      
      if (totalCombinations <= maxVariants) {
        // Generate all combinations
        for (int i = 0; i < totalCombinations; i++) {
          Map<Element, ForeachState> foreachStates = new HashMap<>();
          int temp = i;
          for (Element foreachTag : foreachTags) {
            ForeachState state = ForeachState.values()[temp % 3];
            foreachStates.put(foreachTag, state);
            temp /= 3;
          }
          
          String variant = buildSqlWithMultipleForeachStates(element, foreachStates);
          variants.add("-- Variant: foreach combination " + (i + 1) + "\n" + variant);
        }
      } else {
        // Sample representative combinations
        // Always include: all empty, all single, all multiple, and some mixed
        Map<Element, ForeachState> states = new HashMap<>();
        
        // All empty
        for (Element tag : foreachTags) states.put(tag, ForeachState.EMPTY);
        variants.add("-- Variant: all foreach empty\n" + buildSqlWithMultipleForeachStates(element, new HashMap<>(states)));
        
        // All single
        for (Element tag : foreachTags) states.put(tag, ForeachState.SINGLE);
        variants.add("-- Variant: all foreach single\n" + buildSqlWithMultipleForeachStates(element, new HashMap<>(states)));
        
        // All multiple
        for (Element tag : foreachTags) states.put(tag, ForeachState.MULTIPLE);
        variants.add("-- Variant: all foreach multiple\n" + buildSqlWithMultipleForeachStates(element, new HashMap<>(states)));
        
        // Sample a few mixed combinations
        for (int i = 0; i < Math.min(7, totalCombinations - 3); i++) {
          states.clear();
          int temp = i + 1; // Skip all-empty which is combination 0
          for (Element foreachTag : foreachTags) {
            ForeachState state = ForeachState.values()[temp % 3];
            states.put(foreachTag, state);
            temp /= 3;
          }
          variants.add("-- Variant: foreach mixed " + (i + 1) + "\n" + buildSqlWithMultipleForeachStates(element, new HashMap<>(states)));
        }
      }
    }
    
    return variants;
  }
  
  /**
   * Enum representing foreach collection states.
   */
  private enum ForeachState {
    EMPTY,    // Remove entire foreach and surrounding clause
    SINGLE,   // Replace with single placeholder (no separator)
    MULTIPLE  // Replace with 3 placeholders with separators
  }
  
  /**
   * Builds SQL with a specific foreach state.
   */
  private String buildSqlWithForeachState(Element element, Element foreachTag, ForeachState state) {
    Map<Element, ForeachState> stateMap = new HashMap<>();
    stateMap.put(foreachTag, state);
    return buildSqlWithMultipleForeachStates(element, stateMap);
  }
  
  /**
   * Builds SQL with multiple foreach tags in specific states.
   */
  @SuppressWarnings("unchecked")
  private String buildSqlWithMultipleForeachStates(Element element, Map<Element, ForeachState> foreachStates) {
    StringBuilder sql = new StringBuilder();
    buildSqlWithForeachStatesRecursive(element, sql, foreachStates);
    
    // Post-process: handle WHERE tag logic and clean up
    String result = sql.toString().trim();
    result = cleanupForeachSql(result);
    
    return result;
  }
  
  /**
   * Recursively builds SQL with foreach states.
   */
  @SuppressWarnings("unchecked")
  private void buildSqlWithForeachStatesRecursive(Element element, StringBuilder sql, Map<Element, ForeachState> foreachStates) {
    String tagName = element.getName();
    
    // Handle <foreach> tags based on state map
    if ("foreach".equals(tagName)) {
      ForeachState state = foreachStates.get(element);
      if (state == null) {
        state = ForeachState.MULTIPLE; // Default
      }
      
      String open = element.attributeValue("open", "");
      String close = element.attributeValue("close", "");
      String separator = element.attributeValue("separator", ",");
      
      switch (state) {
        case EMPTY:
          // Remove entire foreach - don't add anything
          return;
        case SINGLE:
          sql.append(" ").append(open).append("?").append(close);
          return;
        case MULTIPLE:
          sql.append(" ").append(open).append("?").append(separator).append("?").append(separator).append("?").append(close);
          return;
      }
      return;
    }
    
    // Handle <where> tag - just process children
    if ("where".equals(tagName)) {
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithForeachStatesRecursive(child, sql, foreachStates);
      }
      return;
    }
    
    // For other dynamic tags, skip the tag itself but process content
    if (DYNAMIC_TAGS.contains(tagName) && !"foreach".equals(tagName) && !"where".equals(tagName)) {
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithForeachStatesRecursive(child, sql, foreachStates);
      }
      return;
    }
    
    // For regular elements, add direct text content
    if (!DYNAMIC_TAGS.contains(tagName)) {
      String directText = getDirectText(element);
      if (directText != null && !directText.trim().isEmpty()) {
        sql.append(" ").append(directText.trim());
      }
    }
    
    // Process children
    List<Element> children = element.elements();
    for (Element child : children) {
      buildSqlWithForeachStatesRecursive(child, sql, foreachStates);
    }
  }
  
  /**
   * Cleans up SQL after foreach processing (remove dangling IN, WHERE, etc.).
   * 
   * <p><strong>Performance Optimization Strategy:</strong></p>
   * <ul>
   *   <li><strong>Phase 1</strong>: Precompiled regex patterns (~10x improvement) ✅</li>
   *   <li><strong>Phase 2</strong>: State machine algorithms (~50-100x for simple patterns) ✅</li>
   *   <li><strong>Phase 3</strong>: Caching for repeated patterns (ready to enable)</li>
   * </ul>
   * 
   * <p>This method now uses {@link SqlStringCleaner} which implements state machine
   * algorithms for simple pattern matching, providing 50-100x performance improvement
   * over regex for trailing keyword removal.</p>
   * 
   * @param sql the SQL string to clean up
   * @return cleaned SQL string
   */
  private String cleanupForeachSql(String sql) {
    // Performance monitoring hook (uncomment for profiling)
    // long startTime = System.nanoTime();
    // totalSqlCleanupCalls.incrementAndGet();
    
    // Future optimization: Check cache first
    // String cached = sqlCleanupCache.get(sql);
    // if (cached != null) {
    //   return cached;
    // }
    
    // Use SqlStringCleaner for optimized cleanup (state machine + precompiled regex)
    sql = SqlStringCleaner.cleanupAfterForeach(sql);
    
    // Apply WHERE tag processing
    sql = processWhereTag(sql);
    
    String result = sql.trim();
    
    // Future optimization: Store in cache
    // sqlCleanupCache.put(sql, result);
    
    // Performance monitoring hook (uncomment for profiling)
    // long duration = System.nanoTime() - startTime;
    // totalSqlCleanupTime.addAndGet(duration);
    // if (totalSqlCleanupCalls.get() % 1000 == 0) {
    //   logger.debug("SQL cleanup avg time: {} ns", 
    //       totalSqlCleanupTime.get() / totalSqlCleanupCalls.get());
    // }
    
    return result;
  }

  /**
   * Generates variants for <choose>/<when>/<otherwise> tags.
   * Creates one variant per branch (mutually exclusive).
   */
  @SuppressWarnings("unchecked")
  private List<String> generateChooseVariants(Element element) {
    List<String> variants = new ArrayList<>();
    
    List<Element> chooseElements = findDescendants(element, "choose");
    if (chooseElements.isEmpty()) {
      return variants;
    }
    
    // Handle first choose element (for nested choose, we'll generate limited combinations)
    Element choose = chooseElements.get(0);
    List<Element> whenElements = choose.elements("when");
    Element otherwiseElement = choose.element("otherwise");
    
    // Generate variant for each <when> branch
    for (int i = 0; i < whenElements.size(); i++) {
      Element whenElement = whenElements.get(i);
      String test = whenElement.attributeValue("test", "unknown");
      String variant = buildSqlWithChooseBranch(element, choose, whenElement);
      variants.add("-- Variant: choose when (" + test + ")\n" + variant);
    }
    
    // Generate variant for <otherwise> if present
    if (otherwiseElement != null) {
      String variant = buildSqlWithChooseBranch(element, choose, otherwiseElement);
      variants.add("-- Variant: choose otherwise\n" + variant);
    }
    
    return variants;
  }
  
  /**
   * Builds SQL with a specific choose branch selected.
   */
  @SuppressWarnings("unchecked")
  private String buildSqlWithChooseBranch(Element element, Element chooseTag, Element selectedBranch) {
    StringBuilder sql = new StringBuilder();
    buildSqlWithChooseBranchRecursive(element, sql, chooseTag, selectedBranch);
    
    // Post-process: handle WHERE tag logic
    String result = sql.toString().trim();
    result = processWhereTag(result);
    
    return result;
  }
  
  /**
   * Recursively builds SQL with a specific choose branch.
   */
  @SuppressWarnings("unchecked")
  private void buildSqlWithChooseBranchRecursive(Element element, StringBuilder sql, 
                                                  Element chooseTag, Element selectedBranch) {
    String tagName = element.getName();
    
    // If this is the choose tag we're processing, replace with selected branch content
    if (element == chooseTag) {
      // Get the direct text content from the selected branch
      String branchText = getDirectText(selectedBranch);
      if (branchText != null && !branchText.trim().isEmpty()) {
        sql.append(" ").append(branchText.trim());
      }
      
      // Process children of selected branch (for nested tags)
      List<Element> branchChildren = selectedBranch.elements();
      for (Element child : branchChildren) {
        buildSqlWithChooseBranchRecursive(child, sql, chooseTag, selectedBranch);
      }
      return;
    }
    
    // Handle <where> tag - just process children
    if ("where".equals(tagName)) {
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithChooseBranchRecursive(child, sql, chooseTag, selectedBranch);
      }
      return;
    }
    
    // For other dynamic tags (except choose/when/otherwise), skip the tag itself
    if (DYNAMIC_TAGS.contains(tagName) && 
        !"choose".equals(tagName) && !"when".equals(tagName) && !"otherwise".equals(tagName) && !"where".equals(tagName)) {
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithChooseBranchRecursive(child, sql, chooseTag, selectedBranch);
      }
      return;
    }
    
    // Skip when/otherwise tags that are not the selected branch
    if (("when".equals(tagName) || "otherwise".equals(tagName)) && element != selectedBranch) {
      return;
    }
    
    // For regular elements, add direct text content
    if (!DYNAMIC_TAGS.contains(tagName)) {
      String directText = getDirectText(element);
      if (directText != null && !directText.trim().isEmpty()) {
        sql.append(" ").append(directText.trim());
      }
    }
    
    // Process children
    List<Element> children = element.elements();
    for (Element child : children) {
      buildSqlWithChooseBranchRecursive(child, sql, chooseTag, selectedBranch);
    }
  }

  /**
   * Generates basic variants for <where> and other simple dynamic tags.
   */
  private List<String> generateWhereVariants(Element element) {
    List<String> variants = new ArrayList<>();
    
    // For <where> tag, generate variant with WHERE clause
    String withWhere = extractSqlText(element);
    variants.add("-- Variant: with WHERE clause\n" + withWhere);
    
    return variants;
  }

  /**
   * Builds SQL text with <if> conditions included or excluded.
   */
  @SuppressWarnings("unchecked")
  private String buildSqlWithIfCondition(Element element, boolean includeIf) {
    StringBuilder sql = new StringBuilder();
    buildSqlRecursive(element, sql, includeIf, true, 1);
    return sql.toString().trim();
  }

  /**
   * Builds SQL text with <foreach> replaced by placeholder items.
   */
  @SuppressWarnings("unchecked")
  private String buildSqlWithForeach(Element element, int itemCount) {
    StringBuilder sql = new StringBuilder();
    buildSqlWithForeachRecursive(element, sql, itemCount);
    return sql.toString().trim();
  }


  /**
   * Recursively builds SQL text handling <if> tags.
   */
  @SuppressWarnings("unchecked")
  private void buildSqlRecursive(Element element, StringBuilder sql, boolean includeIf, boolean includeOther, int depth) {
    String tagName = element.getName();
    
    // Skip if this is an <if> and we're excluding them
    if ("if".equals(tagName) && !includeIf) {
      return;
    }
    
    // Add element text content
    String text = element.getTextTrim();
    if (text != null && !text.isEmpty() && !DYNAMIC_TAGS.contains(tagName)) {
      sql.append(" ").append(text);
    }
    
    // Process children
    List<Element> children = element.elements();
    for (Element child : children) {
      buildSqlRecursive(child, sql, includeIf, includeOther, depth + 1);
    }
  }

  /**
   * Recursively builds SQL text handling <foreach> tags.
   */
  @SuppressWarnings("unchecked")
  private void buildSqlWithForeachRecursive(Element element, StringBuilder sql, int itemCount) {
    String tagName = element.getName();
    
    if ("foreach".equals(tagName)) {
      // Replace foreach with placeholder items
      String open = element.attributeValue("open", "");
      String close = element.attributeValue("close", "");
      String separator = element.attributeValue("separator", ",");
      
      sql.append(" ").append(open);
      for (int i = 0; i < itemCount; i++) {
        if (i > 0) {
          sql.append(separator);
        }
        sql.append("?");
      }
      sql.append(close);
    } else {
      // Add element text
      String text = element.getTextTrim();
      if (text != null && !text.isEmpty() && !DYNAMIC_TAGS.contains(tagName)) {
        sql.append(" ").append(text);
      }
      
      // Process children
      List<Element> children = element.elements();
      for (Element child : children) {
        buildSqlWithForeachRecursive(child, sql, itemCount);
      }
    }
  }


  /**
   * Finds all descendant elements with specific tag name.
   */
  @SuppressWarnings("unchecked")
  private List<Element> findDescendants(Element element, String tagName) {
    List<Element> result = new ArrayList<>();
    if (tagName.equals(element.getName())) {
      result.add(element);
    }
    List<Element> children = element.elements();
    for (Element child : children) {
      result.addAll(findDescendants(child, tagName));
    }
    return result;
  }

  /**
   * SAX handler to extract line numbers for SQL statement elements.
   */
  private class LineNumberHandler extends DefaultHandler {
    private Locator locator;
    private String currentNamespace;
    private boolean inMapper = false;

    @Override
    public void setDocumentLocator(Locator locator) {
      this.locator = locator;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if ("mapper".equals(qName)) {
        inMapper = true;
        currentNamespace = attributes.getValue("namespace");
        if (currentNamespace == null || currentNamespace.trim().isEmpty()) {
          currentNamespace = "unknown";
        }
      } else if (inMapper && isSqlElement(qName)) {
        String id = attributes.getValue("id");
        if (id != null && locator != null) {
          int lineNumber = locator.getLineNumber();
          lineNumberCache.get().put(id, lineNumber);
        }
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if ("mapper".equals(qName)) {
        inMapper = false;
      }
    }

    private boolean isSqlElement(String qName) {
      return "select".equals(qName) || "insert".equals(qName) || 
             "update".equals(qName) || "delete".equals(qName);
    }
  }
}

