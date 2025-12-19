package com.footstone.sqlguard.scanner.mybatis;

import org.apache.ibatis.scripting.xmltags.*;
import org.apache.ibatis.session.Configuration;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Lightweight SqlNode builder that reuses MyBatis's SqlNode system
 * without requiring full Configuration or class loading.
 *
 * <p>This builder can process MyBatis dynamic SQL tags like {@code <if>},
 * {@code <choose>}, {@code <foreach>}, etc., and generate executable SQL
 * without loading any Java classes from the target project.</p>
 *
 * <p><strong>Key Advantages:</strong></p>
 * <ul>
 *   <li>No class loading required</li>
 *   <li>Full support for MyBatis dynamic SQL tags</li>
 *   <li>Reuses battle-tested MyBatis code</li>
 *   <li>Fast and lightweight</li>
 * </ul>
 *
 * @author SQL Guard Team
 * @since 1.0.0
 */
public class LightweightSqlNodeBuilder {

  private static final Logger logger = LoggerFactory.getLogger(LightweightSqlNodeBuilder.class);

  private final Configuration configuration;

  public LightweightSqlNodeBuilder() {
    this.configuration = new Configuration();
  }

  /**
   * Builds a SqlNode tree from XML element.
   *
   * @param element the XML element containing SQL and dynamic tags
   * @return the root SqlNode
   */
  public SqlNode buildSqlNode(Element element) {
    List<SqlNode> contents = new ArrayList<>();

    // Process all child nodes
    for (Object obj : element.content()) {
      if (obj instanceof Text) {
        // Static text node
        Text textNode = (Text) obj;
        String text = textNode.getText();
        if (text != null && !text.trim().isEmpty()) {
          contents.add(new StaticTextSqlNode(text));
        }
      } else if (obj instanceof Element) {
        // Dynamic SQL tag
        Element child = (Element) obj;
        SqlNode sqlNode = parseDynamicTag(child);
        if (sqlNode != null) {
          contents.add(sqlNode);
        }
      }
    }

    return new MixedSqlNode(contents);
  }

  /**
   * Parses a dynamic SQL tag element.
   *
   * @param element the tag element
   * @return the corresponding SqlNode, or null if unknown tag
   */
  private SqlNode parseDynamicTag(Element element) {
    String nodeName = element.getName();

    switch (nodeName) {
      case "if":
        return parseIfTag(element);
      case "choose":
        return parseChooseTag(element);
      case "when":
      case "otherwise":
        // These are handled by choose tag
        return buildSqlNode(element);
      case "foreach":
        return parseForeachTag(element);
      case "where":
        return parseWhereTag(element);
      case "set":
        return parseSetTag(element);
      case "trim":
        return parseTrimTag(element);
      case "bind":
        // Bind tag doesn't generate SQL, skip it
        return null;
      default:
        // Unknown tag, treat as static text
        logger.debug("Unknown dynamic tag: {}, treating as static text", nodeName);
        return new StaticTextSqlNode(element.asXML());
    }
  }

  /**
   * Parses {@code <if test="...">} tag.
   */
  private SqlNode parseIfTag(Element element) {
    String test = element.attributeValue("test");
    SqlNode contents = buildSqlNode(element);
    return new IfSqlNode(contents, test);
  }

  /**
   * Parses {@code <choose>} tag with {@code <when>} and {@code <otherwise>}.
   */
  private SqlNode parseChooseTag(Element element) {
    List<SqlNode> whenSqlNodes = new ArrayList<>();
    SqlNode defaultSqlNode = null;

    for (Object obj : element.elements()) {
      if (obj instanceof Element) {
        Element child = (Element) obj;
        String nodeName = child.getName();

        if ("when".equals(nodeName)) {
          String test = child.attributeValue("test");
          SqlNode contents = buildSqlNode(child);
          whenSqlNodes.add(new IfSqlNode(contents, test));
        } else if ("otherwise".equals(nodeName)) {
          defaultSqlNode = buildSqlNode(child);
        }
      }
    }

    return new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
  }

  /**
   * Parses {@code <foreach>} tag.
   */
  private SqlNode parseForeachTag(Element element) {
    String collection = element.attributeValue("collection");
    String item = element.attributeValue("item");
    String index = element.attributeValue("index");
    String open = element.attributeValue("open");
    String close = element.attributeValue("close");
    String separator = element.attributeValue("separator");

    SqlNode contents = buildSqlNode(element);

    return new ForEachSqlNode(configuration, contents, collection, 
        index, item, open, close, separator);
  }

  /**
   * Parses {@code <where>} tag.
   */
  private SqlNode parseWhereTag(Element element) {
    SqlNode contents = buildSqlNode(element);
    return new WhereSqlNode(configuration, contents);
  }

  /**
   * Parses {@code <set>} tag.
   */
  private SqlNode parseSetTag(Element element) {
    SqlNode contents = buildSqlNode(element);
    return new SetSqlNode(configuration, contents);
  }

  /**
   * Parses {@code <trim>} tag.
   */
  private SqlNode parseTrimTag(Element element) {
    String prefix = element.attributeValue("prefix");
    String prefixOverrides = element.attributeValue("prefixOverrides");
    String suffix = element.attributeValue("suffix");
    String suffixOverrides = element.attributeValue("suffixOverrides");

    SqlNode contents = buildSqlNode(element);

    return new TrimSqlNode(configuration, contents, prefix, 
        prefixOverrides, suffix, suffixOverrides);
  }

  /**
   * Generates SQL from SqlNode tree.
   *
   * <p>Uses empty parameter context, assuming all {@code <if>} conditions are true.</p>
   *
   * @param sqlNode the root SqlNode
   * @return the generated SQL
   */
  public String generateSql(SqlNode sqlNode) {
    try {
      // Create DynamicContext with empty parameters
      // This will cause all <if> conditions to be evaluated
      // We use a permissive context that assumes conditions are true
      DynamicContext context = new DynamicContext(configuration, createPermissiveParameters());

      // Apply SqlNode tree to generate SQL
      sqlNode.apply(context);

      // Get the generated SQL
      String sql = context.getSql();

      // Clean up extra whitespace
      return sql.replaceAll("\\s+", " ").trim();

    } catch (Exception e) {
      logger.warn("Failed to generate SQL from SqlNode: {}", e.getMessage());
      return "";
    }
  }

  /**
   * Creates a permissive parameter object that makes most conditions evaluate to true.
   *
   * <p>This is a simple implementation that returns non-null values for any property access.</p>
   */
  private Object createPermissiveParameters() {
    // Return a map that pretends all properties exist and are non-null
    return new HashMap<String, Object>() {
      @Override
      public Object get(Object key) {
        // Return a non-null value for any key
        // This makes conditions like "id != null" evaluate to true
        Object value = super.get(key);
        if (value == null) {
          // Return a placeholder value
          return "PLACEHOLDER";
        }
        return value;
      }

      @Override
      public boolean containsKey(Object key) {
        // Pretend all keys exist
        return true;
      }
    };
  }

  /**
   * Generates multiple SQL variants by trying different parameter combinations.
   *
   * <p>This method generates SQL with different assumptions about which
   * {@code <if>} conditions are true or false.</p>
   *
   * @param sqlNode the root SqlNode
   * @return list of SQL variants
   */
  public List<String> generateSqlVariants(SqlNode sqlNode) {
    Set<String> variants = new LinkedHashSet<>();

    try {
      // Variant 1: All conditions true (permissive parameters)
      String sql1 = generateSql(sqlNode);
      if (!sql1.isEmpty()) {
        variants.add(sql1);
      }

      // Variant 2: All conditions false (empty parameters)
      DynamicContext context2 = new DynamicContext(configuration, new HashMap<>());
      sqlNode.apply(context2);
      String sql2 = context2.getSql().replaceAll("\\s+", " ").trim();
      if (!sql2.isEmpty()) {
        variants.add(sql2);
      }

    } catch (Exception e) {
      logger.warn("Failed to generate SQL variants: {}", e.getMessage());
    }

    return new ArrayList<>(variants);
  }

  /**
   * Checks if the SQL contains dynamic tags.
   *
   * @param element the XML element
   * @return true if contains dynamic tags
   */
  public static boolean containsDynamicTags(Element element) {
    if (element == null) {
      return false;
    }

    // Check if this element is a dynamic tag
    String name = element.getName();
    if (isDynamicTag(name)) {
      return true;
    }

    // Check child elements recursively
    for (Object obj : element.elements()) {
      if (obj instanceof Element) {
        if (containsDynamicTags((Element) obj)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Checks if the tag name is a MyBatis dynamic SQL tag.
   */
  private static boolean isDynamicTag(String tagName) {
    return "if".equals(tagName) ||
           "choose".equals(tagName) ||
           "when".equals(tagName) ||
           "otherwise".equals(tagName) ||
           "foreach".equals(tagName) ||
           "where".equals(tagName) ||
           "set".equals(tagName) ||
           "trim".equals(tagName) ||
           "bind".equals(tagName);
  }
}










