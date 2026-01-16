---
name: archive-completed-document
description: Automatically archive temporary documents marked as completed by moving them to appropriate archive directories and updating archive records
allowed-tools: Read, Bash, Edit, Write
version: "1.0"
---

# Archive Completed Document

When a temporary document is marked as "✅ 已完成" (completed), automatically archive it to the appropriate category in `docs/7-archive/`.

## When to Use This Skill

Claude should invoke this skill when:
- User marks a document status as "✅ 已完成" or "completed"
- User explicitly requests archiving a document
- User asks "should I archive this?"
- A document has been completed for 7+ days

## Archive Categories

Based on document filename and content, classify into:

| Pattern | Archive Location | Examples |
|---------|------------------|----------|
| `migration-*`, `*-migration-*` | `7-archive/migration-records/YYYY-MM/` | migration-plan-api.md |
| `analysis-*`, `*-analysis-*`, `*-report.md` | `7-archive/analysis-reports/YYYY-MM/` | analysis-database-performance.md |
| `todo-*`, `*-plan.md`, `*-todo.md` | `7-archive/planning-archives/YYYY-MM/` | todo-refactor-auth.md |
| `compliance-*`, `*-compliance.md` | `7-archive/compliance-records/YYYY-MM/` | compliance-gdpr-check.md |
| Others | `7-archive/misc/YYYY-MM/` | temp-notes.md |

## Task Steps

### Step 1: Verify Document Status

Read the document and confirm it contains:
- "状态.*✅.*已完成" OR "Status.*✅.*Complete" OR "状态: ✅ 已完成"

If not found, warn user and ask for confirmation before archiving.

### Step 2: Determine Archive Category

Analyze filename and content to determine the best archive category:

```bash
filename=$(basename "$FILE_PATH")

if [[ "$filename" =~ ^migration- ]] || [[ "$filename" =~ -migration- ]]; then
    category="migration-records"
elif [[ "$filename" =~ ^analysis- ]] || [[ "$filename" =~ -report\.md$ ]]; then
    category="analysis-reports"
elif [[ "$filename" =~ ^todo- ]] || [[ "$filename" =~ -plan\.md$ ]]; then
    category="planning-archives"
elif [[ "$filename" =~ ^compliance- ]]; then
    category="compliance-records"
else
    category="misc"
fi
```

### Step 3: Create Archive Directory

```bash
current_month=$(date +%Y-%m)
archive_dir="docs/7-archive/$category/$current_month"
mkdir -p "$archive_dir"
```

### Step 4: Move Document

```bash
mv "$FILE_PATH" "$archive_dir/"
```

### Step 5: Update Archive README

Read `docs/7-archive/$category/README.md` and add entry:

```markdown
### YYYY-MM/filename.md

- **归档时间**: YYYY-MM-DD
- **用途**: [Brief description from document]
- **状态**: ✅ 已完成
- **结果**: [Key outcome if mentioned in document]
```

If README doesn't exist, create it with standard format.

### Step 6: Git Add

```bash
git add "$archive_dir"
git add "docs/7-archive/$category/README.md"
```

### Step 7: Report to User

Provide summary:

```
✅ 文档已归档

原文件: docs/filename.md
归档位置: docs/7-archive/$category/YYYY-MM/
归档时间: YYYY-MM-DD HH:MM:SS

归档记录已添加到:
- docs/7-archive/$category/README.md

Git状态:
- 文件已添加到 staging area
- 建议运行: git commit -m "docs: 归档 filename.md"

下一步建议:
- 检查 git status 确认变更
- 提交归档操作
```

## Example Execution

### Input: Document Marked Complete

User edits `docs/migration-plan-api-v2.md` and changes status to "✅ 已完成".

### Skill Execution

```
📦 开始归档流程...

1️⃣ 验证文档状态
   ✅ 文档已标记为完成 (状态: ✅ 已完成)

2️⃣ 确定归档分类
   📁 分类: migration-records
   📌 原因: 文件名包含 "migration" 和 "plan"

3️⃣ 创建归档目录
   ✅ docs/7-archive/migration-records/2025-11/

4️⃣ 移动文档
   ✅ docs/migration-plan-api-v2.md
      → docs/7-archive/migration-records/2025-11/

5️⃣ 更新归档记录
   ✅ 已添加条目到 README.md

6️⃣ Git添加
   ✅ git add docs/7-archive/migration-records/2025-11/
   ✅ git add docs/7-archive/migration-records/README.md

✅ 归档完成！

归档位置: docs/7-archive/migration-records/2025-11/migration-plan-api-v2.md
归档时间: 2025-11-26 14:30:00

建议提交命令:
git commit -m "docs: 归档API v2迁移计划"
```

## Archive README Template

When creating new category README:

```markdown
# [Category Name] 归档

**归档类别**: [category]
**创建时间**: YYYY-MM-DD

---

## 📋 归档文件列表

### YYYY-MM/

#### filename.md

- **归档时间**: YYYY-MM-DD
- **用途**: Document purpose
- **状态**: ✅ 已完成
- **结果**: Key outcomes

---

**维护者**: 文档管理系统
**最后更新**: YYYY-MM-DD
```

## Safety Checks

Before archiving:

1. ✅ Confirm document has completion status marker
2. ✅ Check document is in `docs/` root (not subdirectories)
3. ✅ Verify archive directory exists or can be created
4. ✅ Check for name conflicts in archive destination
5. ✅ Ensure document type is temporary (has "临时文档" marker)

If any check fails, warn user and ask for confirmation.

## Error Handling

| Error | Resolution |
|-------|------------|
| Document not marked complete | Warn user, offer to add completion marker |
| File already exists in archive | Suggest rename with timestamp suffix |
| Cannot determine category | Use `misc/` category and log warning |
| Git add fails | Report error, provide manual git commands |

## Integration with Other Skills

- **Preceded by**: `analyze-temp-document` (which adds metadata)
- **Triggers**: User editing document to mark complete, or explicit archive request
- **Followed by**: Optional git commit (suggest but don't auto-commit)

## Notes

- This skill is DESTRUCTIVE (moves files) - always confirm before executing
- Preserves file content and metadata during move
- Updates archive catalog for discoverability
- Git operations are additive only (no auto-commit)
- Archive destination is organized by month for easy browsing
