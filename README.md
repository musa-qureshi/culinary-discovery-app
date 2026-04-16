# Culinary Discovery App

Farm-to-table recipe and meal kit marketplace built for a database systems project.

## Overview

This project connects home cooks, verified chefs, suppliers, and admins in one platform for:

- recipe discovery and creation
- meal planning
- supplier browsing
- chef verification workflows
- role-based dashboards

## Stack

- Backend: Spring Boot, JDBC, MySQL
- Frontend: Vue 3, TypeScript, Vite
- Database: MySQL 8+

## Current State

The auth flow is implemented first:

- register as home cook, verified chef, or supplier
- admin accounts are added manually in the database
- login routes users to role-specific dashboards

## Documentation

- [Local setup guide](LOCAL_SETUP.md)
- [Database auth schema](database/mysql/001_auth_foundation.sql)

## Project Layout

- `backend/` Spring Boot API
- `frontend/` Vue app
- `database/mysql/` SQL scripts