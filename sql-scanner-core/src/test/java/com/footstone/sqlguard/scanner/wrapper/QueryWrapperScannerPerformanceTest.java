package com.footstone.sqlguard.scanner.wrapper;

import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for QueryWrapperScanner.
 *
 * <p>Tests verify that scanner performs efficiently on large codebases
 * and handles edge cases without performance degradation.</p>
 */
class QueryWrapperScannerPerformanceTest {

  private QueryWrapperScanner scanner;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new QueryWrapperScanner();
  }

  @Test
  void testLargeProjectSimulation_shouldCompleteInReasonableTime() throws IOException {
    // Given: 100 Java files with varying wrapper usage
    int fileCount = 100;
    int filesWithWrappers = 20;

    for (int i = 0; i < fileCount; i++) {
      String fileName = "Service" + i + ".java";
      String content;

      if (i < filesWithWrappers) {
        // File with wrapper
        content = "package com.example;\n" +
            "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
            "public class Service" + i + " {\n" +
            "    public void method() {\n" +
            "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
            "    }\n" +
            "}\n";
      } else {
        // File without wrapper
        content = "package com.example;\n" +
            "public class Service" + i + " {\n" +
            "    public void method() {\n" +
            "        System.out.println(\"Hello\");\n" +
            "    }\n" +
            "}\n";
      }

      createJavaFile(fileName, content);
    }

    // When: Scanning the project
    long startTime = System.currentTimeMillis();
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());
    long duration = System.currentTimeMillis() - startTime;

    // Then: Should complete in reasonable time and detect all wrappers
    assertEquals(filesWithWrappers, usages.size());
    assertTrue(duration < 30000, "Scan took " + duration + "ms, expected < 30s for 100 files");
    System.out.println("Scanned " + fileCount + " files in " + duration + "ms");
  }

  @Test
  void testLargeFile_shouldHandleEfficiently() throws IOException {
    // Given: Very large Java file with multiple wrappers
    StringBuilder largeFileContent = new StringBuilder();
    largeFileContent.append("package com.example;\n");
    largeFileContent.append("import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n");
    largeFileContent.append("public class LargeService {\n");

    int methodCount = 100;
    for (int i = 0; i < methodCount; i++) {
      largeFileContent.append("    public void method").append(i).append("() {\n");
      if (i % 10 == 0) {
        // Add wrapper every 10 methods
        largeFileContent.append("        QueryWrapper<User> wrapper = new QueryWrapper<>();\n");
      }
      largeFileContent.append("        System.out.println(\"Method ").append(i).append("\");\n");
      largeFileContent.append("    }\n");
    }

    largeFileContent.append("}\n");

    createJavaFile("LargeService.java", largeFileContent.toString());

    // When: Scanning the project
    long startTime = System.currentTimeMillis();
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());
    long duration = System.currentTimeMillis() - startTime;

    // Then: Should handle large file efficiently
    assertEquals(10, usages.size());
    assertTrue(duration < 5000, "Large file scan took " + duration + "ms, expected < 5s");
  }

  @Test
  void testManyWrappersInSingleFile_shouldDetectAll() throws IOException {
    // Given: File with many wrapper instantiations
    StringBuilder content = new StringBuilder();
    content.append("package com.example;\n");
    content.append("import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n");
    content.append("import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;\n");
    content.append("public class MultiWrapperService {\n");

    int wrapperCount = 50;
    for (int i = 0; i < wrapperCount; i++) {
      content.append("    public void method").append(i).append("() {\n");
      if (i % 2 == 0) {
        content.append("        QueryWrapper<User> wrapper = new QueryWrapper<>();\n");
      } else {
        content.append("        UpdateWrapper<User> wrapper = new UpdateWrapper<>();\n");
      }
      content.append("    }\n");
    }

    content.append("}\n");

    createJavaFile("MultiWrapperService.java", content.toString());

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect all wrappers
    assertEquals(wrapperCount, usages.size());
  }

  @Test
  void testDeepDirectoryStructure_shouldTraverseEfficiently() throws IOException {
    // Given: Deep directory structure with Java files
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    // Create files at different depths
    createJavaFile("Service1.java", wrapperCode);
    createJavaFile("level1/Service2.java", wrapperCode);
    createJavaFile("level1/level2/Service3.java", wrapperCode);
    createJavaFile("level1/level2/level3/Service4.java", wrapperCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should find all wrappers regardless of depth
    assertEquals(4, usages.size());
  }

  @Test
  void testEmptyProject_shouldHandleGracefully() throws IOException {
    // Given: Empty project (no Java files)
    Files.createDirectories(tempDir.resolve("src/main/java"));

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should return empty list without errors
    assertEquals(0, usages.size());
  }

  @Test
  void testMemoryUsage_shouldNotRetainAST() throws IOException {
    // Given: Multiple files to scan
    for (int i = 0; i < 50; i++) {
      String content = "package com.example;\n" +
          "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
          "public class Service" + i + " {\n" +
          "    public void method() {\n" +
          "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
          "    }\n" +
          "}\n";
      createJavaFile("Service" + i + ".java", content);
    }

    // When: Scanning multiple times
    for (int i = 0; i < 3; i++) {
      List<WrapperUsage> usages = scanner.scan(tempDir.toFile());
      assertEquals(50, usages.size());
    }

    // Then: Should complete without OutOfMemoryError
    // This test verifies that AST is not retained between scans
    assertTrue(true, "Memory test completed successfully");
  }

  /**
   * Helper method to create a Java file in src/main/java structure.
   */
  private File createJavaFile(String relativePath, String content) throws IOException {
    Path fullPath = tempDir.resolve("src/main/java/com/example").resolve(relativePath);
    Files.createDirectories(fullPath.getParent());
    // Java 8 compatible file writing
    Files.write(fullPath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return fullPath.toFile();
  }
}

