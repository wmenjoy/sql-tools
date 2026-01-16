---
description: Create a new document following project standards with proper templates, metadata, and directory placement
allowed-tools: Read, Write, AskUserQuestion, Skill
argument-hint: [type]
---

# Create New Standard Document

Interactive command to create well-structured documents that comply with the project's seven-layer documentation architecture.

## Usage

```bash
/new-doc [type]
```

### Document Types

| Type | Description | Layer | Example |
|------|-------------|-------|---------|
| `spec` | Technical specification | 1-specs | API docs, DB schema |
| `prd` | Product requirements | 2-requirements | Feature PRD |
| `story` | User story | 2-requirements | User stories |
| `guide` | User/dev guide | 3-guides | Tutorials, how-tos |
| `plan` | Planning document | 4-planning | Roadmap, sprint plan |
| `wiki` | Business knowledge | 5-wiki | Domain knowledge |
| `adr` | Architecture decision | 6-decisions | Design decisions |
| `temp` | Temporary analysis | docs/ | Analysis, migration plan |
| _(no args)_ | Interactive mode | - | Ask questions to determine type |

## Implementation

When the user runs this command:

### Mode 1: With Type Argument

If user specifies type (e.g., `/new-doc spec`):

1. **Confirm type**: "You want to create a [type] document. Is this correct?"

2. **Invoke write-standard-document skill**: Use the Skill tool to call `write-standard-document`

3. **Skill handles**:
   - Asking clarifying questions
   - Selecting appropriate template
   - Suggesting file name and location
   - Creating the document
   - Providing next steps

### Mode 2: Interactive (No Arguments)

If user runs `/new-doc` without arguments:

1. **Welcome message**:
```
📝 Document Creation Wizard

I'll help you create a standard document. Let me ask a few questions to determine the best format.

What would you like to document?

Examples:
- "A new API endpoint"
- "User guide for login feature"
- "Database schema changes"
- "Migration plan for the new service"
```

2. **Listen to user response**

3. **Analyze keywords** to determine document type:
   - Keywords like "API", "endpoint", "schema", "interface" → spec
   - Keywords like "requirement", "feature", "PRD", "user story" → prd/story
   - Keywords like "guide", "tutorial", "how to", "instructions" → guide
   - Keywords like "plan", "roadmap", "sprint", "backlog" → plan
   - Keywords like "business", "domain", "how it works", "mechanism" → wiki
   - Keywords like "decision", "architecture", "why we chose" → adr
   - Keywords like "analysis", "migration", "todo", "temporary" → temp

4. **Confirm understanding**:
```
Based on your description, this sounds like a [type] document.

[Brief explanation of what this type is for]

Is this correct? (y/n)
```

5. **Invoke skill**: Use Skill tool to call `write-standard-document`

### Common Workflows

#### Workflow: Create API Documentation

```
User: /new-doc spec

Claude:
> You want to create a technical specification. What component or system are you documenting?

User: Authentication API

Claude:
> [Uses write-standard-document skill]
>
> Creating API specification for Authentication API...
>
> Suggested location: docs/1-specs/api/authentication.md
>
> This document will include:
> - API endpoints
> - Request/response formats
> - Authentication flows
> - Error codes
>
> Should I create this document? (y/n)

User: y

Claude:
> ✅ Created: docs/1-specs/api/authentication.md
>
> Next steps:
> 1. Fill in API endpoints (Section 3)
> 2. Add authentication flow diagrams (Section 4)
> 3. Define error codes (Section 6)
```

#### Workflow: Create User Guide

```
User: /new-doc guide

Claude:
> Creating a user or developer guide.
>
> Questions:
> 1. Who is the audience? (users/developers/testers)
> 2. What task are you teaching?

User: Developers, how to set up the local environment

Claude:
> [Uses write-standard-document skill]
>
> Creating developer guide for local environment setup...
>
> Suggested location: docs/3-guides/development/local-setup.md
>
> This guide will include:
> - Prerequisites checklist
> - Step-by-step setup instructions
> - Common troubleshooting
> - Next steps
>
> Should I create this document? (y/n)
```

#### Workflow: Create Architecture Decision Record

```
User: /new-doc adr

Claude:
> Creating an Architecture Decision Record (ADR).
>
> What decision are you documenting?

User: We decided to use PostgreSQL instead of MongoDB

Claude:
> [Uses write-standard-document skill]
>
> Creating ADR for database selection decision...
>
> I need the next ADR number. Let me check existing ADRs...
> [Checks docs/6-decisions/adr/ directory]
>
> Next ADR number: 003
>
> Suggested location: docs/6-decisions/adr/adr-003-use-postgresql.md
>
> This ADR will include:
> - Decision context and background
> - Alternatives considered (MongoDB, MySQL, etc.)
> - Pros/cons comparison
> - Final decision rationale
> - Consequences and risks
>
> Should I create this document? (y/n)
```

#### Workflow: Interactive Mode

```
User: /new-doc

Claude:
> 📝 Document Creation Wizard
>
> What would you like to document?

User: I need to write about how our test workflow engine works

Claude:
> This sounds like business/domain knowledge documentation - perfect for the Wiki layer!
>
> The Wiki layer (docs/5-wiki/) is for explaining:
> - Business logic and rules
> - How systems work internally
> - Domain concepts and mechanisms
>
> Is this correct? (y/n)

User: y

Claude:
> [Uses write-standard-document skill]
>
> Creating wiki documentation for workflow engine...
>
> Suggested location: docs/5-wiki/workflow/engine-mechanics.md
>
> This wiki document will include:
> - Business background
> - Core concepts
> - How the engine works
> - Common scenarios
> - FAQ section
>
> Should I create this document? (y/n)
```

## Helper Functions

### Determine Document Type from Keywords

```javascript
function guessDocumentType(userInput) {
  const input = userInput.toLowerCase();

  if (input.match(/\b(api|endpoint|interface|schema|database|protocol)\b/)) {
    return 'spec';
  }

  if (input.match(/\b(requirement|feature|prd|epic|user story)\b/)) {
    return 'prd';
  }

  if (input.match(/\b(guide|tutorial|how to|instructions|setup|install)\b/)) {
    return 'guide';
  }

  if (input.match(/\b(plan|roadmap|sprint|backlog|milestone)\b/)) {
    return 'plan';
  }

  if (input.match(/\b(business|domain|how .* works|mechanism|logic)\b/)) {
    return 'wiki';
  }

  if (input.match(/\b(decision|adr|architecture|why we|chose|selected)\b/)) {
    return 'adr';
  }

  if (input.match(/\b(analysis|migration|temporary|todo|temp)\b/)) {
    return 'temp';
  }

  return 'unknown'; // Ask more questions
}
```

### Get Next ADR Number

```bash
# Check existing ADRs and suggest next number
existing_adrs=$(find docs/6-decisions/adr -name "adr-*.md" | wc -l)
next_number=$(printf "%03d" $((existing_adrs + 1)))
echo "$next_number"
```

## Output Format

### Success Message

```
✅ Document Created Successfully!

📄 File: docs/[layer]/[subdirectory]/[filename].md
📁 Layer: [Layer Name] ([layer number])
📋 Type: [Document Type]
📏 Template: [Template Used]

Document structure:
- ✅ Metadata header (version, date, maintainer)
- ✅ Main sections with placeholders
- ✅ Cross-reference section
- ✅ Examples and best practices

Next steps:
1. Open the file and fill in [specific sections]
2. Replace [ALL CAPS PLACEHOLDERS] with actual content
3. Add diagrams/code examples where marked
4. Review metadata at the top
5. Run `git add [filepath]` when ready

Helpful commands:
- git status          # Check staging area
- /cleanup-docs check # View document status (for temp docs)

Happy documenting! 📝
```

### Error Handling

**Error: Cannot determine document type**
```
❓ I'm not sure what type of document you need.

Let me ask more specifically:

[Uses AskUserQuestion to present document type options]
```

**Error: File already exists**
```
⚠️  File already exists: docs/[path]/[filename].md

Options:
1. Choose a different name
2. Open and update the existing file
3. Create a versioned copy (e.g., filename-v2.md)

What would you like to do?
```

## Integration with Other Tools

- **After creation**: `analyze-temp-document` skill can classify temporary docs
- **Quality check**: User can run `/cleanup-docs check` to verify
- **Archiving**: Temp docs can be archived with `/cleanup-docs archive`

## Best Practices Enforced

✅ **Always use templates** - Ensures consistency
✅ **Include metadata** - Version, date, maintainer
✅ **Correct placement** - Right layer and subdirectory
✅ **Follow naming** - Lowercase with hyphens
✅ **Add cross-refs** - Link to related docs
✅ **User confirmation** - Never create without asking

## Examples

### Quick Creation

```bash
/new-doc spec        # Create technical spec
/new-doc prd         # Create PRD
/new-doc guide       # Create user/dev guide
/new-doc adr         # Create ADR
```

### Interactive Mode

```bash
/new-doc             # Wizard mode - ask questions
```

## Notes

- This command always invokes the `write-standard-document` skill
- The skill handles all the heavy lifting (templates, questions, validation)
- This command provides a user-friendly entry point
- All document types follow project standards in `docs/directory-standards.md`
- Templates are comprehensive but can be customized after creation

---

**Pro Tip**: If you're unsure which type to choose, run `/new-doc` without arguments and I'll help you decide through interactive questions.
