# SQL Audit Service - 完整启动指南

## 🎯 快速启动步骤

### 步骤1: 部署MySQL到远程主机(192.168.220.202)

```bash
# 1.1 将配置文件复制到远程主机
scp docker-compose-mysql.yml user@192.168.220.202:/opt/sql-audit/
scp mysql-init.sql user@192.168.220.202:/opt/sql-audit/mysql/init/

# 1.2 SSH登录到远程主机
ssh user@192.168.220.202

# 1.3 启动MySQL容器
cd /opt/sql-audit
docker-compose -f docker-compose-mysql.yml up -d

# 1.4 验证MySQL启动成功
docker-compose -f docker-compose-mysql.yml logs -f mysql
docker ps | grep sql-audit-mysql

# 1.5 测试数据库连接
docker exec -it sql-audit-mysql mysql -uroot -proot123 -e "SHOW DATABASES;"
```

**预期输出**:
```
+--------------------+
| Database           |
+--------------------+
| information_schema |
| mysql              |
| performance_schema |
| sql_audit_service  |
| sys                |
+--------------------+
```

### 步骤2: 验证Kafka可访问性

```bash
# 从本地主机测试Kafka连接
telnet 192.168.126.18 9092

# 或使用kafka-console-consumer测试(如果已安装Kafka客户端)
kafka-console-consumer --bootstrap-server 192.168.126.18:9092 --topic sql-audit-events --from-beginning --max-messages 1
```

### 步骤3: 编译项目

```bash
# 进入项目根目录
cd /Users/liujinliang/workspace/ai/sqltools/sql-audit-service

# 编译整个项目(跳过测试)
mvn clean package -DskipTests

# 或者仅编译Web模块
cd sql-audit-service-web
mvn clean package -DskipTests
```

### 步骤4: 启动Web服务

```bash
# 方式1: 使用Maven启动(开发环境)
cd sql-audit-service-web
mvn spring-boot:run

# 方式2: 使用JAR包启动(生产环境)
cd sql-audit-service-web/target
java -jar sql-audit-service-web-1.0.0-SNAPSHOT.jar

# 方式3: 指定配置文件启动
java -jar sql-audit-service-web-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 步骤5: 验证服务启动

```bash
# 检查服务健康状态
curl http://localhost:8080/actuator/health

# 查看Prometheus指标
curl http://localhost:8080/actuator/prometheus

# 访问Swagger UI
open http://localhost:8080/swagger-ui.html

# 检查Kafka消费者状态
curl http://localhost:8080/actuator/metrics/kafka.consumer.fetch.manager.records.consumed.total
```

**预期健康检查输出**:
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "MySQL",
        "validationQuery": "isValid()"
      }
    },
    "kafka": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

## 📋 配置概览

### Kafka配置
- **Bootstrap Servers**: `192.168.126.18:9092`
- **Topic**: `sql-audit-events`
- **Consumer Group**: `audit-service`
- **DLQ Topic**: `sql-audit-events-dlq`

### MySQL配置
- **Host**: `192.168.220.202`
- **Port**: `3306`
- **Database**: `sql_audit_service`
- **Username**: `root`
- **Password**: `root123`

### Web服务配置
- **Port**: `8080` (默认)
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **Actuator**: `http://localhost:8080/actuator`
- **Metrics**: `http://localhost:8080/actuator/prometheus`

## 🔍 日志位置

启动后查看日志:

```bash
# 应用日志(控制台输出)
tail -f logs/sql-audit-service.log

# MySQL日志
ssh user@192.168.220.202
docker logs -f sql-audit-mysql

# 慢查询日志
ssh user@192.168.220.202
docker exec sql-audit-mysql tail -f /var/lib/mysql/slow.log
```

## 🧪 功能测试

### 1. 发送测试消息到Kafka

```bash
# 使用kafka-console-producer发送测试消息
kafka-console-producer --bootstrap-server 192.168.126.18:9092 --topic sql-audit-events

# 输入以下JSON(回车发送)
{"sqlId":"test-001","sql":"SELECT * FROM users WHERE 1=1","sqlType":"SELECT","mapperId":"UserMapper.selectAll","timestamp":"2025-12-25T10:00:00Z","executionTimeMs":100,"rowsAffected":50}
```

### 2. 查看消费日志

观察控制台输出，应该看到类似以下日志:

```
2025-12-25 10:00:01.234  INFO 12345 --- [kafka-virtual-1] c.f.a.s.c.KafkaAuditEventConsumer : Successfully processed audit event from partition 0 offset 123 in 45ms, result: SUCCESS
```

### 3. 查询审计报告

```bash
# 查询所有审计报告
curl http://localhost:8080/api/audit-reports?page=0&size=10

# 查询高风险报告
curl http://localhost:8080/api/audit-reports?riskLevel=HIGH

# 查看统计信息
curl http://localhost:8080/api/statistics/overview
```

## 🐛 故障排查

### 问题1: 无法连接MySQL

**症状**: 启动时报错 `Communications link failure`

**解决方案**:
```bash
# 1. 验证MySQL容器运行中
ssh user@192.168.220.202
docker ps | grep mysql

# 2. 测试网络连通性
telnet 192.168.220.202 3306

# 3. 检查防火墙
ssh user@192.168.220.202
sudo firewall-cmd --list-all | grep 3306

# 4. 检查MySQL日志
docker logs sql-audit-mysql
```

### 问题2: 无法连接Kafka

**症状**: 启动时报错 `Connection refused` 或 `Timeout`

**解决方案**:
```bash
# 1. 测试Kafka连通性
telnet 192.168.126.18 9092

# 2. 检查Kafka服务状态
ssh user@192.168.126.18
sudo systemctl status kafka

# 3. 查看Kafka日志
ssh user@192.168.126.18
tail -f /var/log/kafka/server.log
```

### 问题3: Consumer没有消费消息

**症状**: Kafka有消息但服务不消费

**解决方案**:
```bash
# 1. 检查消费者组状态
kafka-consumer-groups --bootstrap-server 192.168.126.18:9092 --describe --group audit-service

# 2. 查看应用日志
grep "KafkaAuditEventConsumer" logs/sql-audit-service.log

# 3. 检查背压控制是否暂停消费
curl http://localhost:8080/actuator/metrics | grep backpressure

# 4. 重启服务
kill <PID>
mvn spring-boot:run
```

### 问题4: 内存不足

**症状**: `OutOfMemoryError` 或服务频繁重启

**解决方案**:
```bash
# 增加JVM堆内存
java -Xms1g -Xmx2g -jar sql-audit-service-web-1.0.0-SNAPSHOT.jar

# 或在application.yml中配置
# spring.jpa.properties.hibernate.jdbc.batch_size=20
```

## 📊 监控指标

### Prometheus指标

```bash
# 消费吞吐量
audit_kafka_throughput_total

# 消费延迟
audit_kafka_processing_time_seconds

# 错误率
audit_kafka_errors_total

# 消费Lag
audit_kafka_lag

# Kafka消费者指标
kafka_consumer_fetch_manager_records_consumed_total
```

### Grafana Dashboard

推荐导入以下Dashboard:
- Kafka Consumer Dashboard (ID: 7589)
- Spring Boot Dashboard (ID: 6756)
- MySQL Dashboard (ID: 7362)

## 🔄 升级和维护

### 更新代码

```bash
# 拉取最新代码
git pull origin main

# 重新编译
mvn clean package -DskipTests

# 重启服务
# 方式1: 直接kill并重启
pkill -f sql-audit-service-web
java -jar target/sql-audit-service-web-1.0.0-SNAPSHOT.jar &

# 方式2: 使用systemd(生产环境推荐)
sudo systemctl restart sql-audit-service
```

### 数据库迁移

```bash
# 备份数据库
ssh user@192.168.220.202
docker exec sql-audit-mysql mysqldump -uroot -proot123 sql_audit_service > backup_$(date +%Y%m%d).sql

# 恢复数据库
docker exec -i sql-audit-mysql mysql -uroot -proot123 sql_audit_service < backup_20251225.sql
```

## 📞 联系支持

- 文档: `README.md`
- 问题反馈: GitHub Issues
- 开发者: <your-email@example.com>

## ✅ 验证清单

完成以下检查确保服务正常运行:

- [ ] MySQL容器在192.168.220.202上运行
- [ ] MySQL数据库`sql_audit_service`已创建
- [ ] 可以从本地连接到192.168.220.202:3306
- [ ] Kafka在192.168.126.18:9092可访问
- [ ] Web服务启动成功(http://localhost:8080/actuator/health返回UP)
- [ ] Swagger UI可访问(http://localhost:8080/swagger-ui.html)
- [ ] Kafka消费者成功消费测试消息
- [ ] 审计报告保存到MySQL数据库
- [ ] Prometheus指标正常暴露

祝部署顺利! 🚀
