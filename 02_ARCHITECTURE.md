# Architecture — Smart Grocery Store Management System

## Recommended Architecture

Use a simple 3-layer web architecture.

```text
Browser
  |
  | HTTP / JSON
  v
Flask Application
  |
  | SQL
  v
MySQL Database
```

## Frontend

HTML/CSS/JavaScript.

Responsibilities:

- Display pages
- Collect user input
- Send requests
- Display API responses
- Client-side convenience validation

The frontend must NOT be trusted for business rules.

## Backend

Python + Flask.

Responsibilities:

- Authentication
- Authorization
- Business logic
- Validation
- Database access
- Calculations
- API endpoints
- Error handling

## Database

MySQL.

Initial relational design:

```text
users
  |
  +---- roles

categories
  |
  +---- products
           |
           +---- inventory_transactions

customers
  |
  +---- sales
           |
           +---- sale_items
                    |
                    +---- products
```

## Suggested Folder Structure

```text
smart-grocery-store/
├── app/
│   ├── __init__.py
│   ├── routes/
│   ├── services/
│   ├── models/
│   ├── repositories/
│   ├── templates/
│   └── static/
│       ├── css/
│       └── js/
├── tests/
├── database/
│   ├── schema.sql
│   └── seed.sql
├── docs/
│   ├── PRD.md
│   ├── ARCHITECTURE.md
│   ├── PROJECT_MEMORY.md
│   ├── DECISIONS.md
│   ├── TEST_PLAN.md
│   └── DEMO_CHECKLIST.md
├── .env.example
├── .gitignore
├── requirements.txt
├── run.py
└── README.md
```

The exact structure may be adjusted by the implementation agent after inspection, but it must document any deviation.

## Engineering Principles

- Keep the MVP modular but not over-engineered.
- Prefer simple functions/classes over unnecessary abstractions.
- Validate on both client and server.
- Use parameterized queries/ORM safely.
- Use transactions for multi-step operations.
- Keep secrets in environment variables.
- Write tests for important business logic.
- Never make an unreviewed large rewrite.

## API Naming

Use consistent REST-style routes, for example:

```text
POST   /api/auth/login
POST   /api/auth/logout

GET    /api/products
POST   /api/products
GET    /api/products/<id>
PUT    /api/products/<id>

GET    /api/customers
POST   /api/customers

POST   /api/sales
GET    /api/sales
GET    /api/sales/<id>

GET    /api/reports/sales
GET    /api/reports/inventory
```

The agent may change exact route names if there is a good reason, but must record the decision.

## Do Not Add Yet

Do not introduce:

- Microservices
- Kubernetes
- Complex cloud architecture
- Machine learning
- Real payment gateways
- Mobile applications
- Real barcode hardware dependencies

unless the user explicitly approves them.
