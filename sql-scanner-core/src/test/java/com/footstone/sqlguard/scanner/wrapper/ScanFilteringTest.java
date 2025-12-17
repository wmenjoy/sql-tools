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
 * Test class for scan filtering logic in QueryWrapperScanner.
 *
 * <p>Tests verify that scanner correctly filters files based on directory
 * structure, excluding test files, generated code, and build artifacts.</p>
 */
class ScanFilteringTest {

  private QueryWrapperScanner scanner;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new QueryWrapperScanner();
  }

  @Test
  void testSourceDirectoryFiltering_shouldIncludeMainOnly() throws IOException {
    // Given: Wrappers in both src/main/java and src/test/java
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("src/main/java/com/example/MainService.java", wrapperCode);
    createJavaFile("src/test/java/com/example/TestService.java", wrapperCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should only detect wrapper from src/main/java
    assertEquals(1, usages.size());
    assertTrue(usages.get(0).getFilePath().contains("src/main/java"));
    assertFalse(usages.get(0).getFilePath().contains("src/test/java"));
  }

  @Test
  void testTargetDirectoryFiltering_shouldExcludeBuild() throws IOException {
    // Given: Wrappers in src/main/java and target/classes
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("src/main/java/com/example/MainService.java", wrapperCode);
    createJavaFile("target/classes/com/example/CompiledService.java", wrapperCode);
    createJavaFile("build/classes/com/example/BuildService.java", wrapperCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should only detect wrapper from src/main/java
    assertEquals(1, usages.size());
    assertTrue(usages.get(0).getFilePath().contains("src/main/java"));
  }

  @Test
  void testGeneratedCodeFiltering_shouldExcludeGenerated() throws IOException {
    // Given: Wrappers in src/main/java and target/generated-sources
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("src/main/java/com/example/MainService.java", wrapperCode);
    createJavaFile("target/generated-sources/annotations/com/example/GeneratedService.java", wrapperCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should only detect wrapper from src/main/java
    assertEquals(1, usages.size());
    assertTrue(usages.get(0).getFilePath().contains("src/main/java"));
  }

  @Test
  void testNonJavaFiles_shouldSkip() throws IOException {
    // Given: Java files and non-Java files
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("src/main/java/com/example/Service.java", wrapperCode);
    createFile("src/main/resources/application.properties", "key=value");
    createFile("src/main/resources/mybatis-config.xml", "<configuration></configuration>");

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should only detect wrapper from .java file
    assertEquals(1, usages.size());
  }

  @Test
  void testGitDirectoryFiltering_shouldExcludeGit() throws IOException {
    // Given: Wrappers in src/main/java and .git directory
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("src/main/java/com/example/Service.java", wrapperCode);
    createJavaFile(".git/objects/com/example/GitService.java", wrapperCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should only detect wrapper from src/main/java
    assertEquals(1, usages.size());
    assertFalse(usages.get(0).getFilePath().contains(".git"));
  }

  @Test
  void testMultipleSourceRoots_shouldDetectAll() throws IOException {
    // Given: Wrappers in multiple src/main/java directories (multi-module project)
    String wrapperCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("module1/src/main/java/com/example/Service1.java", wrapperCode);
    createJavaFile("module2/src/main/java/com/example/Service2.java", wrapperCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect wrappers from both modules
    assertEquals(2, usages.size());
  }

  /**
   * Helper method to create a Java file at specified path.
   */
  private File createJavaFile(String relativePath, String content) throws IOException {
    Path filePath = tempDir.resolve(relativePath);
    Files.createDirectories(filePath.getParent());
    // Java 8 compatible file writing
    Files.write(filePath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return filePath.toFile();
  }

  /**
   * Helper method to create a non-Java file at specified path.
   */
  private File createFile(String relativePath, String content) throws IOException {
    Path filePath = tempDir.resolve(relativePath);
    Files.createDirectories(filePath.getParent());
    // Java 8 compatible file writing
    Files.write(filePath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return filePath.toFile();
  }
}

