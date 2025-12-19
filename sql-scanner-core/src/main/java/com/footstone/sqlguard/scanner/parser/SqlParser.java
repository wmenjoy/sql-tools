package com.footstone.sqlguard.scanner.parser;

import com.footstone.sqlguard.scanner.model.SqlEntry;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;

/**
 * Interface for parsing SQL statements from various source files.
 *
 * <p>SqlParser defines a uniform contract for extracting SQL entries from different
 * source types (XML mappers, Java annotations, etc.). Implementations must handle
 * file parsing errors gracefully and provide clear error messages.</p>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Throw IOException for file system errors (file not found, read errors)</li>
 *   <li>Throw ParseException for malformed content (invalid XML, syntax errors)</li>
 *   <li>Return partial results when possible, logging errors for individual entries</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> Implementations should be thread-safe to support
 * concurrent parsing of multiple files.</p>
 *
 * @see SqlEntry
 * @see com.footstone.sqlguard.scanner.model.SourceType
 */
public interface SqlParser {

  /**
   * Parses a source file and extracts SQL entries.
   *
   * <p>This method analyzes the given file and returns all SQL statements found,
   * along with their metadata (source location, type, etc.).</p>
   *
   * @param file the source file to parse (must exist and be readable)
   * @return list of SQL entries found in the file (never null, may be empty)
   * @throws IOException if file cannot be read or does not exist
   * @throws ParseException if file content is malformed and cannot be parsed
   */
  List<SqlEntry> parse(File file) throws IOException, ParseException;
}












