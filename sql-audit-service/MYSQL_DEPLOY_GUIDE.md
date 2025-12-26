# ============================================================================
# SQL Audit Service - 远程MySQL部署指南
# ============================================================================

## 部署步骤

### 1. 准备远程主机(192.168.220.202)

```bash
# SSH登录到远程主机
ssh user@192.168.220.202

# 创建部署目录
sudo mkdir -p /opt/sql-audit/mysql/{data,conf,init}
sudo chown -R $USER:$USER /opt/sql-audit
```

### 2. 上传配置文件

从本地主机上传文件到远程主机:

```bash
# 上传Docker Compose文件
scp docker-compose-mysql.yml user@192.168.220.202:/opt/sql-audit/

# 上传MySQL初始化脚本
scp mysql-init.sql user@192.168.220.202:/opt/sql-audit/mysql/init/

# (可选)上传MySQL自定义配置
scp my.cnf user@192.168.220.202:/opt/sql-audit/mysql/conf/
```

### 3. 启动MySQL

在远程主机上执行:

```bash
# 进入部署目录
cd /opt/sql-audit

# 启动MySQL容器
docker-compose -f docker-compose-mysql.yml up -d

# 查看启动日志
docker-compose -f docker-compose-mysql.yml logs -f mysql

# 检查容器状态
docker-compose -f docker-compose-mysql.yml ps
```

### 4. 验证MySQL

```bash
# 使用MySQL客户端连接
docker exec -it sql-audit-mysql mysql -uroot -proot123

# 或者从本地主机连接(确保防火墙已开放3306端口)
mysql -h 192.168.220.202 -uroot -proot123 -e "SHOW DATABASES;"
```

### 5. 防火墙配置

如果远程主机有防火墙,需要开放3306端口:

```bash
# CentOS/RHEL (firewalld)
sudo firewall-cmd --permanent --add-port=3306/tcp
sudo firewall-cmd --reload

# Ubuntu/Debian (ufw)
sudo ufw allow 3306/tcp
sudo ufw reload

# 验证端口开放
sudo netstat -tlnp | grep 3306
```

## 配置说明

### MySQL配置优化

创建 `/opt/sql-audit/mysql/conf/my.cnf`:

```ini
[mysqld]
# 字符集
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# 连接数
max_connections=1000
max_connect_errors=1000

# 缓冲区大小
innodb_buffer_pool_size=2G
innodb_log_buffer_size=16M

# 日志配置
log_error=/var/lib/mysql/error.log
slow_query_log=1
slow_query_log_file=/var/lib/mysql/slow.log
long_query_time=2

# 性能优化
innodb_flush_log_at_trx_commit=2
innodb_flush_method=O_DIRECT
innodb_io_capacity=2000
innodb_io_capacity_max=4000

# 时区
default-time-zone='+8:00'

[client]
default-character-set=utf8mb4
```

## 管理命令

### 启动/停止/重启

```bash
# 启动
docker-compose -f docker-compose-mysql.yml up -d

# 停止
docker-compose -f docker-compose-mysql.yml stop

# 重启
docker-compose -f docker-compose-mysql.yml restart

# 停止并删除容器
docker-compose -f docker-compose-mysql.yml down

# 停止并删除容器和数据卷(危险!)
docker-compose -f docker-compose-mysql.yml down -v
```

### 查看日志

```bash
# 实时查看日志
docker-compose -f docker-compose-mysql.yml logs -f

# 查看最近100行日志
docker-compose -f docker-compose-mysql.yml logs --tail=100

# 查看MySQL慢查询日志
docker exec sql-audit-mysql tail -f /var/lib/mysql/slow.log
```

### 备份恢复

```bash
# 备份数据库
docker exec sql-audit-mysql mysqldump -uroot -proot123 sql_audit_service > backup_$(date +%Y%m%d).sql

# 恢复数据库
docker exec -i sql-audit-mysql mysql -uroot -proot123 sql_audit_service < backup_20231225.sql
```

## 连接信息

| 配置项 | 值 |
|--------|-----|
| 主机 | 192.168.220.202 |
| 端口 | 3306 |
| 数据库 | sql_audit_service |
| 用户名 | root |
| 密码 | root123 |

## JDBC连接字符串

```
jdbc:mysql://192.168.220.202:3306/sql_audit_service?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
```

## 故障排查

### 1. 容器无法启动

```bash
# 查看容器日志
docker logs sql-audit-mysql

# 检查端口占用
sudo netstat -tlnp | grep 3306

# 检查数据目录权限
ls -la /opt/sql-audit/mysql/data
```

### 2. 无法从外部连接

```bash
# 检查MySQL绑定地址
docker exec sql-audit-mysql mysql -uroot -proot123 -e "SHOW VARIABLES LIKE 'bind_address';"

# 检查防火墙
sudo firewall-cmd --list-all

# 检查网络连通性
telnet 192.168.220.202 3306
```

### 3. 性能问题

```bash
# 查看当前连接数
docker exec sql-audit-mysql mysql -uroot -proot123 -e "SHOW PROCESSLIST;"

# 查看InnoDB状态
docker exec sql-audit-mysql mysql -uroot -proot123 -e "SHOW ENGINE INNODB STATUS\G"

# 查看慢查询
docker exec sql-audit-mysql mysql -uroot -proot123 -e "SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;"
```

## 监控指标

推荐使用Prometheus + Grafana监控MySQL:

1. 安装MySQL Exporter
2. 配置Prometheus抓取指标
3. 导入Grafana Dashboard (ID: 7362)

监控指标包括:
- QPS (Queries Per Second)
- 连接数
- 慢查询数量
- InnoDB缓冲池命中率
- 锁等待情况
