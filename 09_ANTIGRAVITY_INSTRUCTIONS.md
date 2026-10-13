# Google Antigravity Beginner Workflow

## 1. Install

Use the official Google Antigravity download page:

https://antigravity.google/download

For Windows, the official documentation currently lists Windows 10 64-bit as supported. Antigravity projects define the folders/repositories an agent can access, so create a dedicated project folder for this application.

## 2. Create the Project Folder

Recommended:

```text
Documents/
└── Projects/
    └── smart-grocery-store/
```

Do not put the project inside Downloads or a temporary folder.

## 3. Open as an Antigravity Project

Create a project and add the `smart-grocery-store` folder.

## 4. Put Project Memory in the Repository

Keep these files in the project:

```text
AGENTS.md
docs/
  PRD.md
  ARCHITECTURE.md
  PROJECT_MEMORY.md
  DECISIONS.md
  TASKS.md
  TEST_PLAN.md
  DEMO_CHECKLIST.md
```

The purpose is to give agents stable context instead of repeating the entire project description every time.

## 5. Never Start With "Build Everything"

Bad:

> Build my whole grocery store system.

Better:

> Read AGENTS.md, PRD.md, ARCHITECTURE.md, PROJECT_MEMORY.md and TASKS.md. Inspect the repository. Do not code yet. Tell me the smallest next implementation step and what files you expect to change.

## 6. Use One Milestone at a Time

Recommended order:

```text
Setup
→ Database
→ Authentication
→ Products
→ Inventory
→ Customers
→ POS
→ Sales
→ Reports
→ UI polish
→ Testing
→ Final demo
```

## 7. Require Verification

After every meaningful feature:

> Implement only this task. Then run the relevant tests and verify the workflow. Report exactly what changed, what was tested, what failed, and the next recommended task.

## 8. Ask the Agent to Explain

Because this is a learning project:

> Explain the important code you created in beginner-friendly language. Tell me which files contain the main logic and what I should understand before moving to the next phase.

## 9. Use Agent Artifacts as Evidence

Keep useful plans, diagrams, test results, and implementation notes generated during development. Antigravity is designed around agents working across editor, terminal, browser, and artifacts, so use those capabilities for verification rather than blindly accepting generated code.

## 10. Memory Trick

After a major milestone, ask:

> Update PROJECT_MEMORY.md, TASKS.md and DECISIONS.md so another agent can continue this project without needing our previous chat.

## 11. Context Reset Trick

If the agent becomes confused:

> Stop. Re-read AGENTS.md, PRD.md, ARCHITECTURE.md, PROJECT_MEMORY.md, DECISIONS.md and TASKS.md. Summarize the current project state before making any changes.

## 12. Prevent Overengineering

Use:

> Do not introduce new frameworks, services, libraries, architectural patterns, or infrastructure unless necessary for this task. If you think one is necessary, explain why and ask for approval.

## 13. Database Safety

Use:

> Before any destructive database command, stop and ask for confirmation. Never drop or reset project data automatically.

## 14. Final Review Prompt

Before submission:

> Perform a final engineering review of the Smart Grocery Store Management System against PRD.md. Do not rewrite the project. Identify missing requirements, bugs, security issues, broken workflows, weak validation, documentation gaps, and demo risks. Fix only issues that I explicitly approve, then run the complete test plan.
