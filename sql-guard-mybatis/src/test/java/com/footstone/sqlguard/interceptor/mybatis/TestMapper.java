package com.footstone.sqlguard.interceptor.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.*;

/**
 * Test mapper interface for integration tests.
 *
 * <p>Contains both safe and dangerous SQL statements for testing:</p>
 * <ul>
 *   <li>Safe queries with WHERE clauses</li>
 *   <li>Dangerous queries without WHERE clauses</li>
 *   <li>Dynamic SQL with if/where tags</li>
 *   <li>Batch operations</li>
 * </ul>
 */
public interface TestMapper {

  /**
   * Safe query - has WHERE clause.
   */
  @Select("SELECT * FROM users WHERE id = #{id}")
  User findById(Long id);

  /**
   * Dangerous query - no WHERE clause (should violate).
   */
  @Select("SELECT * FROM users")
  List<User> findAll();

  /**
   * Safe query with multiple conditions.
   */
  @Select("SELECT * FROM users WHERE name = #{name} AND age > #{age}")
  List<User> findByNameAndAge(@Param("name") String name, @Param("age") Integer age);

  /**
   * Dangerous update - no WHERE clause (should violate).
   */
  @Update("UPDATE users SET status = #{status}")
  int updateAllStatus(String status);

  /**
   * Safe update - has WHERE clause.
   */
  @Update("UPDATE users SET name = #{name} WHERE id = #{id}")
  int updateNameById(@Param("id") Long id, @Param("name") String name);

  /**
   * Dangerous delete - no WHERE clause (should violate).
   */
  @Delete("DELETE FROM users")
  int deleteAll();

  /**
   * Safe delete - has WHERE clause.
   */
  @Delete("DELETE FROM users WHERE id = #{id}")
  int deleteById(Long id);

  /**
   * Safe insert.
   */
  @Insert("INSERT INTO users (name, email, age, status) VALUES (#{name}, #{email}, #{age}, #{status})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(User user);

  /**
   * Query with RowBounds for pagination.
   */
  @Select("SELECT * FROM users WHERE status = #{status}")
  List<User> findByStatus(String status);

  /**
   * Dynamic SQL with if tag (simulated via annotation).
   */
  @Select("<script>" +
      "SELECT * FROM users WHERE 1=1" +
      "<if test='name != null'> AND name = #{name}</if>" +
      "<if test='age != null'> AND age > #{age}</if>" +
      "</script>")
  List<User> findByDynamicConditions(@Param("name") String name, @Param("age") Integer age);

  /**
   * Alias methods for integration tests.
   */
  @Select("SELECT * FROM users")
  List<User> selectAllUsers();

  @Update("UPDATE users SET name = #{name} WHERE id = #{id}")
  int updateUser(@Param("id") Long id, @Param("name") String name);

  @Select("SELECT * FROM users WHERE name = #{name}")
  List<User> selectUsersByName(@Param("name") String name);

  @Insert("INSERT INTO users (id, name, email) VALUES (#{id}, #{name}, #{email})")
  int insertUser(@Param("id") Long id, @Param("name") String name, @Param("email") String email);

  @Insert("INSERT INTO users (id, name, email) VALUES (#{id}, #{name}, #{email})")
  int insertDuplicateUser(@Param("id") Long id, @Param("name") String name, @Param("email") String email);

  @Delete("DELETE FROM users WHERE id = #{id}")
  int deleteUser(@Param("id") Long id);

  /**
   * Simple POJO for test data.
   */
  class User {
    private Long id;
    private String name;
    private String email;
    private Integer age;
    private String status;

    public User() {
    }

    public User(String name, String email, Integer age, String status) {
      this.name = name;
      this.email = email;
      this.age = age;
      this.status = status;
    }

    // Getters and setters
    public Long getId() {
      return id;
    }

    public void setId(Long id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public Integer getAge() {
      return age;
    }

    public void setAge(Integer age) {
      this.age = age;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    @Override
    public String toString() {
      return "User{" +
          "id=" + id +
          ", name='" + name + '\'' +
          ", email='" + email + '\'' +
          ", age=" + age +
          ", status='" + status + '\'' +
          '}';
    }
  }
}








