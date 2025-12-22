package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * User entity for integration testing.
 */
@TableName("mp_user")
public class User {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String name;

  private Integer age;

  private String email;

  private String status;

  @TableLogic
  private Integer deleted;

  // Constructors

  public User() {
  }

  public User(String name, Integer age, String email, String status) {
    this.name = name;
    this.age = age;
    this.email = email;
    this.status = status;
  }

  // Getters and Setters

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

  public Integer getAge() {
    return age;
  }

  public void setAge(Integer age) {
    this.age = age;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Integer getDeleted() {
    return deleted;
  }

  public void setDeleted(Integer deleted) {
    this.deleted = deleted;
  }

  @Override
  public String toString() {
    return "User{"
        + "id=" + id
        + ", name='" + name + '\''
        + ", age=" + age
        + ", email='" + email + '\''
        + ", status='" + status + '\''
        + ", deleted=" + deleted
        + '}';
  }
}







