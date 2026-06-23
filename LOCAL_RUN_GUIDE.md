# Run Guide — IntelliJ Community + Local PostgreSQL + Postman

This project was originally built for Docker. It has been adapted to run **locally** in
**IntelliJ IDEA Community Edition** against your **local PostgreSQL**, tested with **Postman**.

> There is **no frontend** in this project. It is a backend-only microservices system that you
> call directly with Postman. (You mentioned running a frontend in VS Code — this repo doesn't
> contain one, so there's nothing to open in VS Code here.)

## What was changed
- **All databases point to local PostgreSQL** (`localhost:5432`, user `postgres`, password `postgres`),
  using a separate database per service: `auth_db`, `patient_db`.
- **Kafka** points to `localhost:9092` (analytics-service consumes events the patient-service produces).
- **Billing gRPC is DISABLED** (`billing.grpc.enabled=false` in patient-service). Patient creation
  still works; it just skips the billing call. You do **not** need to run billing-service.
- **API Gateway** routes now use `localhost` instead of Docker hostnames.
- Added `spring.jpa.defer-datasource-initialization=true` so the seed `data.sql` loads after Hibernate
  creates the tables.

## Services you will run
| Service           | Port  | Needed? |
|-------------------|-------|---------|
| auth-service      | 4005  | yes     |
| patient-service   | 4000  | yes     |
| analytics-service | 4002  | yes (Kafka consumer) |
| api-gateway       | 4004  | optional (only if you want JWT-protected routing) |
| billing-service   | —     | NOT needed (gRPC disabled) |

---

## Step 1 — Prerequisites
1. **JDK 21** (Project uses Java 21). In IntelliJ: File → Project Structure → SDK → 21.
2. **PostgreSQL** installed and running on `localhost:5432` with user `postgres` / password `postgres`.
3. **Apache Kafka** running locally on `localhost:9092` (see Step 3).
4. **Postman**.

## Step 2 — Create the databases
Open `psql` (or pgAdmin) and run:
```sql
CREATE DATABASE auth_db;
CREATE DATABASE patient_db;
```
That's all — tables and seed data are created automatically on first startup.

## Step 3 — Start Kafka locally (KRaft mode, no Zookeeper)
Download Kafka, then in the Kafka folder:
```bash
# 1. Generate a cluster ID
bin/kafka-storage.sh random-uuid
# 2. Format storage (replace <UUID> with the value above)
bin/kafka-storage.sh format -t <UUID> -c config/kraft/server.properties
# 3. Start the broker
bin/kafka-server-start.sh config/kraft/server.properties
```
On Windows use the `bin\windows\*.bat` equivalents. Kafka must listen on `localhost:9092` (default).
The `patient` topic is created automatically when the first event is sent.

> Don't want Kafka right now? You can still run auth-service and patient-service. Creating a patient
> will simply log a Kafka send error and continue. Analytics-service won't receive events without Kafka.

## Step 4 — Open in IntelliJ Community
1. **File → Open** and select the project root folder (the one containing this file).
2. IntelliJ will detect each `pom.xml`. If prompted, **import as Maven projects** / **Load Maven Project**.
3. Wait for Maven to download dependencies (first time takes a few minutes — needs internet).
4. Each service has its own `*ServiceApplication.java` with a `main` method.

## Step 5 — Run the services
In IntelliJ Community there is no Spring dashboard, so run each main class directly:
- Open `auth-service/.../AuthServiceApplication.java` → click the green ▶ next to `main` → Run.
- Do the same for:
  - `patient-service/.../PatientServiceApplication.java`
  - `analytics-service/.../AnalyticsServiceApplication.java`
  - `api-gateway/.../ApiGatewayApplication.java` (optional)

Each becomes a separate Run Configuration you can restart anytime.

**Recommended start order:** auth-service → patient-service → analytics-service → api-gateway.

## Step 6 — Test with Postman
1. Import `postman/PatientManagement-Local.postman_collection.json`.
2. Two ways to call patient endpoints:

**A) Direct to patient-service (no auth) — simplest**
- `GET  http://localhost:4000/patients`
- `POST http://localhost:4000/patients`  (body below)

**B) Through the API Gateway (JWT required)**
- First `POST http://localhost:4005/login` with:
  ```json
  { "email": "testuser@test.com", "password": "password" }
  ```
  (The collection auto-saves the returned token.)
- Then `GET http://localhost:4004/api/patients` with header `Authorization: Bearer <token>`.

**Create patient body:**
```json
{
  "name": "Jane Smith",
  "email": "jane.smith@example.com",
  "address": "42 Oak Street",
  "dateOfBirth": "1990-04-12",
  "registeredDate": "2024-05-01"
}
```

### Seeded login
- Email: `testuser@test.com`
- Password: `password`

---

## Optional: re-enable billing gRPC later
If you ever want the billing service back:
1. In `patient-service/src/main/resources/application.properties` set `billing.grpc.enabled=true`.
2. Add `billing.service.address=localhost` and `billing.service.grpc.port=9001`.
3. Run `billing-service` (it needs its own PostgreSQL DB — create `billing_db` and add datasource
   properties to its `application.properties`, mirroring the others).

## Troubleshooting
- **`password authentication failed`** → your Postgres user/password differs. Edit the
  `spring.datasource.username` / `password` lines in each service's `application.properties`.
- **`database "auth_db" does not exist`** → you skipped Step 2.
- **Port already in use** → another process holds 4000/4005/4002/4004. Stop it or change `server.port`.
- **Kafka connection errors on patient create** → Kafka isn't running on 9092. Either start it (Step 3)
  or ignore if you don't need analytics.
- **Patient create returns 400** → check the JSON body; `dateOfBirth` must be `YYYY-MM-DD`.
