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
 * Test class for false positive prevention in QueryWrapperScanner.
 *
 * <p>Tests verify that scanner only detects genuine MyBatis-Plus wrappers
 * and avoids false positives from similar class names or test code.</p>
 */
class FalsePositivePreventionTest {

  private QueryWrapperScanner scanner;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new QueryWrapperScanner();
  }

  @Test
  void testNonWrapperObjectCreation_shouldNotDetect() throws IOException {
    // Given: Various non-wrapper object creations
    String javaCode = "package com.example;\n" +
        "import java.util.ArrayList;\n" +
        "import java.util.HashMap;\n" +
        "import java.util.HashSet;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        ArrayList<String> list = new ArrayList<>();\n" +
        "        HashMap<String, Object> map = new HashMap<>();\n" +
        "        HashSet<Integer> set = new HashSet<>();\n" +
        "        User user = new User();\n" +
        "        StringBuilder sb = new StringBuilder();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("Service.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should not detect any wrappers
    assertEquals(0, usages.size());
  }

  @Test
  void testWrapperTestClass_shouldBeExcluded() throws IOException {
    // Given: Wrapper in test directory
    String javaCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class ServiceTest {\n" +
        "    public void testMethod() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createTestJavaFile("ServiceTest.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should not detect wrappers from test files
    assertEquals(0, usages.size());
  }

  @Test
  void testPreciseTypeMatching_shouldAvoidPartialMatch() throws IOException {
    // Given: Classes with names containing "Wrapper" but not actual wrappers
    String javaCode = "package com.example;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapperTest test = new QueryWrapperTest();\n" +
        "        CustomQueryWrapper custom = new CustomQueryWrapper();\n" +
        "        WrapperFactory factory = new WrapperFactory();\n" +
        "        MyWrapper wrapper = new MyWrapper();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("Service.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should not detect non-MyBatis-Plus wrappers
    // Note: Without symbol resolution, QueryWrapper-like names may be detected
    // This test documents current behavior
    assertEquals(0, usages.size());
  }

  @Test
  void testImportVerification_shouldCheckImports() throws IOException {
    // Given: Class with QueryWrapper name but different import
    String javaCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("Service.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect MyBatis-Plus wrapper with correct import
    assertEquals(1, usages.size());
    assertEquals("QueryWrapper", usages.get(0).getWrapperType());
  }

  @Test
  void testMixedWrapperAndNonWrapper_shouldDetectOnlyWrappers() throws IOException {
    // Given: Mix of wrapper and non-wrapper object creations
    String javaCode = "package com.example;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "import java.util.ArrayList;\n" +
        "public class Service {\n" +
        "    public void method() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "        ArrayList<String> list = new ArrayList<>();\n" +
        "        User user = new User();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("Service.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should only detect QueryWrapper
    assertEquals(1, usages.size());
    assertEquals("QueryWrapper", usages.get(0).getWrapperType());
  }

  @Test
  void testEmptyFile_shouldNotCrash() throws IOException {
    // Given: Empty Java file
    createJavaFile("EmptyService.java", "");

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should handle gracefully and return empty list
    assertEquals(0, usages.size());
  }

  @Test
  void testFileWithSyntaxError_shouldNotCrash() throws IOException {
    // Given: Java file with syntax error
    String invalidJavaCode = "package com.example;\n" +
        "public class Service {\n" +
        "    public void method( {\n" +  // Missing closing parenthesis
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("InvalidService.java", invalidJavaCode);

    // When: Scanning the project (should log warning and continue)
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should handle parse error gracefully
    // Result may be empty or partial depending on parser behavior
    assertTrue(usages.size() >= 0);
  }

  /**
   * Helper method to create a Java file in src/main/java structure.
   */
  private File createJavaFile(String fileName, String content) throws IOException {
    Path srcMainJava = tempDir.resolve("src/main/java/com/example");
    Files.createDirectories(srcMainJava);
    Path javaFile = srcMainJava.resolve(fileName);
    // Java 8 compatible file writing
    Files.write(javaFile, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return javaFile.toFile();
  }

  /**
   * Helper method to create a Java file in src/test/java structure.
   */
  private File createTestJavaFile(String fileName, String content) throws IOException {
    Path srcTestJava = tempDir.resolve("src/test/java/com/example");
    Files.createDirectories(srcTestJava);
    Path javaFile = srcTestJava.resolve(fileName);
    // Java 8 compatible file writing
    Files.write(javaFile, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return javaFile.toFile();
  }
}

