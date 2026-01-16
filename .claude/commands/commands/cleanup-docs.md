---
description: Check, analyze, and archive temporary documents in docs/ directory
allowed-tools: Read, Glob, Grep, Bash, Edit, Write, Skill
argument-hint: [check|archive|suggest|status]
---

# Cleanup Documents Command

Manage the lifecycle of temporary documents in the `docs/` directory. This command helps identify, analyze, and archive completed or outdated temporary documents.

## Usage

```bash
/cleanup-docs [operation]
```

### Operations

| Operation | Description |
|-----------|-------------|
| `check` | List all temporary documents and their status |
| `archive` | Archive documents marked as complete |
| `suggest` | Get recommendations for cleanup actions |
| `status` | Show summary statistics of document states |
| _(no args)_ | Same as `check` |

## Implementation

When the user runs this command:

### Operation: check (default)

Scan `docs/` root directory for temporary documents and show their status.

**Steps:**

1. Find all `.md` files in `docs/` root (exclude subdirectories):
```bash
find docs/ -maxdepth 1 -type f -name "*.md"
```

2. For each file (excluding README.md and directory-standards.md):
   - Read document metadata
   - Check completion status
   - Calculate age (days since creation/modification)
   - Classify as: 🔄 In Progress, ✅ Completed, ⏰ Overdue (>30 days)

3. Generate report:

```
📊 文档清理检查报告

扫描位置: docs/
发现文档: X 个
扫描时间: YYYY-MM-DD HH:MM:SS

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📁 临时文档列表:

✅ 可归档 (已完成):
  1. migration-plan-api-v2.md
     └─ 状态: ✅ 已完成 | 年龄: 5天 | 分类: migration-records

🔄 进行中:
  2. todo-database-refactor.md
     └─ 状态: 🔄 进行中 | 年龄: 12天 | 分类: planning-archives

⏰ 超期未完成 (>30天):
  3. analysis-performance-baseline.md
     └─ 状态: 🔄 进行中 | 年龄: 45天 | 分类: analysis-reports
     └─ ⚠️ 建议: 更新状态或归档

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📈 统计汇总:
  • 总文档数: 3
  • 可归档: 1
  • 进行中: 1
  • 超期: 1

💡 建议操作:
  • 运行 /cleanup-docs archive 归档已完成文档
  • 运行 /cleanup-docs suggest 获取详细建议
```

### Operation: archive

Archive all documents marked as "✅ 已完成".

**Steps:**

1. Find completed documents (containing "状态.*✅.*已完成")

2. For each completed document:
   - Invoke `archive-completed-document` skill
   - Show progress for each file

3. Generate summary:

```
📦 文档归档执行

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

正在归档 2 个已完成文档...

1️⃣ migration-plan-api-v2.md
   ✅ 已归档到: 7-archive/migration-records/2025-11/

2️⃣ analysis-performance-baseline.md
   ✅ 已归档到: 7-archive/analysis-reports/2025-11/

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ 归档完成！

归档文档: 2 个
Git状态: 已添加到 staging area

建议提交命令:
git commit -m "docs: 归档已完成的临时文档 (2个)"
```

### Operation: suggest

Provide intelligent recommendations for each document.

**Steps:**

1. Scan all temporary documents
2. For each document, invoke `analyze-temp-document` skill (if not already analyzed)
3. Generate actionable recommendations

```
💡 文档清理建议

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📄 migration-plan-api-v2.md
   状态: ✅ 已完成 (5天前)
   建议:
   • 立即归档到 migration-records/
   • 命令: /cleanup-docs archive

📄 todo-database-refactor.md
   状态: 🔄 进行中 (12天)
   建议:
   • 继续保留，任务进行中
   • 建议添加预计完成时间
   • 每周更新进度

📄 analysis-performance-baseline.md
   状态: 🔄 进行中 (45天) ⚠️ 超期
   建议:
   • 选项1: 如果已完成，标记为 "✅ 已完成" 后归档
   • 选项2: 如果仍在进行，更新状态说明原因
   • 选项3: 如果不再需要，直接删除或归档到 misc/

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📌 总体建议:
  1. 归档 1 个已完成文档
  2. 更新 1 个超期文档状态
  3. 1 个文档状态正常，继续保留
```

### Operation: status

Show summary statistics and dashboard.

```
📊 文档管理状态面板

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📈 文档统计:
  总文档数: 42
  └─ 永久文档: 40 (1-specs, 2-requirements, etc.)
  └─ 临时文档: 2 (docs/ 根目录)

📁 临时文档分布:
  ✅ 已完成可归档: 1
  🔄 进行中: 1
  ⏰ 超期 (>30天): 0

📦 归档统计:
  migration-records: 5 个文档
  analysis-reports: 3 个文档
  planning-archives: 2 个文档
  early-designs: 12 个文档

🕐 最近归档:
  • migration-plan-api.md (2天前)
  • backend-docs-migration-plan.md (5天前)
  • document-merge-todo.md (5天前)

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅ 文档管理健康度: 良好
  • 无超期未处理文档
  • 临时文档数量: 2 (合理范围)
  • 归档流程: 正常运行
```

## Helper Functions

### Check Document Age

```bash
get_document_age() {
    local file=$1
    if [[ $(uname) == "Darwin" ]]; then
        file_time=$(stat -f %m "$file")
    else
        file_time=$(stat -c %Y "$file")
    fi
    current_time=$(date +%s)
    age_days=$(( (current_time - file_time) / 86400 ))
    echo "$age_days"
}
```

### Check Document Status

```bash
get_document_status() {
    local file=$1
    if grep -q "状态.*✅.*已完成" "$file" 2>/dev/null; then
        echo "completed"
    elif grep -q "状态.*🔄.*进行中" "$file" 2>/dev/null; then
        echo "in_progress"
    else
        echo "unknown"
    fi
}
```

### Classify Document

```bash
classify_document() {
    local filename=$(basename "$1")

    if [[ "$filename" =~ ^migration- ]] || [[ "$filename" =~ -migration- ]]; then
        echo "migration-records"
    elif [[ "$filename" =~ ^analysis- ]] || [[ "$filename" =~ -report\.md$ ]]; then
        echo "analysis-reports"
    elif [[ "$filename" =~ ^todo- ]] || [[ "$filename" =~ -plan\.md$ ]]; then
        echo "planning-archives"
    else
        echo "misc"
    fi
}
```

## Integration with Skills

This command orchestrates the two main skills:

- **analyze-temp-document**: Used in `suggest` operation
- **archive-completed-document**: Used in `archive` operation

## Examples

### Example 1: Check Status

```bash
User: /cleanup-docs
# or
User: /cleanup-docs check
```

Shows all temporary documents with their status.

### Example 2: Archive Completed

```bash
User: /cleanup-docs archive
```

Archives all documents marked as complete.

### Example 3: Get Suggestions

```bash
User: /cleanup-docs suggest
```

Provides intelligent recommendations for each document.

### Example 4: View Dashboard

```bash
User: /cleanup-docs status
```

Shows comprehensive statistics and health metrics.

## Notes

- Only scans `docs/` root directory (maxdepth 1)
- Excludes permanent documents (README.md, directory-standards.md)
- Non-destructive operations (check, suggest, status) are safe
- Archive operation moves files - always review before confirming
- All operations respect git workflow (add but don't auto-commit)
- Color coding: ✅ Complete, 🔄 In Progress, ⏰ Overdue

## Safety Features

- Preview before archiving
- Confirm destructive operations
- Detailed logging of all actions
- Git operations are explicit and reversible
- Documents are moved, never deleted
