# 🚀 <u>Too Many Issues - Core API & Ingestion Engine</u>

> The enterprise-grade Spring Boot backend powering **[Too Many Issues](https://toomanyissues.vercel.app)**.
> Designed to ingest, index, and summarize real-time GitHub issues across the open-source ecosystem without hitting API rate limits.

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://java.com)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.X-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-blue.svg)](https://postgresql.org)
[![MongoDB](https://img.shields.io/badge/MongoDB-Atlas-47A248.svg)](https://mongodb.com)
[![Redis](https://img.shields.io/badge/Redis-Distributed_Locks-red.svg)](https://redis.io)

## 📌 <u>Overview</u>
This repository contains the RESTful API and automated background workers for the Too Many Issues platform. Instead of simply wrapping the GitHub API, this system utilizes a custom scheduling algorithm, distributed caching, and a polyglot persistence layer to autonomously manage massive data ingestion and AI summarization at scale.

---

## 🏗️ <u>System Architecture & Engineering Highlights</u>

### 1. The Ingestion Engine (Dynamic Polling Algorithm)
To index thousands of repositories without exhausting GitHub's API rate limits, I engineered a custom **"Thermostat" (Hot/Warm/Cold) polling algorithm**.
* **Mechanism:** The cron-scraper dynamically adjusts its polling frequency based on repository activity. Repositories with high issue frequency are polled every hour, while inactive "Cold" repositories are throttled to a 24-hour sync.
* **Result:** Achieved a massive reduction in unnecessary API calls while maintaining real-time relevance for active open-source projects.

![Ingestion Architecture](images/ingestion.png)

### 2. AI Summarization Pipeline & Distributed Concurrency
The platform integrates the **Gemini AI Model** to summarize complex issue threads. To prevent a "thundering herd" race condition (where multiple users request a summary for the same un-cached issue simultaneously), I implemented distributed locking.
* **Mechanism:** Utilizes Redis `SETNX` locks with a 15-second TTL. The first user acquires the lock and triggers the Gemini generation pipeline, while concurrent requests poll the cache.
* **Result:** Prevents duplicate API calls, saves AI token credits, and ensures data consistency across the cluster.

![Concurrency Architecture](images/summary-pipeline.png)

### 3. Polyglot Data Persistence
* **PostgreSQL (Supabase):** Enforces strict relational integrity for User accounts, Role-Based Access Control (RBAC), and user tracking (AI Point deductions).
* **MongoDB (Atlas):** Handles high-volume, schema-less data. Utilizes compound indexing to efficiently query and serve the massive payload of unstructured GitHub issue text and markdown.

### 4. Stateless Security & JWT Rotation
Implemented a deeply customized Spring Security filter chain ensuring robust, stateless authentication.
* **Access Tokens:** Short-lived JWTs returned via JSON payload for Authorization headers.
* **Refresh Tokens:** Long-lived tokens securely issued as `HttpOnly` cookies to mitigate XSS (Cross-Site Scripting) attacks.
* **RBAC:** Enforced via `@PreAuthorize` to strictly separate `USER` application logic from `ADMIN` metric dashboards.

![Security Architecture](images/security-chain.png)

---

## 💻 <u>Tech Stack</u>

* **Core:** Java 17, Spring Boot 3, Spring Data JPA, Spring Security
* **Databases:** PostgreSQL (Relational), MongoDB (Document)
* **Caching & Concurrency:** Redis
* **AI Integration:** Google Gemini API
* **Deployment:** AWS EC2, Docker Compose, Nginx (Reverse Proxy + Let's Encrypt SSL)

---

## 🚀<u>Local Setup & Installation</u>
### **DOCKER SETUP (Recommended):** Want to just run and see everything in action in seconds
**Prerequisites:** Docker

**1. Make an .env file in your working dir and configure the following keys**
```aiignore
# Redis Setup
REDIS_PASSWORD=local_dev_redis_password_123

# PAT/API Tokens
APP_GITHUBPAT=your_github_personal_access_token
GEMINI_API_KEY=your_gemini_api_key

# App Secrets
APP_JWTSECRETKEY=your_super_secret_jwt_key_at_least_256_bits
APP_JWTTOKENTIMEOUT=86400000
INTERNAL_EXPORT_KEY=your_internal_export_sync_key
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```
**2. Run the following command in your working dir:**
```aiignore
curl -o docker-compose.yaml https://raw.githubusercontent.com/narayanpdas/toomanyissues-backend/main/docker-compose.yaml
docker compose up -d 
```
#### Check:
**3. Run ``docker ps`` to check if the container is running.**

**4. For streaming logs run ``docker logs -f toomanyissues-api``**

#### 📔Note: If You want to connect custom database/server, make sure to change the corresponding environmental variables in the toomanyissues-docker-compose.yaml file.

### **JVM SETUP:**  Want to run in an active development environment

**Prerequisites:** Java 17, Maven, Docker (for Redis/DB instances)

**1. Clone the repository:**
```bash
git clone https://github.com/narayanpdas/toomanyissues-backend.git
cd toomanyissues-backend
```
**2. Configure Environment Variables:**
Create an application-dev.yml or .env file in the root directory and configure the following keys:

```aiignore
# Database Connections
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/toomanyissues-postgres-db
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=password123
SPRING_MONGODB_URI=mongodb://localhost:27017/githubIssues

# Redis Setup
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=local_dev_redis_password_123

# PAT/API Tokens
APP_GITHUBPAT=your_github_personal_access_token
GEMINI_API_KEY=your_gemini_api_key

# App Secrets
APP_JWTSECRETKEY=your_super_secret_jwt_key_at_least_256_bits
APP_JWTTOKENTIMEOUT=86400000
INTERNAL_EXPORT_KEY=your_internal_export_sync_key
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

**3. Run the Application:**
```
mvn clean install
mvn spring-boot:run
```
The server will start on http://localhost:8080.

# 📬 Live Platform & Contact
Live: https://toomanyissues.vercel.app

Creator: Narayan

LinkedIn: [Linkedin-Profile](https://www.linkedin.com/in/narayan-prasad-das-85257b249/)