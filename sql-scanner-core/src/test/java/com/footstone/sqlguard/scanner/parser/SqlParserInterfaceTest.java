package com.footstone.sqlguard.scanner.parser;

import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SqlParser interface using mock implementations.
 * Verifies contract behavior including error handling.
 */
@DisplayName("SqlParser Interface Tests")
class SqlParserInterfaceTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("Should parse file and return list of SqlEntry")
  void testParseReturnsListOfSqlEntry() throws IOException, ParseException {
    // Given
    SqlParser parser = new MockSuccessfulParser();
    File testFile = tempDir.resolve("test.xml").toFile();
    testFile.createNewFile();

    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    assertNotNull(entries);
    assertEquals(2, entries.size());
    assertEquals(SourceType.XML, entries.get(0).getSource());
  }

  @Test
  @DisplayName("Should throw IOException when file not found")
  void testParseThrowsIOExceptionForMissingFile() {
    // Given
    SqlParser parser = new MockSuccessfulParser();
    File nonExistentFile = new File("/nonexistent/path/file.xml");

    // When & Then
    IOException exception = assertThrows(IOException.class, () ->
        parser.parse(nonExistentFile));

    assertTrue(exception.getMessage().contains("File not found") ||
               exception.getMessage().contains("does not exist"));
  }

  @Test
  @DisplayName("Should throw ParseException for malformed XML")
  void testParseThrowsParseExceptionForMalformedXml() {
    // Given
    SqlParser parser = new MockMalformedXmlParser();
    File testFile = tempDir.resolve("malformed.xml").toFile();

    // When & Then
    assertThrows(ParseException.class, () ->
        parser.parse(testFile));
  }

  @Test
  @DisplayName("Should handle parse errors gracefully and return partial results")
  void testParseHandlesErrorsGracefullyWithPartialResults() throws IOException, ParseException {
    // Given
    SqlParser parser = new MockPartialResultsParser();
    File testFile = tempDir.resolve("partial.xml").toFile();
    testFile.createNewFile();

    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then - should return what it could parse
    assertNotNull(entries);
    assertEquals(1, entries.size());
  }

  @Test
  @DisplayName("Should return empty list for file with no SQL")
  void testParseReturnsEmptyListForNoSql() throws IOException, ParseException {
    // Given
    SqlParser parser = new MockEmptyParser();
    File testFile = tempDir.resolve("empty.xml").toFile();
    testFile.createNewFile();

    // When
    List<SqlEntry> entries = parser.parse(testFile);

    // Then
    assertNotNull(entries);
    assertTrue(entries.isEmpty());
  }

  // Mock Implementations

  /**
   * Mock parser that successfully parses and returns predefined entries.
   */
  private static class MockSuccessfulParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      if (!file.exists()) {
        throw new IOException("File not found: " + file.getAbsolutePath());
      }

      List<SqlEntry> entries = new ArrayList<>();
      entries.add(new SqlEntry(
          SourceType.XML,
          file.getAbsolutePath(),
          "com.example.UserMapper.selectById",
          SqlCommandType.SELECT,
          "SELECT * FROM user WHERE id = #{id}",
          10
      ));
      entries.add(new SqlEntry(
          SourceType.XML,
          file.getAbsolutePath(),
          "com.example.UserMapper.insert",
          SqlCommandType.INSERT,
          "INSERT INTO user VALUES (#{id}, #{name})",
          20
      ));
      return entries;
    }
  }

  /**
   * Mock parser that throws ParseException for malformed content.
   */
  private static class MockMalformedXmlParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      throw new ParseException("Malformed XML: unexpected token at line 5", 5);
    }
  }

  /**
   * Mock parser that returns partial results despite errors.
   */
  private static class MockPartialResultsParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      if (!file.exists()) {
        throw new IOException("File not found: " + file.getAbsolutePath());
      }

      // Simulate parsing that encounters errors but returns what it could parse
      List<SqlEntry> entries = new ArrayList<>();
      entries.add(new SqlEntry(
          SourceType.XML,
          file.getAbsolutePath(),
          "com.example.UserMapper.selectById",
          SqlCommandType.SELECT,
          "SELECT * FROM user WHERE id = #{id}",
          10
      ));
      // Pretend second entry failed to parse, but we return the first one
      return entries;
    }
  }

  /**
   * Mock parser that returns empty list (file with no SQL statements).
   */
  private static class MockEmptyParser implements SqlParser {
    @Override
    public List<SqlEntry> parse(File file) throws IOException, ParseException {
      if (!file.exists()) {
        throw new IOException("File not found: " + file.getAbsolutePath());
      }
      return new ArrayList<>();
    }
  }
}





