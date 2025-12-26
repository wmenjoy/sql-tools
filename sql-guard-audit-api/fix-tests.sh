#!/bin/bash

# 批量修复测试文件，添加 executionLayer 字段并重命名 mapperId

cd /Users/liujinliang/workspace/ai/sqltools/sql-guard-audit-api/src/test/java/com/footstone/sqlguard/audit

for file in AuditLogWriterBenchmark.java AuditLogRollingIntegrationTest.java LogbackAuditWriterTest.java AuditLogWriterTest.java LogbackAuditAppenderTest.java AuditLogWriterIntegrationTest.java; do
  if [ -f "$file" ]; then
    echo "Processing $file..."

    # 1. 添加 ExecutionLayer import（如果还没有）
    if ! grep -q "import com.footstone.sqlguard.core.model.ExecutionLayer" "$file"; then
      # 在 SqlCommandType import 后面添加
      sed -i '' '/import com\.footstone\.sqlguard\.core\.model\.SqlCommandType/a\
import com.footstone.sqlguard.core.model.ExecutionLayer;
' "$file"
    fi

    # 2. 将 .mapperId( 改为 .statementId(
    sed -i '' 's/\.mapperId(/\.statementId(/g' "$file"

    # 3. 在 .sql( 后面添加 .executionLayer(ExecutionLayer.MYBATIS) (简化处理：在 .sqlType( 后面添加)
    # 这个比较复杂，需要手动处理

  fi
done

echo "Done! Please manually add .executionLayer(ExecutionLayer.MYBATIS) after .sqlType() in each test."
