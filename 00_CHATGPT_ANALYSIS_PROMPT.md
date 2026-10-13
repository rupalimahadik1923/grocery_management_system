# Prompt to Use in ChatGPT After Attaching the Project Files

Analyze the attached project file(s) carefully. This is my approved college project idea, and I want to turn it into a real, working, demonstrable software project.

My project idea is approved by my faculty guide. Do not replace the original idea with a different project. Preserve the terminology, objectives, features, scope, and future enhancements from the attached material.

## What I want you to do

1. Analyze the complete attached project material.
2. Identify:
   - Project title
   - Problem statement
   - Objectives
   - Users/roles
   - Functional requirements
   - Non-functional requirements
   - Main modules
   - Data/entities
   - Important workflows
   - Existing diagrams/features
   - Future enhancements
   - Any missing or unclear requirements
3. If something is unclear or contradictory, ask me specific questions before making major architectural decisions.
4. Separate:
   - What is explicitly present in my project file
   - What you recommend as an improvement
   - What you are assuming because the project file does not specify it
5. Recommend a realistic technology stack for a beginner who has little/no project-development experience.
6. Prefer a stack that is:
   - Simple to learn
   - Free/open source
   - Easy to run locally
   - Suitable for a college demonstration
   - Professional enough for a portfolio
   - Easy for an AI coding agent to maintain
7. Give me a phased software-development roadmap from requirements to final demonstration.
8. For every phase, explain:
   - Goal
   - What we build
   - What I need to learn
   - What the AI agent can do
   - What I personally need to understand
   - How we test it
   - What evidence/screenshots/documentation I should keep
9. I am a beginner. Do not assume that I know Git, APIs, databases, backend/frontend architecture, authentication, deployment, testing, or debugging.
10. Guide me like a mentor, not like an autonomous code generator.
11. Do not build the whole application in one giant prompt.
12. Break the work into small, verifiable milestones.
13. After analysis, create/recommend these project-control documents:
   - PRD.md
   - ARCHITECTURE.md
   - AGENTS.md
   - PROJECT_MEMORY.md
   - DECISIONS.md
   - TASKS.md
   - TEST_PLAN.md
   - DEMO_CHECKLIST.md
   - AGENT_PROMPTS.md
14. Make the prompts suitable for Google Antigravity's agent workflow.
15. Include instructions that agents must inspect existing files before changing them, make small changes, run tests, report exactly what changed, and never silently change requirements.
16. Include a rule that if an agent is uncertain, it must stop and ask instead of inventing requirements.
17. Include a rule that the agent must keep the project documentation synchronized with implementation.
18. Include a beginner-friendly explanation of how I should use Antigravity step by step.

## Important

The attached project is a college project approved by my guide. Improve its implementation quality without changing its approved core idea.

My current preferred implementation stack, unless the analysis finds a strong reason otherwise, is:

- Frontend: HTML5, CSS3, JavaScript
- Backend: Python + Flask
- Database: MySQL
- API style: REST
- Version control: Git + GitHub
- Development environment: Google Antigravity

Do not add AI/ML, mobile apps, microservices, cloud deployment, payment gateways, barcode hardware, or other advanced features to the MVP unless they are clearly necessary. They can remain future enhancements.

At the end, tell me the first exact task I should give the Antigravity agent.
