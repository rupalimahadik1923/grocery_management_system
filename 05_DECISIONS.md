# DECISIONS.md

Record important technical/product decisions here.

## D001 — MVP Stack

**Decision:** Python + Flask + MySQL + HTML/CSS/JavaScript.

**Reason:** The approved material allows Python/Java and specifies HTML/CSS/JavaScript/MySQL. Flask gives a smaller learning curve for a beginner.

**Status:** Proposed.

## D002 — No Microservices

**Decision:** Use one modular Flask application.

**Reason:** Microservices add complexity without improving the college MVP.

**Status:** Accepted.

## D003 — No Real Payment Gateway

**Decision:** MVP will simulate/record payment status rather than integrate real financial payment services.

**Reason:** It is unnecessary for demonstrating the store workflow and introduces security/credential complexity.

**Status:** Accepted.

## D004 — No Real Barcode Hardware

**Decision:** Use product search or a simulated barcode field for MVP.

**Reason:** The project can demonstrate POS functionality without requiring hardware.

**Status:** Accepted.

## D005 — Future AI Features

**Decision:** AI recommendations and demand forecasting remain future enhancements.

**Reason:** They are listed as future enhancements in the approved project and should not delay the core system.

**Status:** Accepted.

## Decision Template

### DXXX — Title

Decision:

Reason:

Alternatives considered:

Impact:

Status:
