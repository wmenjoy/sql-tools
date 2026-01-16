---
name: analyze-temp-document
description: Automatically analyze newly created documents in docs/ to classify them as temporary or permanent, and add appropriate metadata markers
allowed-tools: Read, Edit, Write
version: "1.0"
---

# Analyze Temporary Document

When a new markdown file is created in the `docs/` root directory, automatically analyze it to determine if it's a temporary or permanent document, then add appropriate metadata markers.

## When to Use This Skill

Claude should invoke this skill when:
- A new `.md` file is created in `docs/` root directory (not subdirectories)
- User asks about document classification
- User creates a document with temporary-sounding names

## Classification Rules

### Temporary Document Indicators

**Filename patterns:**
- Starts with: `temp-`, `todo-`, `analysis-`, `migration-`, `compliance-`
- Ends with: `-plan.md`, `-report.md`, `-todo.md`, `-analysis.md`

**Content patterns:**
- Contains: "临时", "临时文档", "TODO:", "待办", "待办清单", "完成时间", "Temporary", "TEMP"
- Has explicit completion dates
- Has status tracking markers

### Permanent Document Indicators

**Filename patterns:**
- `README.md`, `directory-standards.md`
- Matches layer directory names: `*-specs.md`, `*-requirements.md`, `*-guides.md`

**Content patterns:**
- Contains: "版本", "规范", "标准", "Standard", "Specification"
- No completion time markers
- Has version numbers in header

## Task Steps

### Step 1: Read Document

Read the document content (first 100 lines are sufficient for classification).

### Step 2: Analyze Filename

Check filename against temporary and permanent patterns.

### Step 3: Analyze Content

Scan content for classification keywords and patterns.

### Step 4: Classify Document

Based on analysis, classify as:
- **Temporary**: Will be archived after completion or 30 days
- **Permanent**: Long-term documentation, stays in place

### Step 5: Add Metadata Markers

If classified as **temporary**, add to document header (if not already present):

```markdown
**文档类型**: 临时文档
**清理规则**: 完成后归档到 `docs/7-archive/`
**状态**: 🔄 进行中
**创建时间**: YYYY-MM-DD
**预计完成**: YYYY-MM-DD
```

If classified as **permanent**, add to document header (if not already present):

```markdown
**文档类型**: 永久文档
**维护周期**: 长期维护
```

### Step 6: Provide Recommendations

Suggest to the user:

**For temporary documents:**
- Recommend using standard naming prefix (e.g., `temp-`, `todo-`, `analysis-`)
- Suggest setting expected completion date
- Explain that it will be auto-archived to appropriate category when marked complete

**For permanent documents:**
- Recommend moving to appropriate layer directory (1-specs, 2-requirements, etc.)
- Explain why it should be in a specific layer based on content

## Example Output

### For Temporary Document

```
📋 Document Analysis: api-migration-plan.md

Classification: ✅ Temporary Document

Reasoning:
- Filename contains "migration" and "plan"
- Content includes completion timeline
- Contains TODO items

Actions Taken:
- Added metadata markers to document header
- Status: 🔄 进行中
- Retention: 30 days or until marked complete

Recommendations:
1. Rename to: migration-plan-api.md (follows naming standard)
2. Update completion date when known
3. Mark as "✅ 已完成" when done (will trigger auto-archive)

Archive Destination (when complete):
docs/7-archive/migration-records/YYYY-MM/
```

### For Permanent Document

```
📋 Document Analysis: api-design-patterns.md

Classification: ✅ Permanent Document

Reasoning:
- Contains design patterns and standards
- No completion timeline
- Meant for long-term reference

Recommendations:
1. Move to: docs/1-specs/backend/api-design-patterns.md
2. Add version number to header
3. Consider adding to table of contents

This document should remain in the codebase permanently.
```

## Rules Reference

This skill implements document classification rules equivalent to:

```yaml
# Temporary document by filename
temp-doc-by-filename:
  pattern: ^(temp-|todo-|analysis-|migration-|compliance-|.*-(plan|report|todo))\.md$
  action: classification=temporary, retention_days=30

# Permanent document by filename
permanent-doc-by-filename:
  pattern: ^(README|directory-standards)\.md$
  action: classification=permanent, retention=forever

# Temporary by content
temp-doc-by-content:
  contains: ["临时文档", "TODO:", "待办清单", "完成时间"]
  action: classification=temporary
```

## Notes

- This skill should be invoked REACTIVELY (when documents are created)
- It's non-destructive - only adds metadata, doesn't move files
- User can override classification by editing metadata
- Works in conjunction with `archive-completed-document` skill
