# UPI-Simulation

A full-stack digital wallet demo built with React, Spring Boot, and MySQL. It simulates wallet balances and transfers for learning and project demonstrations; it does **not** connect to UPI, banks, or real money.

## Features

- User registration with mobile-number validation and login with JWT authentication.
- Wallet dashboard with balance and account status.
- Demo deposits, withdrawals, and transfers to another registered user's mobile number.
- Transaction history with transaction type, amount, status, and sender/recipient details.
- Role-based access, including admin endpoints to list, freeze, and unfreeze accounts.
- Transfer idempotency: submitting the same transfer request with the same `Idempotency-Key` returns the original transaction rather than applying the debit twice.
- Database transactions and account locking to keep simultaneous balance updates consistent.
- MySQL schema managed by Flyway migrations.
- Swagger UI for exploring the REST API.

## Technology

| Layer | Tools |
| --- | --- |
| Frontend | React, Vite, JavaScript, CSS |
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Authentication | JWT bearer tokens; BCrypt password hashing |
| Database | MySQL 8.4, Flyway |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Local database | Docker Compose |

## Project layout

```text
frontend/                         React application
src/main/java/Wallet_api/         Spring Boot API
src/main/resources/db/migration/  Flyway SQL migrations
pom.xml                           Maven backend configuration
```

## Run locally

### Prerequisites

- Java 21
- Node.js and npm
- Docker Desktop with Docker Compose, or a local MySQL 8.4 server

### 1. Recreate the excluded local configuration

For safety, `compose.yaml` and `src/main/resources/application.properties` are not committed. Create them locally before starting the application. Replace the example database passwords with your own **local demo values**, and use the same MySQL application password in both files.

Create `compose.yaml` in the project root:

```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: wallet-mysql
    environment:
      MYSQL_DATABASE: wallet_db
      MYSQL_USER: wallet_app
      MYSQL_PASSWORD: change_this_local_db_password
      MYSQL_ROOT_PASSWORD: change_this_local_root_password
    ports:
      - "3307:3306"
    volumes:
      - wallet-mysql-data:/var/lib/mysql

volumes:
  wallet-mysql-data:
```

Create `src/main/resources/application.properties`:

```properties
spring.application.name=Wallet-api
server.port=8081
spring.datasource.url=jdbc:mysql://localhost:3307/wallet_db
spring.datasource.username=wallet_app
spring.datasource.password=change_this_local_db_password
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false
app.jwt.secret=${JWT_SECRET}
```

Set `JWT_SECRET` in IntelliJ's Run Configuration environment variables to a randomly generated secret of at least 32 bytes. Keep real credentials and production secrets out of Git.

### 2. Start MySQL

From the project root:

```powershell
docker compose up -d
```

Flyway applies the database migrations when the backend starts.

### 3. Start the backend

Run `WalletApiApplication` from IntelliJ, or use the Maven wrapper in PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8081`.

### 4. Start the frontend

Open a second terminal:

```powershell
cd frontend
npm install
npm run dev
```

Open the local URL printed by Vite, typically `http://localhost:5173` or `http://localhost:5174`.

## API routes

| Method | Route | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Register a user and wallet |
| `POST` | `/api/v1/auth/login` | Sign in and receive a JWT |
| `GET` | `/api/v1/accounts/me` | View the signed-in user's wallet |
| `POST` | `/api/v1/transactions/deposit` | Add demo funds |
| `POST` | `/api/v1/transactions/withdraw` | Withdraw demo funds |
| `POST` | `/api/v1/transactions/transfer` | Transfer demo funds to a registered phone number |
| `GET` | `/api/v1/transactions` | View transaction history |
| `GET` | `/api/v1/admin/accounts` | List accounts (admin only) |
| `GET` | `/api/v1/admin/accounts/{accountId}` | View one account (admin only) |
| `PATCH` | `/api/v1/admin/accounts/{accountId}/freeze` or `/unfreeze` | Change account status (admin only) |
| `GET` | `/api/v1/health` | Check API health |

Swagger UI is available at `http://localhost:8081/swagger-ui/index.html` while the backend is running.

## Demonstrate idempotency

Make one successful transfer in the frontend, then select **Replay same transfer**. The replay sends the same request with the original idempotency key. It should return the same transaction ID, and the wallet balance should only change once. This demonstrates backend duplicate-request handling; it is not a connection to UPI's payment network or bank reconciliation systems.

## Security note

This project is for local learning and demonstration only. Deposits create simulated funds. Do not use real banking credentials, real payment data, or demo configuration values in a production environment.
