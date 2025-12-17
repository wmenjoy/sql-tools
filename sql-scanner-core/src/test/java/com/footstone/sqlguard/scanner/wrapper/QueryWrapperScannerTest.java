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
 * Test class for QueryWrapperScanner basic wrapper detection.
 *
 * <p>Tests cover detection of all four MyBatis-Plus wrapper types:
 * QueryWrapper, LambdaQueryWrapper, UpdateWrapper, LambdaUpdateWrapper.</p>
 */
class QueryWrapperScannerTest {

  private QueryWrapperScanner scanner;

  @TempDir
  Path tempDir;

  @BeforeEach
  void setUp() {
    scanner = new QueryWrapperScanner();
  }

  @Test
  void testQueryWrapperDetection_shouldCreateUsage() throws IOException {
    // Given: Java file with QueryWrapper instantiation
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "import java.util.List;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public List<User> findByName(String name) {\n" +
        "        QueryWrapper<User> wrapper = new QueryWrapper<>();\n" +
        "        wrapper.eq(\"name\", name);\n" +
        "        return userMapper.selectList(wrapper);\n" +
        "    }\n" +
        "}\n";

    File javaFile = createJavaFile("UserService.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect one QueryWrapper usage
    assertEquals(1, usages.size());
    WrapperUsage usage = usages.get(0);
    assertEquals(javaFile.getAbsolutePath(), usage.getFilePath());
    assertEquals("findByName", usage.getMethodName());
    assertEquals(8, usage.getLineNumber());
    assertEquals("QueryWrapper", usage.getWrapperType());
    assertTrue(usage.isNeedsRuntimeCheck());
  }

  @Test
  void testLambdaQueryWrapperDetection_shouldCreateUsage() throws IOException {
    // Given: Java file with LambdaQueryWrapper instantiation
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public User findById(Long id) {\n" +
        "        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();\n" +
        "        wrapper.eq(User::getId, id);\n" +
        "        return userMapper.selectOne(wrapper);\n" +
        "    }\n" +
        "}\n";

    File javaFile = createJavaFile("UserService2.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect one LambdaQueryWrapper usage
    assertEquals(1, usages.size());
    WrapperUsage usage = usages.get(0);
    assertEquals(javaFile.getAbsolutePath(), usage.getFilePath());
    assertEquals("findById", usage.getMethodName());
    assertEquals(7, usage.getLineNumber());
    assertEquals("LambdaQueryWrapper", usage.getWrapperType());
    assertTrue(usage.isNeedsRuntimeCheck());
  }

  @Test
  void testUpdateWrapperDetection_shouldCreateUsage() throws IOException {
    // Given: Java file with UpdateWrapper instantiation
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void updateStatus(Long id, String status) {\n" +
        "        UpdateWrapper<User> wrapper = new UpdateWrapper<>();\n" +
        "        wrapper.eq(\"id\", id).set(\"status\", status);\n" +
        "        userMapper.update(null, wrapper);\n" +
        "    }\n" +
        "}\n";

    File javaFile = createJavaFile("UserService3.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect one UpdateWrapper usage
    assertEquals(1, usages.size());
    WrapperUsage usage = usages.get(0);
    assertEquals(javaFile.getAbsolutePath(), usage.getFilePath());
    assertEquals("updateStatus", usage.getMethodName());
    assertEquals(7, usage.getLineNumber());
    assertEquals("UpdateWrapper", usage.getWrapperType());
    assertTrue(usage.isNeedsRuntimeCheck());
  }

  @Test
  void testLambdaUpdateWrapperDetection_shouldCreateUsage() throws IOException {
    // Given: Java file with LambdaUpdateWrapper instantiation
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void updateEmail(Long id, String email) {\n" +
        "        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();\n" +
        "        wrapper.eq(User::getId, id).set(User::getEmail, email);\n" +
        "        userMapper.update(null, wrapper);\n" +
        "    }\n" +
        "}\n";

    File javaFile = createJavaFile("UserService4.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect one LambdaUpdateWrapper usage
    assertEquals(1, usages.size());
    WrapperUsage usage = usages.get(0);
    assertEquals(javaFile.getAbsolutePath(), usage.getFilePath());
    assertEquals("updateEmail", usage.getMethodName());
    assertEquals(7, usage.getLineNumber());
    assertEquals("LambdaUpdateWrapper", usage.getWrapperType());
    assertTrue(usage.isNeedsRuntimeCheck());
  }

  @Test
  void testNonWrapperObjectCreation_shouldSkip() throws IOException {
    // Given: Java file with non-wrapper object creation
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import java.util.ArrayList;\n" +
        "import java.util.HashMap;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void processUsers() {\n" +
        "        ArrayList<String> list = new ArrayList<>();\n" +
        "        HashMap<String, Object> map = new HashMap<>();\n" +
        "        User user = new User();\n" +
        "    }\n" +
        "}\n";

    createJavaFile("UserService5.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should not detect any wrapper usage
    assertEquals(0, usages.size());
  }

  @Test
  void testMultipleWrappersInFile_shouldDetectAll() throws IOException {
    // Given: Java file with multiple wrapper instantiations
    String javaCode = "package com.example.service;\n" +
        "\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;\n" +
        "import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;\n" +
        "import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;\n" +
        "\n" +
        "public class UserService {\n" +
        "    public void method1() {\n" +
        "        QueryWrapper<User> wrapper1 = new QueryWrapper<>();\n" +
        "    }\n" +
        "    \n" +
        "    public void method2() {\n" +
        "        LambdaQueryWrapper<User> wrapper2 = new LambdaQueryWrapper<>();\n" +
        "    }\n" +
        "    \n" +
        "    public void method3() {\n" +
        "        UpdateWrapper<User> wrapper3 = new UpdateWrapper<>();\n" +
        "    }\n" +
        "}\n";

    File javaFile = createJavaFile("UserService6.java", javaCode);

    // When: Scanning the project
    List<WrapperUsage> usages = scanner.scan(tempDir.toFile());

    // Then: Should detect all three wrapper usages
    assertEquals(3, usages.size());

    // Verify first wrapper
    WrapperUsage usage1 = usages.stream()
        .filter(u -> u.getMethodName().equals("method1"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("method1 wrapper not found"));
    assertEquals(javaFile.getAbsolutePath(), usage1.getFilePath());
    assertEquals(9, usage1.getLineNumber());
    assertEquals("QueryWrapper", usage1.getWrapperType());

    // Verify second wrapper
    WrapperUsage usage2 = usages.stream()
        .filter(u -> u.getMethodName().equals("method2"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("method2 wrapper not found"));
    assertEquals(javaFile.getAbsolutePath(), usage2.getFilePath());
    assertEquals(13, usage2.getLineNumber());
    assertEquals("LambdaQueryWrapper", usage2.getWrapperType());

    // Verify third wrapper
    WrapperUsage usage3 = usages.stream()
        .filter(u -> u.getMethodName().equals("method3"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("method3 wrapper not found"));
    assertEquals(javaFile.getAbsolutePath(), usage3.getFilePath());
    assertEquals(17, usage3.getLineNumber());
    assertEquals("UpdateWrapper", usage3.getWrapperType());
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

