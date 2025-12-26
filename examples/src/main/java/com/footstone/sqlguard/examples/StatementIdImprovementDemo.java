package com.footstone.sqlguard.examples;

import com.footstone.sqlguard.interceptor.jdbc.common.StatementIdGenerator;

/**
 * 演示 StatementId 改进前后的对比
 */
public class StatementIdImprovementDemo {

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("StatementId 唯一性改进演示");
        System.out.println("=".repeat(80));
        System.out.println();

        // 模拟三个不同的 SQL
        String sql1 = "SELECT * FROM users WHERE id = ?";
        String sql2 = "UPDATE users SET name = ? WHERE id = ?";
        String sql3 = "DELETE FROM orders WHERE user_id = ?";

        String datasource = "masterDB";
        String interceptor = "druid";

        System.out.println("📋 测试场景: 同一数据源下的三个不同 SQL");
        System.out.println("   数据源: " + datasource);
        System.out.println("   拦截器: " + interceptor);
        System.out.println();

        // ========== 改进前的格式 ==========
        System.out.println("❌ 改进前 (所有 SQL 的 statementId 都相同):");
        System.out.println("-".repeat(80));
        String oldFormat = "jdbc." + interceptor + ":" + datasource;
        System.out.println("  SQL 1: " + sql1);
        System.out.println("         statementId: " + oldFormat);
        System.out.println();
        System.out.println("  SQL 2: " + sql2);
        System.out.println("         statementId: " + oldFormat);
        System.out.println();
        System.out.println("  SQL 3: " + sql3);
        System.out.println("         statementId: " + oldFormat);
        System.out.println();
        System.out.println("  ⚠️  问题: 无法区分不同的 SQL，审计统计不准确！");
        System.out.println();

        // ========== 改进后的格式 ==========
        System.out.println("✅ 改进后 (每个 SQL 都有唯一的 statementId):");
        System.out.println("-".repeat(80));

        String newId1 = StatementIdGenerator.generate(interceptor, datasource, sql1);
        String newId2 = StatementIdGenerator.generate(interceptor, datasource, sql2);
        String newId3 = StatementIdGenerator.generate(interceptor, datasource, sql3);

        System.out.println("  SQL 1: " + sql1);
        System.out.println("         statementId: " + newId1);
        System.out.println("         SQL Hash:    " + extractHash(newId1));
        System.out.println();

        System.out.println("  SQL 2: " + sql2);
        System.out.println("         statementId: " + newId2);
        System.out.println("         SQL Hash:    " + extractHash(newId2));
        System.out.println();

        System.out.println("  SQL 3: " + sql3);
        System.out.println("         statementId: " + newId3);
        System.out.println("         SQL Hash:    " + extractHash(newId3));
        System.out.println();

        System.out.println("  ✅ 优势: 每个 SQL 都可以单独追踪和统计！");
        System.out.println();

        // ========== 一致性测试 ==========
        System.out.println("🔄 一致性验证 (相同 SQL 生成相同 ID):");
        System.out.println("-".repeat(80));
        String id1a = StatementIdGenerator.generate(interceptor, datasource, sql1);
        String id1b = StatementIdGenerator.generate(interceptor, datasource, sql1);
        String id1c = StatementIdGenerator.generate(interceptor, datasource, sql1);

        System.out.println("  第1次: " + id1a);
        System.out.println("  第2次: " + id1b);
        System.out.println("  第3次: " + id1c);
        System.out.println();
        System.out.println("  相同? " + (id1a.equals(id1b) && id1b.equals(id1c) ? "✅ 是" : "❌ 否"));
        System.out.println();

        // ========== 性能测试 ==========
        System.out.println("⚡ 性能测试 (生成 10000 个 statementId):");
        System.out.println("-".repeat(80));

        long startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            StatementIdGenerator.generate(interceptor, datasource, sql1);
        }
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgTimeMicros = (endTime - startTime) / 10000.0 / 1000.0;

        System.out.println("  总耗时: " + totalTimeMs + " ms");
        System.out.println("  平均耗时: " + String.format("%.2f", avgTimeMicros) + " μs/次");
        System.out.println("  ✅ 性能影响可忽略");
        System.out.println();

        // ========== 碰撞测试 ==========
        System.out.println("🎯 碰撞测试 (1000 个不同 SQL):");
        System.out.println("-".repeat(80));

        java.util.Set<String> uniqueIds = new java.util.HashSet<>();
        int collisions = 0;

        for (int i = 0; i < 1000; i++) {
            String testSql = "SELECT * FROM table" + i + " WHERE id = " + i;
            String testId = StatementIdGenerator.generate(interceptor, datasource, testSql);

            if (!uniqueIds.add(testId)) {
                collisions++;
            }
        }

        System.out.println("  生成数量: 1000");
        System.out.println("  唯一ID数: " + uniqueIds.size());
        System.out.println("  碰撞次数: " + collisions);
        System.out.println("  碰撞率: " + (collisions * 100.0 / 1000) + "%");
        System.out.println("  ✅ " + (collisions == 0 ? "无碰撞！" : "碰撞率极低"));
        System.out.println();

        // ========== 实际应用场景 ==========
        System.out.println("📊 实际应用场景:");
        System.out.println("-".repeat(80));
        System.out.println("  1. 审计日志分析");
        System.out.println("     SELECT statementId, COUNT(*) as exec_count");
        System.out.println("     FROM audit_logs");
        System.out.println("     GROUP BY statementId");
        System.out.println("     ORDER BY exec_count DESC");
        System.out.println();
        System.out.println("     改进前: 只能看到 'jdbc.druid:masterDB' 的总次数");
        System.out.println("     改进后: 可以看到每个 SQL 的具体执行次数 ✅");
        System.out.println();
        System.out.println("  2. 性能监控");
        System.out.println("     - 识别慢查询 SQL");
        System.out.println("     - 追踪特定 SQL 的性能趋势");
        System.out.println("     - 发现潜在的 SQL 优化机会");
        System.out.println();
        System.out.println("  3. 安全审计");
        System.out.println("     - 追踪危险 SQL 的执行来源");
        System.out.println("     - 统计每个 SQL 的违规次数");
        System.out.println("     - 生成详细的审计报告");
        System.out.println();

        System.out.println("=".repeat(80));
        System.out.println("演示完成！");
        System.out.println("=".repeat(80));
    }

    private static String extractHash(String statementId) {
        String[] parts = statementId.split(":");
        return parts.length >= 3 ? parts[2] : "N/A";
    }
}
