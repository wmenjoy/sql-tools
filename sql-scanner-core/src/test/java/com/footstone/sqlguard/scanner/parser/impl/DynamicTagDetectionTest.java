package com.footstone.sqlguard.scanner.parser.impl;

import com.footstone.sqlguard.scanner.model.SqlEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XmlMapperParser dynamic tag detection functionality.
 * Tests detection of MyBatis dynamic SQL tags (if/where/foreach/choose/when/otherwise/set/trim/bind).
 */
@DisplayName("XmlMapperParser Dynamic Tag Detection Tests")
class DynamicTagDetectionTest {

  private XmlMapperParser parser;

  @BeforeEach
  void setUp() {
    parser = new XmlMapperParser();
  }

  @Test
  @DisplayName("Should set dynamic flag for SQL with <if> tag")
  void testIfTag_shouldSetDynamicFlag() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/if-condition.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <if> tag should be marked as dynamic");
  }

  @Test
  @DisplayName("Should set dynamic flag for SQL with <where> tag")
  void testWhereTag_shouldSetDynamicFlag() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/where-tag.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <where> tag should be marked as dynamic");
  }

  @Test
  @DisplayName("Should set dynamic flag for SQL with <foreach> tag")
  void testForeachTag_shouldSetDynamicFlag() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/foreach-loop.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <foreach> tag should be marked as dynamic");
  }

  @Test
  @DisplayName("Should set dynamic flag for SQL with <choose><when> tags")
  void testChooseWhenTag_shouldSetDynamicFlag() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/choose-when.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with <choose>/<when> tags should be marked as dynamic");
  }

  @Test
  @DisplayName("Should NOT set dynamic flag for static SQL")
  void testStaticSQL_shouldNotBeDynamic() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/simple-static.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    for (SqlEntry entry : entries) {
      assertFalse(entry.isDynamic(), 
          "Static SQL should not be marked as dynamic: " + entry.getMapperId());
    }
  }

  @Test
  @DisplayName("Should detect nested dynamic tags")
  void testNestedDynamicTags_shouldDetect() throws IOException, ParseException {
    // Given
    File xmlFile = getTestResourceFile("mappers/nested-dynamic.xml");

    // When
    List<SqlEntry> entries = parser.parse(xmlFile);

    // Then
    assertFalse(entries.isEmpty());
    SqlEntry entry = entries.get(0);
    assertTrue(entry.isDynamic(), "SQL with nested <if> tags should be marked as dynamic");
  }

  @Test
  @DisplayName("Should detect all MyBatis dynamic tags")
  void testAllDynamicTags_shouldDetect() throws IOException, ParseException {
    // Test that all dynamic tags are recognized
    String[] dynamicTags = {"if", "where", "foreach", "choose", "when", "otherwise", "set", "trim", "bind"};
    
    for (String tag : dynamicTags) {
      // Create temporary XML with the dynamic tag
      File tempFile = File.createTempFile("test-dynamic-", ".xml");
      tempFile.deleteOnExit();
      
      String xmlContent = String.format(
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
          "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
          "<mapper namespace=\"test\">\n" +
          "    <select id=\"test\" resultType=\"User\">\n" +
          "        SELECT * FROM user\n" +
          "        <%s test=\"condition\">content</%s>\n" +
          "    </select>\n" +
          "</mapper>",
          tag, tag
      );
      
      java.nio.file.Files.write(tempFile.toPath(), 
          xmlContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));

      // When
      List<SqlEntry> entries = parser.parse(tempFile);

      // Then
      assertFalse(entries.isEmpty(), "Should parse XML with <" + tag + "> tag");
      SqlEntry entry = entries.get(0);
      assertTrue(entry.isDynamic(), 
          "SQL with <" + tag + "> tag should be marked as dynamic");
    }
  }

  /**
   * Helper method to get test resource file.
   */
  private File getTestResourceFile(String resourcePath) {
    URL resource = getClass().getClassLoader().getResource(resourcePath);
    assertNotNull(resource, "Test resource not found: " + resourcePath);
    return new File(resource.getFile());
  }
}






