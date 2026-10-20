# Spot&Slot — Smart Parking Management System
> *"The Moovit of Parking"*

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-JDK_25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![React](https://img.shields.io/badge/React-18-61DAFB?style=for-the-badge&logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?style=for-the-badge&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Python](https://img.shields.io/badge/Python-3.10+-3776AB?style=for-the-badge&logo=python&logoColor=white)](https://www.python.org/)
[![XGBoost](https://img.shields.io/badge/XGBoost-2.x-EB5424?style=for-the-badge)](https://xgboost.readthedocs.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)

---

## 📌 Project Overview

**Spot&Slot** is an end-to-end Smart Parking Management System designed around Gartner's **Ambient Invisible Intelligence (AII)** model. The system embeds real-time awareness and predictive machine learning seamlessly into urban parking infrastructure: drivers simply open the app to view live availability and accurate occupancy forecasts, without needing to know the complex sensing and modeling happening behind the scenes.

Developed at **Afeka College of Engineering** (Department of Software Engineering, Ambient Invisible Intelligence — Spring 2026), the platform addresses chronic urban parking congestion in dense metropolitan areas like North Tel Aviv by providing:
- Real-time availability tracking across parking lots, sections, and individual slots.
- Machine Learning future-occupancy predictions with confidence intervals.
- Role-based management for drivers, lot operators, and system administrators.
- Automated slot health and heartbeat monitoring.

---

## 🏗️ Architecture & Subsystems

```
                               ┌──────────────────────────┐
                               │  Spot&Slot Mobile & Web  │
                               │   (React + Vite + PWA)   │
                               └─────────────┬────────────┘
                                             │ REST (v1.4)
                                             ▼
┌───────────────────────┐      ┌──────────────────────────┐      ┌───────────────────────┐
│  Predictive ML Micro  │◄────►│   Spring Boot 4.1 API    │◄────►│ PostgreSQL Database   │
│ (Flask + XGBoost P10- │ HTTP │ (Business Logic, Auth,   │ JDBC │  (Spring Data JPA /   │
│     P50-P90 Bundle)   │      │   AccessGuard, RBAC)     │      │   Docker Persistence) │
└───────────────────────┘      └──────────────────────────┘      └───────────────────────┘
```

The system is organized as a unified monorepo containing three core micro-components:

| Subsystem | Directory | Tech Stack | Description |
|---|---|---|---|
| **Backend API** | [`/backend`](./backend) | Spring Boot 4.1, JDK 25, Spring Data JPA, PostgreSQL | Central RESTful API (v1.4) managing entities, RBAC access guards, geo-spatial lookups, commands, and health schedulers. Verified with 111 passing automated tests (JUnit, Testcontainers, Cucumber BDD). |
| **Client Apps** | [`/client`](./client) | React 18, Vite 5, TypeScript, Tailwind CSS, shadcn/ui, TanStack Query, Capacitor | Cross-platform web and mobile clients: Driver Mobile App (Android APK via Capacitor & mobile web with Hebrew RTL support) + Operator/Admin Desktop Console. |
| **Predictive Service** | [`/predictive-service`](./predictive-service) | Python 3.10+, Flask, XGBoost, Scikit-learn, Pandas | ML microservice providing `/predict` and `/forecast` endpoints using an XGBoost quantile regression bundle (P10/P50/P90 confidence band) trained on calendar, seasonal, and holiday features ($R^2 = 0.948$). |
| **Documentation** | [`/docs`](./docs) | PDF | Complete architectural report, SRS v1.4, UX/UI research, model evaluation, and Kanban progression report. |

---

## ✨ Key Capabilities

1. **Driver Experience (END_USER)**
   - Live lot map with visual vacant/occupied status (updated via ~5s polling).
   - "Moovit of Parking": Find the best section in a parking lot (`bestSectionInLot`).
   - Filter slots by attributes (e.g. `HANDICAP` accessible spaces).
   - Machine-learning powered 24-hour occupancy forecast with uncertainty bands (P10/P50/P90).
   - Geographic proximity search within a defined radius/square boundary (`byLocation`).

2. **Operator Oversight (OPERATOR)**
   - CRUD management of owned parking infrastructure: Lots, Sections, and Slots.
   - Fine-grained object hierarchy binding (parent/child relationships).
   - Manual slot state toggling (`FREE` / `OCCUPIED`).

3. **System Administration (ADMIN)**
   - User account auditing and elevated role provisioning (`OPERATOR`, `ADMIN`).
   - Comprehensive command invocation history log and system data purging.

4. **Background Health Monitoring**
   - Scheduled heartbeat scanner (`SlotHealthScheduler`) running every 30 seconds to flag stale or offline slots as `FAULTY`.

5. **Resilience & Graceful Degradation**
   - Fail-soft prediction fallback: if the Python ML microservice is unreachable, the API gracefully falls back to real-time heuristic counts with informational flags.

---

## 🚀 Getting Started

### Option 1: Run with Docker Compose (Recommended)

Start all services (PostgreSQL, Predictive ML Service, and Spring Boot Backend) with a single command:

```bash
docker compose up --build
```

- **Backend API**: `http://localhost:8084/ambient-invisible-intelligence`
- **Swagger / OpenAPI Documentation**: `http://localhost:8084/swagger-ui.html`
- **ML Microservice**: `http://localhost:5000`

### Option 2: Running Components Individually

#### 1. Start the Database
```bash
cd backend
docker compose up -d postgres
```

#### 2. Start the Predictive ML Service
```bash
cd predictive-service
python -m venv .venv
source .venv/bin/activate  # On Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

#### 3. Run the Spring Boot API
```bash
cd backend
# Windows:
gradlew.bat bootRun
# Linux / macOS:
./gradlew bootRun
```

#### 4. Run the Client Applications
```bash
cd client
npm install

# Driver Mobile App (runs on :8080):
npm run dev:mobile

# Staff / Admin Console (runs on :8090):
npm run dev:admin
```

---

## 🧪 Testing & Quality Assurance

- **Integration & BDD Suite**: 111 automated tests covering end-to-end REST interactions, role permission matrices, and edge cases using Testcontainers and Cucumber Gherkin feature files.
  ```bash
  cd backend
  ./gradlew test
  ```
- **Load & Performance Testing**: Apache JMeter 5.6.3 test plan (`backend/perf/smart-parking-perf.jmx`) under 10 concurrent users / 1,000 requests achieved 0.00% error rate and single-digit to ~18 ms average latency on paged reads and geographic searches.

---

## 👥 The Penguins Team

Afeka College of Engineering — Ambient Invisible Intelligence (Spring 2026):
- **Omer Asayag** — Team Lead & Scrum Master
- **Avishay Haimov** — Product Owner
- **Tom Gurevich** — QA Engineer & Configuration Manager
- **Stephanie Kovaks** — Technical Writer & UI/UX Engineer
- **Shawn Farberov** — System Architect
- **Nicole Barghout** — DBA & DevOps

---

## 📄 License & Academic Integrity
This repository is submitted as the Final Capstone Project for the Ambient Invisible Intelligence course at Afeka College of Engineering.
