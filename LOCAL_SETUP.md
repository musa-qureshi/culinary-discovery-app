# Local Setup Guide

This guide explains how to run the project locally for the first time on a new machine.

## What You Need

- Git
- Java 21
- Node.js 20 or newer
- npm
- MySQL Server
- MySQL Workbench, if you want a GUI to manage the database

## Project Structure

- `backend/` contains the Spring Boot API.
- `frontend/` contains the Vue 3 app.
- `database/mysql/` contains the SQL schema script.

## Database Setup

1. Start your local MySQL Server.
2. Open MySQL Workbench and connect to your local server.
3. Create a MySQL user if you do not want to use `root`.
4. Make sure that user has permission to create and modify the database.

The backend is configured to connect to:

```text
jdbc:mysql://localhost:3306/culinary_discovery_app?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
```

If you use a different host, port, or database name, you can override it when you start the backend.

## Start the Backend on Windows

Open PowerShell in the project root and run:

```powershell
cd backend
.\run-local.cmd -DbUser root -DbPassword YOUR_MYSQL_PASSWORD
```

If you want to be prompted for the password instead:

```powershell
cd backend
.\run-local.cmd -DbUser root
```

If your MySQL setup uses a different host or port:

```powershell
cd backend
.\run-local.cmd -DbHost localhost -DbPort 3306 -DbName culinary_discovery_app -DbUser root -DbPassword YOUR_MYSQL_PASSWORD -ServerPort 8080
```

## Start the Backend on macOS or Linux

Open a terminal in the project root and run:

```bash
cd backend
./mvnw spring-boot:run
```

Before that, set the database environment variables for your shell session:

```bash
export DB_URL='jdbc:mysql://localhost:3306/culinary_discovery_app?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
export DB_USERNAME='root'
export DB_PASSWORD='YOUR_MYSQL_PASSWORD'
```

If you want to use a different server port:

```bash
export SERVER_PORT=8080
```

## Start the Frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

The frontend runs on `http://localhost:5173` by default.

If your backend is not on port `8080`, set this before starting the frontend:

```bash
export VITE_API_BASE_URL='http://localhost:8081'
```

On Windows PowerShell:

```powershell
$env:VITE_API_BASE_URL = 'http://localhost:8081'
```

## First-Time Run Checklist

1. Confirm MySQL Server is running.
2. Confirm you can log in with the same MySQL credentials in Workbench.
3. Start the backend and wait until it says Tomcat started on port 8080.
4. Start the frontend.
5. Open `http://localhost:5173` in your browser.

## Troubleshooting

- If you see `Failed to fetch`, the backend is usually not running or could not connect to MySQL.
- If you see `Access denied for user`, double-check the MySQL username and password.
- If port `8080` is already in use, start the backend with another port and update `VITE_API_BASE_URL` accordingly.
- If the database does not exist yet, the configured JDBC URL should create it automatically, but only if your MySQL user has permission.

## Database Schema

The auth tables are created from:

```text
backend/src/main/resources/schema.sql
```

The standalone SQL script is also available at:

```text
database/mysql/001_auth_foundation.sql
```