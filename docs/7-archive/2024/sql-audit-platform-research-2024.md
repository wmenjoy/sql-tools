我需要一个开源的sql执行审计平台,首先在数据库层,可以指导所有产品或者云服务执行的sql,通过sql来判断,是否有风险的无参数sql(可能触发全表查询),慢sql,有无参数绑定的sql(可能触发SQL注入),其次,在客户端层是否有mybatis层的插件或者连接池 druid层的插件,我可以将所有执行的sql输入到日志并采集走汇聚分析,最后我觉得才是代码级别的扫描,因为我觉得这个最难,一个是词法语法分析太多难,你扫描的多了业务没时间确认,报告形同虚设,扫描的少了又不起什么作用

10:55

6 steps
S


Sequential Thinking


S


Sequential Thinking


你的分层审计思路非常正确!先从数据库层和客户端层入手,最后才是代码扫描,这确实是最务实的做法。下面我给你推荐各层的开源方案:

一、数据库层审计平台
1. Yearning (推荐)
基于Go语言开发的MySQL SQL审核平台,提供查询审计、SQL审核、SQL回滚、自定义工作流等功能,包含45个SQL审核规则 
Yearning
Zhihu

特点:

轻量级,适合中小企业
支持工作流审批
有完善的权限管理
GitHub: https://github.com/cookieY/Yearning
2. Archery (功能更全面)
基于Python Flask开发的一站式SQL审核查询平台,支持MySQL、PostgreSQL、MariaDB、Redis等多种数据库,提供SQL审核、数据备份与恢复等全方位数据库管理方案 
CSDN

特点:

支持多种数据库
功能更全面(包括查询、慢日志分析等)
支持Docker部署
GitHub: https://github.com/hhyo/Archery
3. 小米SOAR
由小米开发的SQL优化和改写自动化工具,主要由语法解析器、集成环境、优化建议、重写逻辑、工具集五大模块组成 
Starcto

二、客户端层插件方案
1. Druid连接池 (阿里巴巴,推荐)
Druid内置StatFilter监控统计、Wall防火墙用于防御SQL注入、slf4j日志记录功能,可以配置慢SQL检测(slow-sql-millis)和SQL合并(merge-sql) 
Eolink
CSDN

配置示例:

yaml
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      filters: stat,wall,slf4j  # 监控统计、SQL防火墙、日志
      filter:
        stat:
          enabled: true
          log-slow-sql: true
          slow-sql-millis: 1000  # 慢SQL阈值
          merge-sql: true  # 合并相似SQL
        wall:
          enabled: true
          config:
            update-where-none-check: true  # 检测无where条件的update
            delete-where-none-check: true  # 检测无where条件的delete
      # 监控页面配置
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
Druid可以输出的SQL日志到:

控制台(开发环境)
文件日志(通过slf4j)
内置监控页面(/druid/*)
2. p6spy (强力推荐用于日志采集)
p6spy是通过代理JDBC驱动拦截和记录数据库访问操作的工具,可以记录完整SQL语句和执行时间,支持慢SQL检测和自定义日志格式 
Baeldung
MyBatis-Plus

为什么推荐p6spy:

可以输出完整的SQL(参数已替换)
支持自定义日志格式,方便采集
性能开销小
可以和MyBatis、Druid同时使用
配置示例:

yaml
spring:
  datasource:
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
    url: jdbc:p6spy:mysql://localhost:3306/db?...
spy.properties配置:

properties
# 模块配置
modulelist=com.p6spy.engine.spy.P6SpyFactory,com.p6spy.engine.logging.P6LogFactory

# 真实驱动
driverlist=com.mysql.cj.jdbc.Driver

# 日志输出到文件(便于采集)
appender=com.p6spy.engine.spy.appender.FileLogger
logfile=/var/log/sql/sql.log

# 自定义日志格式(便于解析)
logMessageFormat=com.p6spy.engine.spy.appender.CustomLineFormat
customLogMessageFormat=%(currentTime)|%(executionTime)|%(category)|%(sqlSingleLine)

# 慢SQL检测
outagedetection=true
outagedetectioninterval=2
3. MyBatis拦截器
如果你使用MyBatis,可以自己写一个拦截器:

java
@Intercepts({
    @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        String sql = boundSql.getSql();
        
        // 记录SQL到日志系统
        logSql(sql, parameter);
        
        return invocation.proceed();
    }
}
```

## 三、日志采集和汇聚分析架构

我建议你搭建这样的架构:
```
应用层(Druid/p6spy输出日志) 
    ↓
Filebeat/Fluentd采集
    ↓
Kafka消息队列(缓冲)
    ↓
Logstash/Flink Stream处理
    ↓
Elasticsearch存储 + Kibana可视化
    ↓
规则引擎分析:
  - 无where条件检测
  - 慢SQL检测  
  - SQL注入风险检测(关键字黑名单)
  - 参数绑定检测
    ↓
告警系统(钉钉/企业微信/邮件)
关键指标:

SQL执行时间
是否有where条件
是否使用参数绑定
表扫描行数
索引使用情况
四、关于代码扫描
你说得对,代码扫描确实问题最多:

问题:

误报率高 - 词法分析难度大,容易把正常SQL标记为风险
漏报率高 - 动态拼接SQL难以检测
业务确认成本高 - 扫出几百个问题,业务没时间看
建议:

只扫描关键规则 - 比如明显的SQL拼接字符串、没有使用PreparedStatement
结合白名单 - 已确认安全的代码加白名单
增量扫描 - 只扫描新增和修改的代码
可以用SonarQube - 配置最严格的SQL规则,但要调整阈值
工具推荐:

SonarQube + FindSecBugs插件
Semgrep(自定义规则比较灵活)
五、完整实施建议
第一阶段: 部署Druid或p6spy,收集基础SQL日志 第二阶段: 搭建ELK或类似的日志分析平台,实现实时监控 第三阶段: 在审计平台(Yearning/Archery)建立SQL审核流程 第四阶段: (可选)引入代码扫描作为补充

这样由外到内、循序渐进,效果最好且风险可控。你觉得这个方案怎么样?

我需要做sql的审计，帮我规范sql的日志json格式

11:04

1 step
S


Sequential Thinking


S


Sequential Thinking


好的,我给你设计一个完整的SQL审计日志JSON格式标准。这个格式涵盖了审计、安全、性能、追踪等各个维度:

一、标准JSON Schema定义
json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "SQL Audit Log",
  "type": "object",
  "required": ["timestamp", "sql_id", "sql_text", "sql_type", "execution_time_ms"],
  "properties": {
    "log_version": {
      "type": "string",
      "description": "日志格式版本",
      "example": "1.0.0"
    },
    "timestamp": {
      "type": "string",
      "format": "date-time",
      "description": "日志时间戳 ISO 8601格式"
    },
    "environment": {
      "type": "string",
      "enum": ["dev", "test", "staging", "prod"],
      "description": "运行环境"
    },
    "application": {
      "type": "object",
      "properties": {
        "name": {
          "type": "string",
          "description": "应用名称"
        },
        "version": {
          "type": "string",
          "description": "应用版本"
        },
        "instance_id": {
          "type": "string",
          "description": "实例ID"
        },
        "hostname": {
          "type": "string",
          "description": "主机名"
        },
        "ip": {
          "type": "string",
          "description": "应用服务器IP"
        }
      }
    },
    "trace": {
      "type": "object",
      "properties": {
        "trace_id": {
          "type": "string",
          "description": "分布式追踪ID"
        },
        "span_id": {
          "type": "string",
          "description": "Span ID"
        },
        "parent_span_id": {
          "type": "string",
          "description": "父Span ID"
        }
      }
    },
    "request": {
      "type": "object",
      "properties": {
        "api_endpoint": {
          "type": "string",
          "description": "API接口路径"
        },
        "http_method": {
          "type": "string",
          "description": "HTTP方法"
        },
        "client_ip": {
          "type": "string",
          "description": "客户端IP"
        },
        "user_agent": {
          "type": "string",
          "description": "User Agent"
        }
      }
    },
    "user": {
      "type": "object",
      "properties": {
        "user_id": {
          "type": "string",
          "description": "用户ID"
        },
        "username": {
          "type": "string",
          "description": "用户名"
        },
        "tenant_id": {
          "type": "string",
          "description": "租户ID(多租户场景)"
        }
      }
    },
    "database": {
      "type": "object",
      "properties": {
        "type": {
          "type": "string",
          "enum": ["mysql", "postgresql", "oracle", "sqlserver", "mongodb"],
          "description": "数据库类型"
        },
        "host": {
          "type": "string",
          "description": "数据库主机"
        },
        "port": {
          "type": "integer",
          "description": "数据库端口"
        },
        "database_name": {
          "type": "string",
          "description": "数据库名"
        },
        "schema_name": {
          "type": "string",
          "description": "Schema名称"
        },
        "connection_pool": {
          "type": "string",
          "description": "连接池名称"
        }
      }
    },
    "sql": {
      "type": "object",
      "properties": {
        "sql_id": {
          "type": "string",
          "description": "SQL唯一标识(MD5/SHA256)"
        },
        "sql_text": {
          "type": "string",
          "description": "原始SQL语句"
        },
        "sql_template": {
          "type": "string",
          "description": "参数化SQL模板(参数替换为?)"
        },
        "sql_type": {
          "type": "string",
          "enum": ["SELECT", "INSERT", "UPDATE", "DELETE", "DDL", "DCL", "TCL", "UNKNOWN"],
          "description": "SQL类型"
        },
        "parameters": {
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "index": {"type": "integer"},
              "type": {"type": "string"},
              "value": {"type": "string"}
            }
          },
          "description": "SQL参数"
        },
        "tables": {
          "type": "array",
          "items": {"type": "string"},
          "description": "涉及的表名"
        },
        "operation": {
          "type": "string",
          "description": "具体操作(如查询用户、更新订单等)"
        }
      }
    },
    "execution": {
      "type": "object",
      "properties": {
        "status": {
          "type": "string",
          "enum": ["success", "error", "timeout", "cancelled"],
          "description": "执行状态"
        },
        "execution_time_ms": {
          "type": "number",
          "description": "SQL执行时间(毫秒)"
        },
        "rows_affected": {
          "type": "integer",
          "description": "影响行数"
        },
        "rows_examined": {
          "type": "integer",
          "description": "扫描行数"
        },
        "rows_returned": {
          "type": "integer",
          "description": "返回行数"
        },
        "error_code": {
          "type": "string",
          "description": "错误代码"
        },
        "error_message": {
          "type": "string",
          "description": "错误信息"
        },
        "stack_trace": {
          "type": "string",
          "description": "堆栈跟踪"
        }
      }
    },
    "audit": {
      "type": "object",
      "properties": {
        "is_parameterized": {
          "type": "boolean",
          "description": "是否使用参数绑定"
        },
        "has_where_clause": {
          "type": "boolean",
          "description": "是否有WHERE条件(UPDATE/DELETE)"
        },
        "has_limit_clause": {
          "type": "boolean",
          "description": "是否有LIMIT限制"
        },
        "is_slow_sql": {
          "type": "boolean",
          "description": "是否慢SQL"
        },
        "slow_threshold_ms": {
          "type": "number",
          "description": "慢SQL阈值(毫秒)"
        },
        "risk_level": {
          "type": "string",
          "enum": ["low", "medium", "high", "critical"],
          "description": "风险等级"
        },
        "risk_reasons": {
          "type": "array",
          "items": {"type": "string"},
          "description": "风险原因列表"
        },
        "potential_injection": {
          "type": "boolean",
          "description": "是否有SQL注入风险"
        },
        "full_table_scan": {
          "type": "boolean",
          "description": "是否全表扫描"
        },
        "missing_index": {
          "type": "boolean",
          "description": "是否缺少索引"
        }
      }
    },
    "performance": {
      "type": "object",
      "properties": {
        "connection_time_ms": {
          "type": "number",
          "description": "获取连接时间"
        },
        "prepare_time_ms": {
          "type": "number",
          "description": "SQL准备时间"
        },
        "fetch_time_ms": {
          "type": "number",
          "description": "结果集获取时间"
        },
        "index_used": {
          "type": "array",
          "items": {"type": "string"},
          "description": "使用的索引"
        }
      }
    },
    "metadata": {
      "type": "object",
      "properties": {
        "source": {
          "type": "string",
          "enum": ["druid", "p6spy", "mybatis", "jdbc", "hibernate"],
          "description": "日志来源"
        },
        "framework": {
          "type": "string",
          "description": "使用的框架"
        },
        "method_name": {
          "type": "string",
          "description": "调用的方法名"
        },
        "class_name": {
          "type": "string",
          "description": "调用的类名"
        },
        "line_number": {
          "type": "integer",
          "description": "代码行号"
        }
      }
    }
  }
}
二、实际日志样例
样例1: 正常的参数化查询
json
{
  "log_version": "1.0.0",
  "timestamp": "2024-12-17T10:30:45.123Z",
  "environment": "prod",
  "application": {
    "name": "order-service",
    "version": "2.1.5",
    "instance_id": "order-service-pod-7d8f9c-x5k2m",
    "hostname": "k8s-node-01",
    "ip": "10.0.1.15"
  },
  "trace": {
    "trace_id": "a1b2c3d4e5f6g7h8i9j0",
    "span_id": "span-12345",
    "parent_span_id": "span-12344"
  },
  "request": {
    "api_endpoint": "/api/v1/orders/search",
    "http_method": "GET",
    "client_ip": "120.78.45.123",
    "user_agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
  },
  "user": {
    "user_id": "user_1001",
    "username": "zhang.san",
    "tenant_id": "tenant_001"
  },
  "database": {
    "type": "mysql",
    "host": "mysql-master.db.svc.cluster.local",
    "port": 3306,
    "database_name": "order_db",
    "schema_name": "public",
    "connection_pool": "druid-pool-1"
  },
  "sql": {
    "sql_id": "d41d8cd98f00b204e9800998ecf8427e",
    "sql_text": "SELECT order_id, user_id, order_status, created_at FROM orders WHERE user_id = 1001 AND order_status = 'PAID' ORDER BY created_at DESC LIMIT 20",
    "sql_template": "SELECT order_id, user_id, order_status, created_at FROM orders WHERE user_id = ? AND order_status = ? ORDER BY created_at DESC LIMIT ?",
    "sql_type": "SELECT",
    "parameters": [
      {"index": 1, "type": "Long", "value": "1001"},
      {"index": 2, "type": "String", "value": "PAID"},
      {"index": 3, "type": "Integer", "value": "20"}
    ],
    "tables": ["orders"],
    "operation": "查询用户已支付订单"
  },
  "execution": {
    "status": "success",
    "execution_time_ms": 45.8,
    "rows_affected": 0,
    "rows_examined": 120,
    "rows_returned": 15
  },
  "audit": {
    "is_parameterized": true,
    "has_where_clause": true,
    "has_limit_clause": true,
    "is_slow_sql": false,
    "slow_threshold_ms": 1000,
    "risk_level": "low",
    "risk_reasons": [],
    "potential_injection": false,
    "full_table_scan": false,
    "missing_index": false
  },
  "performance": {
    "connection_time_ms": 2.1,
    "prepare_time_ms": 1.5,
    "fetch_time_ms": 42.2,
    "index_used": ["idx_user_id_status"]
  },
  "metadata": {
    "source": "druid",
    "framework": "mybatis-plus",
    "method_name": "selectUserOrders",
    "class_name": "com.example.order.mapper.OrderMapper",
    "line_number": 45
  }
}
样例2: 高风险SQL - 无WHERE条件的DELETE
json
{
  "log_version": "1.0.0",
  "timestamp": "2024-12-17T10:35:12.456Z",
  "environment": "prod",
  "application": {
    "name": "user-service",
    "version": "1.8.2",
    "instance_id": "user-service-pod-3a4b5c-y7z8w",
    "hostname": "k8s-node-02",
    "ip": "10.0.1.16"
  },
  "trace": {
    "trace_id": "k9l8m7n6o5p4q3r2s1t0",
    "span_id": "span-67890"
  },
  "request": {
    "api_endpoint": "/api/v1/users/cleanup",
    "http_method": "POST",
    "client_ip": "192.168.1.100",
    "user_agent": "curl/7.64.1"
  },
  "user": {
    "user_id": "admin_001",
    "username": "admin",
    "tenant_id": "tenant_001"
  },
  "database": {
    "type": "mysql",
    "host": "mysql-master.db.svc.cluster.local",
    "port": 3306,
    "database_name": "user_db",
    "connection_pool": "druid-pool-2"
  },
  "sql": {
    "sql_id": "f47ac10b58cc4372a5670e02b2c3d479",
    "sql_text": "DELETE FROM user_sessions",
    "sql_template": "DELETE FROM user_sessions",
    "sql_type": "DELETE",
    "parameters": [],
    "tables": ["user_sessions"],
    "operation": "清理用户会话"
  },
  "execution": {
    "status": "success",
    "execution_time_ms": 3258.7,
    "rows_affected": 125000,
    "rows_examined": 125000,
    "rows_returned": 0
  },
  "audit": {
    "is_parameterized": false,
    "has_where_clause": false,
    "has_limit_clause": false,
    "is_slow_sql": true,
    "slow_threshold_ms": 1000,
    "risk_level": "critical",
    "risk_reasons": [
      "DELETE语句缺少WHERE条件",
      "影响行数超过10000行",
      "执行时间超过慢SQL阈值",
      "可能是全表删除操作"
    ],
    "potential_injection": false,
    "full_table_scan": true,
    "missing_index": false
  },
  "performance": {
    "connection_time_ms": 1.8,
    "prepare_time_ms": 0.9
  },
  "metadata": {
    "source": "p6spy",
    "framework": "jdbc",
    "method_name": "cleanupSessions",
    "class_name": "com.example.user.service.SessionService",
    "line_number": 128
  }
}
样例3: SQL注入风险 - 字符串拼接
json
{
  "log_version": "1.0.0",
  "timestamp": "2024-12-17T10:40:33.789Z",
  "environment": "prod",
  "application": {
    "name": "product-service",
    "version": "3.2.1",
    "instance_id": "product-service-pod-9x8y7z",
    "hostname": "k8s-node-03",
    "ip": "10.0.1.17"
  },
  "trace": {
    "trace_id": "u9v8w7x6y5z4a3b2c1d0",
    "span_id": "span-11111"
  },
  "request": {
    "api_endpoint": "/api/v1/products/search",
    "http_method": "GET",
    "client_ip": "203.45.67.89",
    "user_agent": "PostmanRuntime/7.29.2"
  },
  "user": {
    "user_id": "user_2002",
    "username": "li.si"
  },
  "database": {
    "type": "mysql",
    "host": "mysql-slave-01.db.svc.cluster.local",
    "port": 3306,
    "database_name": "product_db",
    "connection_pool": "druid-pool-3"
  },
  "sql": {
    "sql_id": "a8f5f167f44f4964e6c998dee827110c",
    "sql_text": "SELECT * FROM products WHERE category = 'electronics' OR 1=1 -- ' AND status = 1",
    "sql_template": "SELECT * FROM products WHERE category = 'electronics' OR 1=1 -- ' AND status = 1",
    "sql_type": "SELECT",
    "parameters": [],
    "tables": ["products"],
    "operation": "搜索产品"
  },
  "execution": {
    "status": "success",
    "execution_time_ms": 1523.4,
    "rows_examined": 500000,
    "rows_returned": 500000
  },
  "audit": {
    "is_parameterized": false,
    "has_where_clause": true,
    "has_limit_clause": false,
    "is_slow_sql": true,
    "slow_threshold_ms": 1000,
    "risk_level": "critical",
    "risk_reasons": [
      "SQL未使用参数绑定",
      "检测到SQL注入特征: OR 1=1",
      "检测到SQL注释符: --",
      "返回大量数据且无LIMIT限制",
      "疑似SQL注入攻击"
    ],
    "potential_injection": true,
    "full_table_scan": true,
    "missing_index": false
  },
  "performance": {
    "connection_time_ms": 2.3,
    "prepare_time_ms": 1.1,
    "fetch_time_ms": 1520.0
  },
  "metadata": {
    "source": "p6spy",
    "framework": "jdbc",
    "method_name": "searchProducts",
    "class_name": "com.example.product.dao.ProductDao",
    "line_number": 67
  }
}
三、Java代码实现示例
java
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * SQL审计日志构建器
 */
public class SqlAuditLogBuilder {
    private static final Logger logger = LoggerFactory.getLogger(SqlAuditLogBuilder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    
    private SqlAuditLog log = new SqlAuditLog();
    
    // SQL注入特征正则
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)(union.*select)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(or\\s+1\\s*=\\s*1)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(--)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(;.*drop)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(exec\\s*\\()", Pattern.CASE_INSENSITIVE)
    );
    
    public SqlAuditLogBuilder() {
        log.setLogVersion("1.0.0");
        log.setTimestamp(Instant.now().toString());
    }
    
    public SqlAuditLogBuilder environment(String env) {
        log.setEnvironment(env);
        return this;
    }
    
    public SqlAuditLogBuilder application(String name, String version, String instanceId, String hostname, String ip) {
        Application app = new Application();
        app.setName(name);
        app.setVersion(version);
        app.setInstanceId(instanceId);
        app.setHostname(hostname);
        app.setIp(ip);
        log.setApplication(app);
        return this;
    }
    
    public SqlAuditLogBuilder trace(String traceId, String spanId, String parentSpanId) {
        Trace trace = new Trace();
        trace.setTraceId(traceId);
        trace.setSpanId(spanId);
        trace.setParentSpanId(parentSpanId);
        log.setTrace(trace);
        return this;
    }
    
    public SqlAuditLogBuilder request(String apiEndpoint, String httpMethod, String clientIp, String userAgent) {
        Request request = new Request();
        request.setApiEndpoint(apiEndpoint);
        request.setHttpMethod(httpMethod);
        request.setClientIp(clientIp);
        request.setUserAgent(userAgent);
        log.setRequest(request);
        return this;
    }
    
    public SqlAuditLogBuilder user(String userId, String username, String tenantId) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setTenantId(tenantId);
        log.setUser(user);
        return this;
    }
    
    public SqlAuditLogBuilder database(String type, String host, Integer port, String databaseName, String connectionPool) {
        Database database = new Database();
        database.setType(type);
        database.setHost(host);
        database.setPort(port);
        database.setDatabaseName(databaseName);
        database.setConnectionPool(connectionPool);
        log.setDatabase(database);
        return this;
    }
    
    public SqlAuditLogBuilder sql(String sqlText, List<SqlParameter> parameters) {
        Sql sql = new Sql();
        sql.setSqlText(sqlText);
        sql.setSqlId(generateSqlId(sqlText));
        sql.setSqlTemplate(parameterizeSql(sqlText));
        sql.setSqlType(determineSqlType(sqlText));
        sql.setParameters(parameters);
        sql.setTables(extractTables(sqlText));
        log.setSql(sql);
        return this;
    }
    
    public SqlAuditLogBuilder execution(String status, Double executionTimeMs, Integer rowsAffected, 
                                       Integer rowsExamined, Integer rowsReturned, 
                                       String errorCode, String errorMessage) {
        Execution execution = new Execution();
        execution.setStatus(status);
        execution.setExecutionTimeMs(executionTimeMs);
        execution.setRowsAffected(rowsAffected);
        execution.setRowsExamined(rowsExamined);
        execution.setRowsReturned(rowsReturned);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);
        log.setExecution(execution);
        return this;
    }
    
    public SqlAuditLogBuilder metadata(String source, String framework, String methodName, 
                                      String className, Integer lineNumber) {
        Metadata metadata = new Metadata();
        metadata.setSource(source);
        metadata.setFramework(framework);
        metadata.setMethodName(methodName);
        metadata.setClassName(className);
        metadata.setLineNumber(lineNumber);
        log.setMetadata(metadata);
        return this;
    }
    
    /**
     * 自动审计SQL风险
     */
    public SqlAuditLogBuilder autoAudit(double slowThresholdMs) {
        Audit audit = new Audit();
        String sqlText = log.getSql().getSqlText().toUpperCase();
        String sqlType = log.getSql().getSqlType();
        List<String> riskReasons = new ArrayList<>();
        
        // 1. 检查参数绑定
        boolean isParameterized = log.getSql().getParameters() != null && 
                                 !log.getSql().getParameters().isEmpty();
        audit.setIsParameterized(isParameterized);
        if (!isParameterized) {
            riskReasons.add("SQL未使用参数绑定");
        }
        
        // 2. 检查WHERE条件 (UPDATE/DELETE)
        boolean hasWhereClause = sqlText.contains("WHERE");
        audit.setHasWhereClause(hasWhereClause);
        if (("UPDATE".equals(sqlType) || "DELETE".equals(sqlType)) && !hasWhereClause) {
            riskReasons.add(sqlType + "语句缺少WHERE条件");
        }
        
        // 3. 检查LIMIT
        boolean hasLimitClause = sqlText.contains("LIMIT");
        audit.setHasLimitClause(hasLimitClause);
        
        // 4. 检查慢SQL
        Double executionTime = log.getExecution().getExecutionTimeMs();
        boolean isSlowSql = executionTime != null && executionTime > slowThresholdMs;
        audit.setIsSlowSql(isSlowSql);
        audit.setSlowThresholdMs(slowThresholdMs);
        if (isSlowSql) {
            riskReasons.add("执行时间超过慢SQL阈值");
        }
        
        // 5. 检查SQL注入风险
        boolean potentialInjection = checkSqlInjection(log.getSql().getSqlText());
        audit.setPotentialInjection(potentialInjection);
        if (potentialInjection) {
            riskReasons.add("疑似SQL注入攻击");
        }
        
        // 6. 检查全表扫描
        Integer rowsExamined = log.getExecution().getRowsExamined();
        Integer rowsReturned = log.getExecution().getRowsReturned();
        boolean fullTableScan = rowsExamined != null && rowsExamined > 10000 && 
                               (rowsReturned == null || rowsExamined / rowsReturned > 10);
        audit.setFullTableScan(fullTableScan);
        if (fullTableScan) {
            riskReasons.add("可能存在全表扫描");
        }
        
        // 7. 检查大量数据影响
        Integer rowsAffected = log.getExecution().getRowsAffected();
        if (rowsAffected != null && rowsAffected > 10000) {
            riskReasons.add("影响行数超过10000行");
        }
        
        // 计算风险等级
        String riskLevel = calculateRiskLevel(riskReasons, potentialInjection, isSlowSql, hasWhereClause, sqlType);
        audit.setRiskLevel(riskLevel);
        audit.setRiskReasons(riskReasons.isEmpty() ? null : riskReasons);
        
        log.setAudit(audit);
        return this;
    }
    
    /**
     * 构建最终日志
     */
    public SqlAuditLog build() {
        return log;
    }
    
    /**
     * 输出JSON字符串
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(log);
        } catch (Exception e) {
            logger.error("Failed to serialize SQL audit log", e);
            return null;
        }
    }
    
    /**
     * 直接记录日志
     */
    public void log() {
        logger.info(toJson());
    }
    
    // ========== 私有工具方法 ==========
    
    private String generateSqlId(String sql) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sql.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
    
    private String parameterizeSql(String sql) {
        // 简化版: 将数字和字符串替换为?
        return sql.replaceAll("'[^']*'", "?")
                 .replaceAll("\\b\\d+\\b", "?");
    }
    
    private String determineSqlType(String sql) {
        String upperSql = sql.trim().toUpperCase();
        if (upperSql.startsWith("SELECT")) return "SELECT";
        if (upperSql.startsWith("INSERT")) return "INSERT";
        if (upperSql.startsWith("UPDATE")) return "UPDATE";
        if (upperSql.startsWith("DELETE")) return "DELETE";
        if (upperSql.startsWith("CREATE") || upperSql.startsWith("ALTER") || 
            upperSql.startsWith("DROP")) return "DDL";
        return "UNKNOWN";
    }
    
    private List<String> extractTables(String sql) {
        // 简化版: 提取FROM和JOIN后的表名
        List<String> tables = new ArrayList<>();
        String upperSql = sql.toUpperCase();
        Pattern pattern = Pattern.compile("(?:FROM|JOIN)\\s+([a-zA-Z0-9_]+)");
        java.util.regex.Matcher matcher = pattern.matcher(upperSql);
        while (matcher.find()) {
            tables.add(matcher.group(1).toLowerCase());
        }
        return tables.isEmpty() ? null : tables;
    }
    
    private boolean checkSqlI
这个有点太复了，我们只是，使用druid、mybatis把sql技术都打印出来，统一审计一下。

11:09
njection(String sql) {
for (Pattern pattern : INJECTION_PATTERNS) {
if (pattern.matcher(sql).find()) {
return true;
}
}
return false;
}

private String calculateRiskLevel(List<String> riskReasons, boolean potentialInjection, 
                                 boolean isSlowSql, boolean hasWhereClause, String sqlType) {
    if (potentialInjection) return "critical";
    if (("UPDATE".equals(sqlType) || "DELETE".equals(sqlType)) && !hasWhereClause) return "critical";
    if (riskReasons.size() >= 3) return "high";
    if (isSlowSql || riskReasons.size() >= 1) return "medium";
    return "low";
}
}

// ========== 数据模型类 ==========

@Data
class SqlAuditLog {
private String logVersion;
private String timestamp;
private String environment;
private Application application;
private Trace trace;
private Request request;
private User user;
private Database database;
private Sql sql;
private Execution execution;
private Audit audit;
private Performance performance;
private Metadata metadata;
}

@Data
class Application {
private String name;
private String version;
private String instanceId;
private String hostname;
private String ip;
}

@Data
class Trace {
private String traceId;
private String spanId;
private String parentSpanId;
}

@Data
class Request {
private String apiEndpoint;
private String httpMethod;
private String clientIp;
private String userAgent;
}

@Data
class User {
private String userId;
private String username;
private String tenantId;
}

@Data
class Database {
private String type;
private String host;
private Integer port;
private String databaseName;
private String schemaName;
private String connectionPool;
}

@Data class Sql { private String sqlId; private String sqlText; private String sqlTemplate; private String sqlType; private List<SqlParameter> parameters; private List<String> tables; private String operation; }

@Data
@Builder
class SqlParameter {
private Integer index;
private String type;
private String value;
}

@Data
class Execution {
private String status;
private Double executionTimeMs;
private Integer rowsAffected;
private Integer rowsExamined;
private Integer rowsReturned;
private String errorCode;
private String errorMessage;
private String stackTrace;
}

@Data class Audit { private Boolean isParameterized; private Boolean hasWhereClause; private Boolean hasLimitClause; private Boolean isSlowSql; private Double slowThresholdMs; private String riskLevel; private List<String> riskReasons; private Boolean potentialInjection; private Boolean fullTableScan; private Boolean missingIndex; }

@Data class Performance { private Double connectionTimeMs; private Double prepareTimeMs; private Double fetchTimeMs; private List<String> indexUsed; }

@Data
class Metadata {
private String source;
private String framework;
private String methodName;
private String className;
private Integer lineNumber;
}


## 四、使用示例
```java
// 示例1: 记录正常SQL
SqlAuditLogBuilder builder = new SqlAuditLogBuilder()
    .environment("prod")
    .application("order-service", "2.1.5", "pod-123", "k8s-node-01", "10.0.1.15")
    .trace(MDC.get("traceId"), MDC.get("spanId"), null)
    .request("/api/v1/orders", "GET", request.getRemoteAddr(), request.getHeader("User-Agent"))
    .user(currentUser.getId(), currentUser.getUsername(), currentUser.getTenantId())
    .database("mysql", "mysql-master", 3306, "order_db", "druid-pool-1")
    .sql(sql, parameters)
    .execution("success", executionTime, rowsAffected, rowsExamined, rowsReturned, null, null)
    .metadata("druid", "mybatis-plus", "selectOrders", "OrderMapper", 45)
    .autoAudit(1000.0);  // 慢SQL阈值1秒

builder.log();  // 直接输出日志
```

## 五、Elasticsearch索引映射
```json
{
  "mappings": {
    "properties": {
      "log_version": {"type": "keyword"},
      "timestamp": {"type": "date"},
      "environment": {"type": "keyword"},
      "application": {
        "properties": {
          "name": {"type": "keyword"},
          "version": {"type": "keyword"},
          "instance_id": {"type": "keyword"},
          "hostname": {"type": "keyword"},
          "ip": {"type": "ip"}
        }
      },
      "trace": {
        "properties": {
          "trace_id": {"type": "keyword"},
          "span_id": {"type": "keyword"},
          "parent_span_id": {"type": "keyword"}
        }
      },
      "user": {
        "properties": {
          "user_id": {"type": "keyword"},
          "username": {"type": "keyword"},
          "tenant_id": {"type": "keyword"}
        }
      },
      "database": {
        "properties": {
          "type": {"type": "keyword"},
          "database_name": {"type": "keyword"},
          "connection_pool": {"type": "keyword"}
        }
      },
      "sql": {
        "properties": {
          "sql_id": {"type": "keyword"},
          "sql_text": {"type": "text", "fields": {"keyword": {"type": "keyword"}}},
          "sql_template": {"type": "text"},
          "sql_type": {"type": "keyword"},
          "tables": {"type": "keyword"}
        }
      },
      "execution": {
        "properties": {
          "status": {"type": "keyword"},
          "execution_time_ms": {"type": "double"},
          "rows_affected": {"type": "long"},
          "rows_examined": {"type": "long"},
          "rows_returned": {"type": "long"}
        }
      },
      "audit": {
        "properties": {
          "is_parameterized": {"type": "boolean"},
          "has_where_clause": {"type": "boolean"},
          "is_slow_sql": {"type": "boolean"},
          "risk_level": {"type": "keyword"},
          "risk_reasons": {"type": "text"},
          "potential_injection": {"type": "boolean"},
          "full_table_scan": {"type": "boolean"}
        }
      }
    }
  }
}
```

## 六、常用查询分析示例
```json
// 1. 查询所有高风险SQL
{
  "query": {
    "term": {"audit.risk_level": "critical"}
  }
}

// 2. 查询慢SQL TOP 10
{
  "query": {"term": {"audit.is_slow_sql": true}},
  "sort": [{"execution.execution_time_ms": "desc"}],
  "size": 10
}

// 3. 查询SQL注入攻击
{
  "query": {
    "term": {"audit.potential_injection": true}
  }
}

// 4. 按表名聚合分析
{
  "aggs": {
    "tables": {
      "terms": {"field": "sql.tables", "size": 20},
      "aggs": {
        "avg_execution_time": {
          "avg": {"field": "execution.execution_time_ms"}
        }
      }
    }
  }
}

// 5. 查询某用户的所有SQL
{
  "query": {
    "term": {"user.user_id": "user_1001"}
  }
}
```

这个JSON格式规范涵盖了SQL审计的各个维度,你可以根据实际需求进行裁剪或扩展。有什么需要调整的吗?
这个对于业务影响太复杂了， 我只是想通过druid或者mybatis，把sql信息发送到kafka，然后后端进行审核是否有危险的sql

11:10

1 step
S


Sequential Thinking


S


Sequential Thinking


明白了！给你一个极简方案，对业务代码零侵入，只需配置就能把SQL发送到Kafka。

一、极简JSON格式
json
{
  "timestamp": "2024-12-17T10:30:45.123Z",
  "app_name": "order-service",
  "sql": "SELECT * FROM orders WHERE user_id = ?",
  "sql_type": "SELECT",
  "params": ["1001"],
  "exec_time_ms": 45.8,
  "rows_affected": 0,
  "db_name": "order_db",
  "success": true,
  "error_msg": null
}
就这10个字段，够用了！

二、方案一：Druid + Kafka（推荐）
1. 添加依赖
xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>druid-spring-boot-starter</artifactId>
    <version>1.2.20</version>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
2. 自定义Druid日志监听器
java
package com.example.config;

import com.alibaba.druid.pool.DruidDataSourceStatLogger;
import com.alibaba.druid.pool.DruidDataSourceStatLoggerAdapter;
import com.alibaba.druid.pool.DruidDataSourceStatValue;
import com.alibaba.druid.stat.JdbcSqlStatValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class SqlAuditLogger extends DruidDataSourceStatLoggerAdapter implements DruidDataSourceStatLogger {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${spring.application.name}")
    private String appName;
    
    @Value("${sql.audit.kafka.topic:sql-audit}")
    private String topic;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public void log(DruidDataSourceStatValue statValue) {
        List<JdbcSqlStatValue> sqlList = statValue.getSqlList();
        
        for (JdbcSqlStatValue sqlStat : sqlList) {
            try {
                Map<String, Object> auditLog = new HashMap<>();
                auditLog.put("timestamp", Instant.now().toString());
                auditLog.put("app_name", appName);
                auditLog.put("sql", sqlStat.getSql());
                auditLog.put("sql_type", determineSqlType(sqlStat.getSql()));
                auditLog.put("exec_time_ms", sqlStat.getExecuteMillisMax());
                auditLog.put("rows_affected", sqlStat.getUpdateCount());
                auditLog.put("db_name", statValue.getName());
                auditLog.put("success", sqlStat.getErrorCount() == 0);
                auditLog.put("error_msg", null);
                
                // 异步发送到Kafka
                String json = objectMapper.writeValueAsString(auditLog);
                kafkaTemplate.send(topic, json);
                
            } catch (Exception e) {
                // 记录失败不影响业务
                e.printStackTrace();
            }
        }
    }
    
    private String determineSqlType(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
}
3. Druid配置
yaml
spring:
  application:
    name: order-service
  
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      filters: stat,wall
      # 开启SQL监控
      filter:
        stat:
          enabled: true
          log-slow-sql: true
          slow-sql-millis: 1000
          merge-sql: true
      # 监控统计日志输出间隔(毫秒)
      time-between-log-stats-millis: 300000
      
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: 0  # 不等待确认，性能最高
      retries: 0

sql:
  audit:
    kafka:
      topic: sql-audit
三、方案二：MyBatis拦截器 + Kafka
1. MyBatis拦截器
java
package com.example.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", 
               args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", 
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlAuditInterceptor implements Interceptor {
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    @Value("${spring.application.name}")
    private String appName;
    
    @Value("${sql.audit.kafka.topic:sql-audit}")
    private String topic;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        
        String sql = boundSql.getSql();
        List<Object> params = getParameters(boundSql);
        
        Object result = null;
        boolean success = true;
        String errorMsg = null;
        int rowsAffected = 0;
        
        try {
            result = invocation.proceed();
            if (result instanceof Integer) {
                rowsAffected = (Integer) result;
            } else if (result instanceof List) {
                rowsAffected = ((List<?>) result).size();
            }
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long execTime = System.currentTimeMillis() - startTime;
            sendToKafka(sql, params, execTime, rowsAffected, success, errorMsg);
        }
        
        return result;
    }
    
    private void sendToKafka(String sql, List<Object> params, long execTime, 
                            int rowsAffected, boolean success, String errorMsg) {
        try {
            Map<String, Object> auditLog = new HashMap<>();
            auditLog.put("timestamp", Instant.now().toString());
            auditLog.put("app_name", appName);
            auditLog.put("sql", sql.replaceAll("\\s+", " ").trim());
            auditLog.put("sql_type", determineSqlType(sql));
            auditLog.put("params", params);
            auditLog.put("exec_time_ms", execTime);
            auditLog.put("rows_affected", rowsAffected);
            auditLog.put("success", success);
            auditLog.put("error_msg", errorMsg);
            
            String json = objectMapper.writeValueAsString(auditLog);
            
            // 异步发送，不阻塞业务
            kafkaTemplate.send(topic, json);
            
        } catch (Exception e) {
            // 静默失败，不影响业务
        }
    }
    
    private List<Object> getParameters(BoundSql boundSql) {
        List<Object> params = new ArrayList<>();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();
        
        if (parameterMappings != null && parameterObject != null) {
            for (ParameterMapping parameterMapping : parameterMappings) {
                String propertyName = parameterMapping.getProperty();
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    params.add(boundSql.getAdditionalParameter(propertyName));
                } else if (parameterObject instanceof Map) {
                    params.add(((Map<?, ?>) parameterObject).get(propertyName));
                } else {
                    params.add(parameterObject);
                }
            }
        }
        return params;
    }
    
    private String determineSqlType(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
2. 注册拦截器
java
@Configuration
public class MyBatisConfig {
    
    @Autowired
    private SqlAuditInterceptor sqlAuditInterceptor;
    
    @Bean
    public ConfigurationCustomizer configurationCustomizer() {
        return configuration -> configuration.addInterceptor(sqlAuditInterceptor);
    }
}
四、后端消费者 - SQL审计服务
java
package com.example.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SqlAuditConsumer {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // SQL注入特征
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
        Pattern.compile("(?i)(union.*select)"),
        Pattern.compile("(?i)(or\\s+1\\s*=\\s*1)"),
        Pattern.compile("(?i)(or\\s+'1'\\s*=\\s*'1')"),
        Pattern.compile("(?i)(--)|(/\\*.*\\*/)"),
        Pattern.compile("(?i)(;.*drop)"),
        Pattern.compile("(?i)(exec\\s*\\()")
    );
    
    @KafkaListener(topics = "sql-audit", groupId = "sql-audit-group")
    public void consumeSqlAudit(String message) {
        try {
            Map<String, Object> log = objectMapper.readValue(message, Map.class);
            
            String sql = (String) log.get("sql");
            String sqlType = (String) log.get("sql_type");
            double execTime = ((Number) log.get("exec_time_ms")).doubleValue();
            
            // 审计规则
            List<String> risks = new ArrayList<>();
            
            // 1. 检查慢SQL
            if (execTime > 1000) {
                risks.add("慢SQL: " + execTime + "ms");
            }
            
            // 2. 检查SQL注入
            if (checkSqlInjection(sql)) {
                risks.add("疑似SQL注入");
            }
            
            // 3. 检查无WHERE条件的UPDATE/DELETE
            if (("UPDATE".equals(sqlType) || "DELETE".equals(sqlType)) 
                && !sql.toUpperCase().contains("WHERE")) {
                risks.add("危险操作: " + sqlType + "缺少WHERE条件");
            }
            
            // 4. 检查SELECT *
            if ("SELECT".equals(sqlType) && sql.toUpperCase().contains("SELECT *")) {
                risks.add("不建议使用SELECT *");
            }
            
            // 5. 检查无LIMIT的大查询
            if ("SELECT".equals(sqlType) && !sql.toUpperCase().contains("LIMIT")) {
                risks.add("SELECT缺少LIMIT限制");
            }
            
            // 如果有风险，发送告警
            if (!risks.isEmpty()) {
                sendAlert(log, risks);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean checkSqlInjection(String sql) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                return true;
            }
        }
        return false;
    }
    
    private void sendAlert(Map<String, Object> log, List<String> risks) {
        System.out.println("⚠️ SQL风险告警 ⚠️");
        System.out.println("应用: " + log.get("app_name"));
        System.out.println("SQL: " + log.get("sql"));
        System.out.println("风险: " + String.join(", ", risks));
        System.out.println("-----------------------------------");
        
        // 这里可以接入钉钉/企业微信/邮件告警
        // dingTalkService.sendAlert(log, risks);
    }
}
五、部署配置
application.yml
yaml
spring:
  application:
    name: order-service
  
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: 0  # 不等待确认，性能最高
      retries: 0
    consumer:
      group-id: sql-audit-group
      auto-offset-reset: latest

sql:
  audit:
    kafka:
      topic: sql-audit
六、方案优势
✅ 零侵入 - 不需要改业务代码，只需配置
✅ 异步发送 - 发送到Kafka不影响业务性能
✅ 简单JSON - 只有10个字段，易于解析
✅ 灵活审计 - 后端可以随时调整审计规则
✅ 可扩展 - 可以部署多个消费者实例

需要我再简化吗？😊

只需要sql 写到日志文件即可，有专门的日志收集软件

11:13
S


Sequential Thinking
Request

{
  `thought`: `用户更简化了需求，不需要Kafka，只需要把SQL写到日志文件，然后用Filebeat/Fluentd这类日志收集工具采集。

所以现在需求变成：
1. 极简JSON格式（保持不变）
2. Druid配置，把SQL输出到日志文件
3. MyBatis拦截器，把SQL输出到日志文件
4. 使用SLF4J + Logback/Log4j2配置独立的SQL审计日志文件

这样更简单，对业务零影响，只需要配置日志框架就可以了。`,
  `thoughtNumber`: 1,
  `totalThoughts`: 2,
  `nextThoughtNeeded`: true
}
Response

{
  "thoughtNumber": 1,
  "totalThoughts": 2,
  "nextThoughtNeeded": true,
  "branches": [],
  "thoughtHistoryLength": 17
}
S


Sequential Thinking


好的！更简单了，直接写日志文件，用Filebeat/Fluentd采集。

一、极简JSON格式（一行一条）
json
{"timestamp":"2024-12-17T10:30:45.123Z","app":"order-service","sql":"SELECT * FROM orders WHERE user_id = ?","type":"SELECT","params":["1001"],"time_ms":45.8,"rows":15,"db":"order_db","success":true}
二、方案一：Druid + SLF4J日志
1. Druid配置（application.yml）
yaml
spring:
  application:
    name: order-service
  
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    url: jdbc:mysql://localhost:3306/order_db
    username: root
    password: 123456
    druid:
      # 开启SQL监控和日志
      filters: stat,slf4j
      filter:
        stat:
          enabled: true
          log-slow-sql: true
          slow-sql-millis: 1000
          merge-sql: false  # 不合并SQL，记录每条原始SQL
        slf4j:
          enabled: true
          # SQL日志级别
          statement-log-enabled: true
          statement-executable-sql-log-enable: true
2. 自定义Druid日志输出
java
package com.example.config;

import com.alibaba.druid.filter.logging.Slf4jLogFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DruidConfig {
    
    @Bean
    public Slf4jLogFilter slf4jLogFilter() {
        Slf4jLogFilter filter = new Slf4jLogFilter();
        // 使用独立的logger名称，方便在logback中单独配置
        filter.setStatementLoggerName("SQL_AUDIT");
        filter.setStatementExecutableSqlLogEnable(true);
        return filter;
    }
}
三、方案二：MyBatis拦截器（推荐）
MyBatis拦截器 - 输出JSON日志
java
package com.example.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@Intercepts({
    @Signature(type = Executor.class, method = "update", 
               args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query", 
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlAuditInterceptor implements Interceptor {
    
    // 使用独立的logger
    private static final Logger SQL_AUDIT_LOGGER = LoggerFactory.getLogger("SQL_AUDIT");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${spring.application.name:unknown}")
    private String appName;
    
    @Value("${spring.datasource.url:}")
    private String jdbcUrl;
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];
        BoundSql boundSql = mappedStatement.getBoundSql(parameter);
        
        String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
        List<Object> params = getParameters(boundSql);
        
        Object result = null;
        boolean success = true;
        String errorMsg = null;
        int rows = 0;
        
        try {
            result = invocation.proceed();
            
            if (result instanceof Integer) {
                rows = (Integer) result;
            } else if (result instanceof List) {
                rows = ((List<?>) result).size();
            }
            
        } catch (Exception e) {
            success = false;
            errorMsg = e.getMessage();
            throw e;
        } finally {
            long execTime = System.currentTimeMillis() - startTime;
            logSqlAudit(sql, params, execTime, rows, success, errorMsg);
        }
        
        return result;
    }
    
    private void logSqlAudit(String sql, List<Object> params, long execTime, 
                            int rows, boolean success, String errorMsg) {
        try {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("timestamp", Instant.now().toString());
            log.put("app", appName);
            log.put("sql", sql);
            log.put("type", determineSqlType(sql));
            log.put("params", params.isEmpty() ? null : params);
            log.put("time_ms", execTime);
            log.put("rows", rows);
            log.put("db", extractDbName(jdbcUrl));
            log.put("success", success);
            if (!success) {
                log.put("error", errorMsg);
            }
            
            // 输出一行JSON
            String jsonLog = objectMapper.writeValueAsString(log);
            SQL_AUDIT_LOGGER.info(jsonLog);
            
        } catch (Exception e) {
            // 静默失败
        }
    }
    
    private List<Object> getParameters(BoundSql boundSql) {
        List<Object> params = new ArrayList<>();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();
        
        if (parameterMappings != null && parameterObject != null) {
            for (ParameterMapping parameterMapping : parameterMappings) {
                String propertyName = parameterMapping.getProperty();
                Object value = null;
                
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject instanceof Map) {
                    value = ((Map<?, ?>) parameterObject).get(propertyName);
                } else {
                    value = parameterObject;
                    break; // 单个参数对象，只添加一次
                }
                
                params.add(value != null ? value.toString() : null);
            }
        }
        return params;
    }
    
    private String determineSqlType(String sql) {
        String upper = sql.toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
    
    private String extractDbName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) return "unknown";
        try {
            // 从jdbc:mysql://localhost:3306/order_db?xxx 提取 order_db
            int lastSlash = jdbcUrl.lastIndexOf("/");
            int questionMark = jdbcUrl.indexOf("?", lastSlash);
            if (questionMark > 0) {
                return jdbcUrl.substring(lastSlash + 1, questionMark);
            }
            return jdbcUrl.substring(lastSlash + 1);
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
注册拦截器
java
package com.example.config;

import com.example.interceptor.SqlAuditInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

@Configuration
public class MyBatisConfig {
    
    @Autowired
    private List<SqlSessionFactory> sqlSessionFactoryList;
    
    @Autowired
    private SqlAuditInterceptor sqlAuditInterceptor;
    
    @PostConstruct
    public void addInterceptor() {
        for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
            sqlSessionFactory.getConfiguration().addInterceptor(sqlAuditInterceptor);
        }
    }
}
四、Logback配置（独立SQL审计日志文件）
logback-spring.xml
xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 定义日志文件路径 -->
    <property name="LOG_PATH" value="/var/log/app"/>
    
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- 应用日志 -->
    <appender name="APP_LOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <!-- ==================== SQL审计日志（独立文件）==================== -->
    <appender name="SQL_AUDIT" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <!-- SQL审计日志文件 -->
        <file>${LOG_PATH}/sql-audit.log</file>
        
        <!-- 滚动策略：按天滚动 -->
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/sql-audit.%d{yyyy-MM-dd}.log</fileNamePattern>
            <!-- 保留30天 -->
            <maxHistory>30</maxHistory>
            <!-- 总大小限制 10GB -->
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        
        <!-- JSON格式输出，每行一条记录 -->
        <encoder>
            <pattern>%msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
        
        <!-- 异步写入，不阻塞业务 -->
        <append>true</append>
        <immediateFlush>false</immediateFlush>
    </appender>
    
    <!-- 异步SQL审计日志（推荐） -->
    <appender name="ASYNC_SQL_AUDIT" class="ch.qos.logback.classic.AsyncAppender">
        <!-- 队列大小 -->
        <queueSize>512</queueSize>
        <!-- 队列满时丢弃TRACE、DEBUG、INFO级别日志 -->
        <discardingThreshold>0</discardingThreshold>
        <!-- 不阻塞 -->
        <neverBlock>true</neverBlock>
        <appender-ref ref="SQL_AUDIT"/>
    </appender>
    
    <!-- SQL审计logger配置 -->
    <logger name="SQL_AUDIT" level="INFO" additivity="false">
        <appender-ref ref="ASYNC_SQL_AUDIT"/>
    </logger>
    
    <!-- 根logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="APP_LOG"/>
    </root>
</configuration>
五、生成的日志样例（sql-audit.log）
json
{"timestamp":"2024-12-17T10:30:45.123Z","app":"order-service","sql":"SELECT order_id, user_id, order_status FROM orders WHERE user_id = ? AND status = ?","type":"SELECT","params":["1001","PAID"],"time_ms":45,"rows":15,"db":"order_db","success":true}
{"timestamp":"2024-12-17T10:30:46.234Z","app":"order-service","sql":"UPDATE orders SET status = ? WHERE order_id = ?","type":"UPDATE","params":["COMPLETED","20241217001"],"time_ms":23,"rows":1,"db":"order_db","success":true}
{"timestamp":"2024-12-17T10:30:47.345Z","app":"order-service","sql":"DELETE FROM user_sessions","type":"DELETE","params":null,"time_ms":3258,"rows":125000,"db":"order_db","success":true}
{"timestamp":"2024-12-17T10:30:48.456Z","app":"order-service","sql":"SELECT * FROM products WHERE category = 'electronics' OR 1=1 --","type":"SELECT","params":null,"time_ms":1523,"rows":50000,"db":"product_db","success":true}
一行一条JSON，易于解析，Filebeat/Fluentd直接采集！

六、Filebeat采集配置示例
yaml
# filebeat.yml
filebeat.inputs:
- type: log
  enabled: true
  paths:
    - /var/log/app/sql-audit.log
  # JSON格式解析
  json.keys_under_root: true
  json.add_error_key: true
  fields:
    log_type: sql_audit

output.elasticsearch:
  hosts: ["localhost:9200"]
  index: "sql-audit-%{+yyyy.MM.dd}"

# 或者输出到Kafka
# output.kafka:
#   hosts: ["localhost:9092"]
#   topic: "sql-audit"
七、完整依赖（pom.xml）
xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- MyBatis -->
    <dependency>
        <groupId>org.mybatis.spring.boot</groupId>
        <artifactId>mybatis-spring-boot-starter</artifactId>
        <version>2.3.1</version>
    </dependency>
    
    <!-- Druid -->
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>druid-spring-boot-starter</artifactId>
        <version>1.2.20</version>
    </dependency>
    
    <!-- MySQL -->
    <dependency>
        <groupId>mysql</groupId>
        <artifactId>mysql-connector-java</artifactId>
    </dependency>
    
    <!-- Jackson JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    
    <!-- Lombok (可选) -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
八、方案总结
✅ 零侵入 - 业务代码无需修改
✅ 高性能 - 异步写日志，不阻塞
✅ 独立文件 - sql-audit.log 单独采集
✅ JSON格式 - 一行一条，易解析
✅ 自动滚动 - 按天分割，自动清理

直接用Filebeat/Fluentd采集 /var/log/app/sql-audit.log 即可！简单高效！

druid 如何将日志格式标准化的呢？

11:16

2 steps

Druid StatLogger 自定义日志格式
10 results


favicon
转存Druid监控记录到日志文件_druiddatasourcestatlogger-CSDN博客

csdn.net


favicon
保存Druid的监控记录 - Cousoulis - 博客园

cnblogs.com


favicon
转存Druid监控记录到日志文件的自定义实现和Druid的实现_druiddatasourcestatloggeradapter自定义存储-CSDN博客

csdn.net


favicon
DRUID怎么使用log4j把所有执行的sql记录到.log日志文件？ 配置报错 _问答-阿里云开发者社区

aliyun.com


favicon
怎么保存Druid的监控记录 · alibaba/druid Wiki · GitHub

github.com


favicon
配置_StatFilter · alibaba/druid Wiki · GitHub

github.com


favicon
Druid使用详细说明教程 | 无疑 官方网站 | nacos、dubbo 、arthas报错处理 | 阿里开源 | 无疑

alibaba.com


favicon
配置_LogFilter · alibaba/druid Wiki · GitHub

github.com


favicon
Druid中使用log4j2进行日志输出 · alibaba/druid Wiki · GitHub

github.com


favicon
使用Druid监控SQL执行状态 - OSCHINA - 中文开源技术交流社区

oschina.net

S


Sequential Thinking


好问题！Druid标准化日志格式有几种方式，我给你详细说明：

一、Druid日志输出的三种方式
方式1：LogFilter（不推荐 - 格式不是JSON）
Druid内置提供了四种LogFilter（Log4jFilter、Log4j2Filter、CommonsLogFilter、Slf4jLogFilter），用于输出JDBC执行的日志 
GitHub

yaml
spring:
  datasource:
    druid:
      filters: stat,slf4j  # 启用slf4j日志
```

**问题：** 这种方式输出的是文本格式，不是JSON，类似：
```
[INFO] Preparing: SELECT * FROM orders WHERE id = ?
[INFO] Parameters: 1001(Long)
[INFO] Total: 1
方式2：StatLogger（不推荐 - 只是统计汇总）
DruidDataSource有一个属性timeBetweenLogStatsMillis，配置timeBetweenLogStatsMillis>0之后，DruidDataSource会定期把监控数据输出到日志中 
Alibaba Cloud

这样输出的日志采用JSON的方式记录 
Zhihu

问题： 这个是定期输出汇总统计数据，不是每条SQL，格式类似：

json
{"url":"jdbc:mysql://127.0.0.1:3306/db","dbType":"mysql","activeCount":0,"poolingCount":1}
方式3：自定义Filter（推荐 ✅）
这是最佳方案！ 自定义Filter拦截每条SQL，输出标准JSON格式。

二、推荐方案：自定义Druid Filter输出JSON日志
1. 自定义SQL审计Filter
java
package com.example.config;

import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.filter.FilterEventAdapter;
import com.alibaba.druid.proxy.jdbc.JdbcParameter;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Druid SQL审计Filter - 输出标准JSON格式日志
 */
public class SqlAuditFilter extends FilterEventAdapter {
    
    // 使用独立的logger
    private static final Logger SQL_AUDIT_LOGGER = LoggerFactory.getLogger("SQL_AUDIT");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${spring.application.name:unknown}")
    private String appName;
    
    /**
     * Statement执行后的回调
     */
    @Override
    protected void statementExecuteAfter(StatementProxy statement, String sql, boolean result) {
        logSqlAudit(statement, sql, true, null);
    }
    
    /**
     * Statement执行查询后的回调
     */
    @Override
    protected void statementExecuteQueryAfter(StatementProxy statement, String sql, ResultSetProxy resultSet) {
        logSqlAudit(statement, sql, true, null);
    }
    
    /**
     * Statement执行更新后的回调
     */
    @Override
    protected void statementExecuteUpdateAfter(StatementProxy statement, String sql, int updateCount) {
        logSqlAudit(statement, sql, true, null);
    }
    
    /**
     * Statement执行批处理后的回调
     */
    @Override
    protected void statementExecuteBatchAfter(StatementProxy statement, int[] result) {
        logSqlAudit(statement, statement.getLastExecuteSql(), true, null);
    }
    
    /**
     * Statement执行错误的回调
     */
    @Override
    public void statement_executeErrorAfter(StatementProxy statement, String sql, Throwable error) {
        logSqlAudit(statement, sql, false, error.getMessage());
    }
    
    /**
     * 记录SQL审计日志
     */
    private void logSqlAudit(StatementProxy statement, String sql, boolean success, String errorMsg) {
        try {
            Map<String, Object> log = new LinkedHashMap<>();
            
            // 基础信息
            log.put("timestamp", Instant.now().toString());
            log.put("app", appName);
            log.put("sql", sql != null ? sql.replaceAll("\\s+", " ").trim() : "");
            log.put("type", determineSqlType(sql));
            
            // 参数
            List<Object> params = extractParameters(statement);
            if (!params.isEmpty()) {
                log.put("params", params);
            }
            
            // 执行信息
            log.put("time_ms", statement.getLastExecuteTimeNano() / 1_000_000.0);
            log.put("rows", statement.getUpdateCount());
            
            // 数据库信息
            String jdbcUrl = statement.getConnectionProxy().getDirectDataSource().getUrl();
            log.put("db", extractDbName(jdbcUrl));
            
            // 状态
            log.put("success", success);
            if (!success && errorMsg != null) {
                log.put("error", errorMsg);
            }
            
            // 输出JSON日志
            String jsonLog = objectMapper.writeValueAsString(log);
            SQL_AUDIT_LOGGER.info(jsonLog);
            
        } catch (Exception e) {
            // 静默失败，不影响业务
        }
    }
    
    /**
     * 提取SQL参数
     */
    private List<Object> extractParameters(StatementProxy statement) {
        List<Object> params = new ArrayList<>();
        try {
            Map<Integer, JdbcParameter> parameters = statement.getParameters();
            if (parameters != null && !parameters.isEmpty()) {
                // 按参数索引排序
                List<Integer> keys = new ArrayList<>(parameters.keySet());
                Collections.sort(keys);
                for (Integer key : keys) {
                    JdbcParameter param = parameters.get(key);
                    if (param != null) {
                        Object value = param.getValue();
                        params.add(value != null ? value.toString() : null);
                    }
                }
            }
        } catch (Exception e) {
            // 参数提取失败不影响日志
        }
        return params;
    }
    
    /**
     * 判断SQL类型
     */
    private String determineSqlType(String sql) {
        if (sql == null || sql.isEmpty()) return "UNKNOWN";
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }
    
    /**
     * 从JDBC URL提取数据库名
     */
    private String extractDbName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) return "unknown";
        try {
            int lastSlash = jdbcUrl.lastIndexOf("/");
            int questionMark = jdbcUrl.indexOf("?", lastSlash);
            if (questionMark > 0) {
                return jdbcUrl.substring(lastSlash + 1, questionMark);
            }
            return jdbcUrl.substring(lastSlash + 1);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
2. 注册Filter到Druid
Spring Boot配置方式
java
package com.example.config;

import com.alibaba.druid.spring.boot.autoconfigure.DruidDataSourceBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Arrays;

@Configuration
public class DruidConfig {
    
    @Bean
    @ConfigurationProperties("spring.datasource.druid")
    public DataSource dataSource() {
        com.alibaba.druid.pool.DruidDataSource dataSource = 
            DruidDataSourceBuilder.create().build();
        
        // 注册自定义Filter
        SqlAuditFilter sqlAuditFilter = new SqlAuditFilter();
        dataSource.setProxyFilters(Arrays.asList(sqlAuditFilter));
        
        return dataSource;
    }
    
    @Bean
    public SqlAuditFilter sqlAuditFilter() {
        return new SqlAuditFilter();
    }
}
XML配置方式
xml
<!-- 自定义Filter -->
<bean id="sqlAuditFilter" class="com.example.config.SqlAuditFilter"/>

<!-- Druid数据源 -->
<bean id="dataSource" class="com.alibaba.druid.pool.DruidDataSource" 
      init-method="init" destroy-method="close">
    <property name="url" value="${jdbc.url}"/>
    <property name="username" value="${jdbc.username}"/>
    <property name="password" value="${jdbc.password}"/>
    
    <!-- 其他配置... -->
    
    <!-- 注册自定义Filter -->
    <property name="proxyFilters">
        <list>
            <ref bean="sqlAuditFilter"/>
        </list>
    </property>
</bean>
3. application.yml配置
yaml
spring:
  application:
    name: order-service
  
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      url: jdbc:mysql://localhost:3306/order_db
      username: root
      password: 123456
      driver-class-name: com.mysql.cj.jdbc.Driver
      
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      
      # 开启StatFilter用于性能监控
      filters: stat
      
      filter:
        stat:
          enabled: true
          log-slow-sql: true
          slow-sql-millis: 1000
4. Logback配置（独立日志文件）
xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATH" value="/var/log/app"/>
    
    <!-- SQL审计日志（独立文件） -->
    <appender name="SQL_AUDIT" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_PATH}/sql-audit.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>${LOG_PATH}/sql-audit.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>
    
    <!-- 异步SQL审计日志 -->
    <appender name="ASYNC_SQL_AUDIT" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>true</neverBlock>
        <appender-ref ref="SQL_AUDIT"/>
    </appender>
    
    <!-- SQL审计logger -->
    <logger name="SQL_AUDIT" level="INFO" additivity="false">
        <appender-ref ref="ASYNC_SQL_AUDIT"/>
    </logger>
    
    <!-- 根logger -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
三、生成的日志样例
sql-audit.log（每行一条JSON）
json
{"timestamp":"2024-12-17T10:30:45.123Z","app":"order-service","sql":"SELECT order_id, user_id, status FROM orders WHERE user_id = ? AND status = ?","type":"SELECT","params":["1001","PAID"],"time_ms":45.8,"rows":15,"db":"order_db","success":true}
{"timestamp":"2024-12-17T10:30:46.234Z","app":"order-service","sql":"UPDATE orders SET status = ? WHERE order_id = ?","type":"UPDATE","params":["COMPLETED","20241217001"],"time_ms":23.4,"rows":1,"db":"order_db","success":true}
{"timestamp":"2024-12-17T10:30:47.345Z","app":"order-service","sql":"DELETE FROM user_sessions WHERE created_at < ?","type":"DELETE","params":["2024-12-10"],"time_ms":156.7,"rows":5000,"db":"order_db","success":true}
{"timestamp":"2024-12-17T10:30:48.456Z","app":"order-service","sql":"INSERT INTO logs (user_id, action, created_at) VALUES (?, ?, ?)","type":"INSERT","params":["1001","LOGIN","2024-12-17 10:30:48"],"time_ms":12.3,"rows":1,"db":"order_db","success":true}
四、方案对比总结
方案	输出格式	每条SQL	JSON格式	推荐度
LogFilter	文本	✅	❌	⭐⭐
StatLogger	JSON	❌(汇总)	✅	⭐⭐
自定义Filter	JSON	✅	✅	⭐⭐⭐⭐⭐
最佳方案就是自定义Filter！ 完全满足你的需求：

✅ 标准JSON格式
✅ 每条SQL都记录
✅ 输出到独立日志文件
✅ 对业务零侵入
✅ 异步写入，性能高
直接用Filebeat采集 /var/log/app/sql-audit.log 即可！

是否可以将数据库的类型，版本也加进来

11:18
当然可以！我给你补充数据库类型和版本信息。

修改后的SqlAuditFilter
java
package com.example.config;

import com.alibaba.druid.filter.FilterEventAdapter;
import com.alibaba.druid.proxy.jdbc.JdbcParameter;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Druid SQL审计Filter - 输出标准JSON格式日志
 */
public class SqlAuditFilter extends FilterEventAdapter {
    
    private static final Logger SQL_AUDIT_LOGGER = LoggerFactory.getLogger("SQL_AUDIT");
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${spring.application.name:unknown}")
    private String appName;
    
    // 缓存数据库元信息，避免每次SQL都查询
    private static final Map<String, DatabaseInfo> DB_INFO_CACHE = new ConcurrentHashMap<>();
    
    @Override
    protected void statementExecuteAfter(StatementProxy statement, String sql, boolean result) {
        logSqlAudit(statement, sql, true, null);
    }
    
    @Override
    protected void statementExecuteQueryAfter(StatementProxy statement, String sql, ResultSetProxy resultSet) {
        logSqlAudit(statement, sql, true, null);
    }
    
    @Override
    protected void statementExecuteUpdateAfter(StatementProxy statement, String sql, int updateCount) {
        logSqlAudit(statement, sql, true, null);
    }
    
    @Override
    protected void statementExecuteBatchAfter(StatementProxy statement, int[] result) {
        logSqlAudit(statement, statement.getLastExecuteSql(), true, null);
    }
    
    @Override
    public void statement_executeErrorAfter(StatementProxy statement, String sql, Throwable error) {
        logSqlAudit(statement, sql, false, error.getMessage());
    }
    
    /**
     * 记录SQL审计日志
     */
    private void logSqlAudit(StatementProxy statement, String sql, boolean success, String errorMsg) {
        try {
            Map<String, Object> log = new LinkedHashMap<>();
            
            // 基础信息
            log.put("timestamp", Instant.now().toString());
            log.put("app", appName);
            log.put("sql", sql != null ? sql.replaceAll("\\s+", " ").trim() : "");
            log.put("type", determineSqlType(sql));
            
            // 参数
            List<Object> params = extractParameters(statement);
            if (!params.isEmpty()) {
                log.put("params", params);
            }
            
            // 执行信息
            log.put("time_ms", statement.getLastExecuteTimeNano() / 1_000_000.0);
            log.put("rows", statement.getUpdateCount());
            
            // 数据库信息（包含类型和版本）
            DatabaseInfo dbInfo = getDatabaseInfo(statement);
            log.put("db_name", dbInfo.dbName);
            log.put("db_type", dbInfo.dbType);
            log.put("db_version", dbInfo.dbVersion);
            
            // 状态
            log.put("success", success);
            if (!success && errorMsg != null) {
                log.put("error", errorMsg);
            }
            
            // 输出JSON日志
            String jsonLog = objectMapper.writeValueAsString(log);
            SQL_AUDIT_LOGGER.info(jsonLog);
            
        } catch (Exception e) {
            // 静默失败，不影响业务
        }
    }
    
    /**
     * 获取数据库信息（带缓存）
     */
    private DatabaseInfo getDatabaseInfo(StatementProxy statement) {
        try {
            String jdbcUrl = statement.getConnectionProxy().getDirectDataSource().getUrl();
            
            // 从缓存中获取
            DatabaseInfo cached = DB_INFO_CACHE.get(jdbcUrl);
            if (cached != null) {
                return cached;
            }
            
            // 获取数据库元数据
            Connection connection = statement.getConnectionProxy().getRawObject();
            DatabaseMetaData metaData = connection.getMetaData();
            
            DatabaseInfo dbInfo = new DatabaseInfo();
            dbInfo.dbName = extractDbName(jdbcUrl);
            dbInfo.dbType = metaData.getDatabaseProductName();
            dbInfo.dbVersion = metaData.getDatabaseProductVersion();
            
            // 标准化数据库类型名称
            dbInfo.dbType = normalizeDatabaseType(dbInfo.dbType);
            
            // 缓存起来
            DB_INFO_CACHE.put(jdbcUrl, dbInfo);
            
            return dbInfo;
            
        } catch (Exception e) {
            // 获取失败返回默认值
            DatabaseInfo defaultInfo = new DatabaseInfo();
            defaultInfo.dbName = "unknown";
            defaultInfo.dbType = "unknown";
            defaultInfo.dbVersion = "unknown";
            return defaultInfo;
        }
    }
    
    /**
     * 标准化数据库类型名称
     */
    private String normalizeDatabaseType(String productName) {
        if (productName == null) return "unknown";
        String lower = productName.toLowerCase();
        if (lower.contains("mysql")) return "mysql";
        if (lower.contains("postgresql") || lower.contains("postgres")) return "postgresql";
        if (lower.contains("oracle")) return "oracle";
        if (lower.contains("sql server") || lower.contains("sqlserver")) return "sqlserver";
        if (lower.contains("h2")) return "h2";
        if (lower.contains("sqlite")) return "sqlite";
        if (lower.contains("mariadb")) return "mariadb";
        if (lower.contains("db2")) return "db2";
        return productName;
    }
    
    /**
     * 提取SQL参数
     */
    private List<Object> extractParameters(StatementProxy statement) {
        List<Object> params = new ArrayList<>();
        try {
            Map<Integer, JdbcParameter> parameters = statement.getParameters();
            if (parameters != null && !parameters.isEmpty()) {
                List<Integer> keys = new ArrayList<>(parameters.keySet());
                Collections.sort(keys);
                for (Integer key : keys) {
                    JdbcParameter param = parameters.get(key);
                    if (param != null) {
                        Object value = param.getValue();
                        params.add(value != null ? value.toString() : null);
                    }
                }
            }
        } catch (Exception e) {
            // 参数提取失败不影响日志
        }
        return params;
    }
    
    /**
     * 判断SQL类型
     */
    private String determineSqlType(String sql) {
        if (sql == null || sql.isEmpty()) return "UNKNOWN";
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("SELECT")) return "SELECT";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("CREATE")) return "DDL";
        if (upper.startsWith("ALTER")) return "DDL";
        if (upper.startsWith("DROP")) return "DDL";
        return "OTHER";
    }
    
    /**
     * 从JDBC URL提取数据库名
     */
    private String extractDbName(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) return "unknown";
        try {
            int lastSlash = jdbcUrl.lastIndexOf("/");
            int questionMark = jdbcUrl.indexOf("?", lastSlash);
            if (questionMark > 0) {
                return jdbcUrl.substring(lastSlash + 1, questionMark);
            }
            return jdbcUrl.substring(lastSlash + 1);
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    /**
     * 数据库信息缓存对象
     */
    private static class DatabaseInfo {
        String dbName;
        String dbType;
        String dbVersion;
    }
}
更新后的JSON格式
json
{
  "timestamp": "2024-12-17T10:30:45.123Z",
  "app": "order-service",
  "sql": "SELECT * FROM orders WHERE user_id = ?",
  "type": "SELECT",
  "params": ["1001"],
  "time_ms": 45.8,
  "rows": 15,
  "db_name": "order_db",
  "db_type": "mysql",
  "db_version": "8.0.35",
  "success": true
}
实际日志样例（sql-audit.log）
json
{"timestamp":"2024-12-17T10:30:45.123Z","app":"order-service","sql":"SELECT order_id, user_id, status FROM orders WHERE user_id = ? AND status = ?","type":"SELECT","params":["1001","PAID"],"time_ms":45.8,"rows":15,"db_name":"order_db","db_type":"mysql","db_version":"8.0.35","success":true}
{"timestamp":"2024-12-17T10:30:46.234Z","app":"user-service","sql":"UPDATE users SET last_login = ? WHERE user_id = ?","type":"UPDATE","params":["2024-12-17 10:30:46","1001"],"time_ms":23.4,"rows":1,"db_name":"user_db","db_type":"postgresql","db_version":"14.9","success":true}
{"timestamp":"2024-12-17T10:30:47.345Z","app":"log-service","sql":"INSERT INTO audit_logs (user_id, action, created_at) VALUES (?, ?, ?)","type":"INSERT","params":["1001","LOGIN","2024-12-17 10:30:47"],"time_ms":12.3,"rows":1,"db_name":"log_db","db_type":"mysql","db_version":"5.7.42","success":true}
{"timestamp":"2024-12-17T10:30:48.456Z","app":"product-service","sql":"SELECT * FROM products WHERE category = ?","type":"SELECT","params":["electronics"],"time_ms":156.7,"rows":500,"db_name":"product_db","db_type":"mariadb","db_version":"10.11.5","success":true}
关键改进点
1. 缓存机制
java
private static final Map<String, DatabaseInfo> DB_INFO_CACHE = new ConcurrentHashMap<>();
数据库类型和版本信息只在第一次获取，后续从缓存读取，避免每次SQL都查询DatabaseMetaData，提升性能。

2. 标准化数据库类型
java
private String normalizeDatabaseType(String productName) {
    if (lower.contains("mysql")) return "mysql";
    if (lower.contains("postgresql")) return "postgresql";
    // ...
}
将MySQL、MySql、MySQL Community Server统一为mysql，方便后续分析。

3. 完整的数据库元信息
db_name: 数据库名（从JDBC URL提取）
db_type: 数据库类型（mysql, postgresql, oracle等）
db_version: 数据库版本（如8.0.35, 14.9等）
各种数据库的实际输出
MySQL
json
{"db_name":"order_db","db_type":"mysql","db_version":"8.0.35"}
{"db_name":"legacy_db","db_type":"mysql","db_version":"5.7.42"}
PostgreSQL
json
{"db_name":"user_db","db_type":"postgresql","db_version":"14.9"}
{"db_name":"analytics_db","db_type":"postgresql","db_version":"15.4"}
Oracle
json
{"db_name":"prod_db","db_type":"oracle","db_version":"19.3.0.0.0"}
SQL Server
json
{"db_name":"crm_db","db_type":"sqlserver","db_version":"15.00.2000"}
MariaDB
json
{"db_name":"product_db","db_type":"mariadb","db_version":"10.11.5"}
这样你就可以在审计时区分不同的数据库类型和版本了！比如：

统计各数据库类型的慢SQL分布
检查某个特定版本的数据库是否有问题
分析不同数据库类型的性能差异
需要再加其他字段吗？