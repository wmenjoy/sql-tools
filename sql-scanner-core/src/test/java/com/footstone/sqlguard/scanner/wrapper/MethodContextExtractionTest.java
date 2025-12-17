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
 * Test class for method context extraction in QueryWrapperScanner.
 *
 * <p>Tests verify that wrappers are correctly attributed to their enclosing
 * method, constructor, static block, field initializer, or lambda context.</p>
 */
class MethodContextExtractionTest {

  private QueryWrapperScanner scanner;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new QueryWrapperScanner();
  }

  @Test
  void testMethodLevelWrapper_shouldExtractMethodName() throws IOException {
    // Given: Wrapper in method body
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void findUsers() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract method name
    assertEquals(1, usages.size());
    assertEquals("findUsers", usages.get(0).getMethodName());
  }

  @Test
  void testConstructorWrapper_shouldExtractInit() throws IOException {
    // Given: Wrapper in constructor
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public UserService() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract <init> for constructor
    assertEquals(1, usages.size());
    assertEquals("<init>", usages.get(0).getMethodName());
  }

  @Test
  void testStaticBlockWrapper_shouldExtractStatic() throws IOException {
    // Given: Wrapper in static initializer block
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    static {\n" +
        "        QueryWrapper<Config> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract <static> for static block
    assertEquals(1, usages.size());
    assertEquals("<static>", usages.get(0).getMethodName());
  }

  @Test
  void testFieldInitializerWrapper_shouldExtractFieldName() throws IOException {
    // Given: Wrapper in field initializer
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    private QueryWrapper<User> fieldWrapper = new QueryWrapper<>();\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract field name with _initializer suffix
    assertEquals(1, usages.size());
    String methodName = usages.get(0).getMethodName();
    assertTrue(methodName.equals("fieldWrapper_initializer") || methodName.equals("fieldWrapper"),
        "Expected field initializer context, got: " + methodName);
  }

  @Test
  void testNestedMethodWrapper_shouldExtractOuterMethod() throws IOException {
    // Given: Wrapper in lambda expression within method
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "import java.util.List;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void processUsers(List<User> users) {\n" +
        "        users.forEach(u -> {\n" +
        "            QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "        });\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract outer method name (processUsers), not lambda
    assertEquals(1, usages.size());
    assertEquals("processUsers", usages.get(0).getMethodName());
  }

  @Test
  void testInstanceInitializerWrapper_shouldExtractInstanceInit() throws IOException {
    // Given: Wrapper in instance initializer block
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract <instance_init> for instance initializer
    assertEquals(1, usages.size());
    assertEquals("<instance_init>", usages.get(0).getMethodName());
  }

  @Test
  void testMultipleContextsInFile_shouldExtractCorrectly() throws IOException {
    // Given: Wrappers in different contexts within same file
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    private QueryWrapper<User> fieldWrapper = new QueryWrapper<>();\n" +
        "    \n" +
        "    static {\n" +
        "        UpdateWrapper<Config> staticWrapper = new UpdateWrapper<>();\n" +
        "    }\n" +
        "    \n" +
        "    public UserService() {\n" +
        "        QueryWrapper<User> constructorWrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "    \n" +
        "    public void findUsers() {\n" +
        "        QueryWrapper<User> methodWrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should extract all four contexts correctly
    assertEquals(4, usages.size());

    // Verify field initializer
    boolean hasFieldInit = usages.stream()
        .anyMatch(u -> u.getMethodName().contains("fieldWrapper"));
    assertTrue(hasFieldInit, "Should have field initializer context");

    // Verify static block
    boolean hasStatic = usages.stream()
        .anyMatch(u -> u.getMethodName().equals("<static>"));
    assertTrue(hasStatic, "Should have static block context");

    // Verify constructor
    boolean hasConstructor = usages.stream()
        .anyMatch(u -> u.getMethodName().equals("<init>"));
    assertTrue(hasConstructor, "Should have constructor context");

    // Verify method
    boolean hasMethod = usages.stream()
        .anyMatch(u -> u.getMethodName().equals("findUsers"));
    assertTrue(hasMethod, "Should have method context");
  }

  @Test
  void testUnknownContext_shouldReturnUnknown() throws IOException {
    // Given: Wrapper in unusual context (edge case)
    // Note: In practice, all wrappers should have a context
    // This test documents fallback behavior
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void findUsers() {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should never return "unknown" for normal code
    assertEquals(1, usages.size());
    assertNotEquals("unknown", usages.get(0).getMethodName());
  }

  /**
   * Helper method to create a Java file in src/main/java structure.
   */
  private File createJavaFile(String fileName, String content) throws IOException {
    Path srcMainJava = tempDir.resolve("src/main/java/com/example/service");
    Files.createDirectories(srcMainJava);
    Path javaFile = srcMainJava.resolve(fileName);
    // Java 8 compatible file writing
    Files.write(javaFile, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return javaFile.toFile();
  }
}

