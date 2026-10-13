# Agent Prompts — Copy/Paste Prompts for Antigravity

## Prompt 1 — Understand Before Coding

Read AGENTS.md and all project documentation. Inspect the repository. Do not modify code. Explain the current state, architecture, completed tasks, incomplete tasks, risks, and the single best next task for this project.

## Prompt 2 — Project Setup

Implement only the development environment setup described in TASKS.md. Create the minimal Flask application, virtual environment instructions, requirements file, `.gitignore`, `.env.example`, and README setup instructions. Do not implement business features. Run the application and verify it starts.

## Prompt 3 — Database Design

Read PRD.md and ARCHITECTURE.md. Design the minimum relational database required for the MVP. Explain the tables and relationships before implementation. Then create schema and seed data. Do not add unnecessary tables. Test that the database can be initialized successfully.

## Prompt 4 — Authentication

Implement only authentication. Include password hashing, login, logout, protected routes, and role authorization if roles are already approved. Add tests for valid login, invalid login, logout, and unauthorized access.

## Prompt 5 — Product Management

Implement product/category management according to the PRD. Include create, read, update, search, validation, and deactivation. Do not implement POS yet. Test both valid and invalid inputs.

## Prompt 6 — Inventory

Implement inventory updates, inventory transactions, low-stock detection, and negative-stock prevention. Do not implement sales yet. Verify database values after each test.

## Prompt 7 — Customer Management

Implement customer creation, editing, searching, and viewing. Keep personal data minimal. Add validation and tests.

## Prompt 8 — POS

Implement the minimum POS workflow:

product search → cart → quantity validation → subtotal → total → sale → sale items → stock deduction.

Use a database transaction so the sale and stock update succeed together or fail together.

Do not integrate real payment services.

## Prompt 9 — Reports

Implement basic sales and inventory reports. Verify report values against database records. Keep reporting simple and useful for the college demo.

## Prompt 10 — UI Polish

Review the existing UI. Improve layout, navigation, forms, tables, error messages, loading states, empty states, and responsive behavior. Do not change business logic unless required to fix a bug.

## Prompt 11 — Testing

Run the complete TEST_PLAN.md. Report every pass and failure. Fix only actual defects found during testing. Do not add unrelated features.

## Prompt 12 — Final Audit

Audit the project against PRD.md, AGENTS.md, TEST_PLAN.md and DEMO_CHECKLIST.md. Identify:

- Missing requirements
- Bugs
- Security weaknesses
- Database issues
- Validation problems
- Poor error handling
- Documentation gaps
- Demo risks

Do not make large changes automatically. Present the findings first.

## Prompt 13 — Explain the Project to Me

I am a beginner. Explain the current project architecture as if teaching me for a college viva. Explain:

- Frontend
- Backend
- API
- Database
- Tables
- Authentication
- POS workflow
- Inventory update
- Testing

Use examples from this project.

## Prompt 14 — Viva Preparation

Generate likely viva questions and beginner-friendly answers based only on the actual implementation and PRD. Do not invent technologies that the project does not use.
