# Multi‑Restaurant Platform

A **modular, container‑ready Spring Boot 3 (Java 21) backend** designed to power multi‑restaurant ordering, management, and (eventually) delivery experiences.

> **Why this repo exists** The author rebuilt the project *from scratch* after an earlier “big‑bang” attempt generated too many errors to untangle.  Everything here is meant to be grown **one small, reviewed step at a time**—you’ll see that philosophy reflected throughout this README.

---

## 1  Features (current & planned)

| Status | Domain                 | Highlights                                                                          |
| ------ | ---------------------- | ----------------------------------------------------------------------------------- |
|  ✅     | **Authentication**     | JWT‑based login / registration, role model (ADMIN, RESTAURANT\_ADMIN, CUSTOMER)     |
|  ✅     | **Restaurant & Menu**  | CRUD for restaurants, categories, items (with media & options)                      |
|  ✅    | **Order Flow**         | Cart → order → status timeline (PLACED → CONFIRMED → PREPARING → READY → DELIVERED) |
|  🟡    | **WebSocket Printing** | Real‑time receipt / KDS ticket streaming to browser printers                        |
|  🟡    | **Payments**           | Stripe integration stub (test keys) with webhook listener                           |
|  🔲    | **Admin Dashboard**    | CMS pages, global config, analytics                                                 |

Legend: ✅ finished · 🟡 in progress · 🔲 not started

---

## 2  Tech Stack

| Layer       | Choice                                   | Notes                                                      |
| ----------- | ---------------------------------------- | ---------------------------------------------------------- |
| Language    | **Java 21**                              | Modern features (records, sealed classes, virtual threads) |
| Framework   | **Spring Boot 3.2.x**                    | Modular (each sub‑module is its own Spring Boot project)   |
| Build Tool  | **Gradle 8.x (Kotlin DSL)**              | Central version catalog + sub‑projects                     |
| Persistence | **PostgreSQL 14** (prod), **H2** (tests) | Managed via Flyway migrations                              |
| Security    | **Spring Security 6** + **jjwt 0.12.5**  | Stateless, header‑only tokens                              |
| Container   | **Docker** + **Docker Compose**          | Dev stack & CI images                                      |
| Testing     | JUnit 5, Testcontainers, Mockito         | Each module has its own test suite                         |

---

## 3  Project Structure

```
multi-restaurant-platform/
├── backend/                  # <root Gradle project>
│   ├── build.gradle.kts      # common build logic & dependencies
│   ├── api/                  # REST controllers, DTOs, global config
│   ├── security/             # auth / users / roles / JWT utils
│   ├── restaurant/           # restaurant aggregate
│   ├── menu/                 # categories, items, options
│   ├── order/                # order domain & workflow engine
│   ├── payment/              # Stripe client & webhook listener
│   ├── print/                # WebSocket print service (WIP)
│   ├── admin/                # CMS & system settings (planned)
│   └── common/               # shared base entities, exceptions, utils
├── docker/                   # Dockerfiles, compose files
│   ├── Dockerfile            # prod image (multi‑stage Gradle → JRE)
│   ├── Dockerfile.dev        # hot‑reload dev image
│   └── docker-compose.dev.yml
└── README.md                 # you are here
```

---

## 4  Development Philosophy

1. **Tiny Commits, Clear Diffs** – Each feature lives in its own branch & PR.
2. **Ask Before You Leap** – The workflow is interactive: *write ➜ run ➜ review ➜ ask* ; the AI assistant will always pause before generating lots of boilerplate.
3. **Containers First** – Everything should run with a single `docker‑compose up` on any machine.
4. **No Secrets in Git** – JWT secrets, Stripe keys, etc. live in `.env` or Docker secrets, *never* in source.

---

## 5  Getting Started

### 5.1 Prerequisites

* **JDK 21**
* **Docker 24+** & Docker Compose plugin
* **Gradle 8** (optional – wrapper included)

### 5.2 Clone & Spin Up (Dev Container)

```bash
git clone https://github.com/your‑user/multi‑restaurant‑platform.git
cd multi‑restaurant‑platform

# one‑liner dev environment
docker compose -f docker/docker-compose.dev.yml up --build -d
```

*Backend API will be live at* `http://localhost:8181/api/v1`  *(profile: dev, DB: H2)*

### 5.3 Running Locally Without Docker

```bash
./gradlew :backend:api:bootRun \
  --args='--spring.profiles.active=dev'
```

*Heads‑up*: You’ll need PostgreSQL running on `localhost:5432` and credentials in `application-dev.yml`.

---

## 6  Build, Test & Lint

| Task                | Command                                          |
| ------------------- | ------------------------------------------------ |
| **Module tests**    | `./gradlew backend:<module>:test`                |
| **All tests**       | `./gradlew test`                                 |
| **Static analysis** | `./gradlew checkstyleMain spotbugsMain`          |
| **Container build** | `docker build -t mrp-api -f docker/Dockerfile .` |

*CI Note*: GitHub Actions runs the full matrix on every PR – see `.github/workflows/ci.yml`.

---

## 7  Database Migrations

Flyway runs automatically on app start.  New migrations live in the appropriate module under `src/main/resources/db/migration` using the `V<version>__<name>.sql` convention.

---

## 8  API Preview

*Swagger UI*: `http://localhost:8181/swagger-ui/index.html`

Sample auth flow ➜ register → login → copy `accessToken` → pass as `Bearer <token>` header.

---

## 9  Contributing

1. **Discuss** your idea in an issue – clarity before code.
2. Fork → create feature branch → **tiny commits**.
3. Run `./gradlew test && docker compose -f docker/docker-compose.dev.yml up --build` ; make sure it’s green.
4. Open PR; link the issue; describe *why* & *what*.

---

## 10  License

Distributed under the **MIT License**.  See `LICENSE` for more info.

