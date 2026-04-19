# Auth Design

This document reflects the current scope of this repository. The project currently exposes only these backend areas:

- `auth`
- `users`
- `admin users`
- static login page

There are no active `product`, `cart`, `order`, or `chatbot` modules in the current codebase, so they are intentionally excluded from the access matrix below.

## Access Matrix

| Endpoint | Method | Access |
| --- | --- | --- |
| `/api/auth/login` | `GET` | Public |
| `/api/auth/login` | `POST` | Public |
| `/api/auth/register` | `POST` | Public |
| `/api/auth/forgot-password` | `POST` | Public |
| `/api/auth/reset-password` | `POST` | Public |
| `/api/auth/refresh` | `POST` | Public |
| `/api/auth/me` | `GET` | Authenticated |
| `/api/auth/logout` | `POST` | Authenticated |
| `/api/auth/change-password` | `PUT` | Authenticated |
| `/api/users/profile` | `GET` | Authenticated |
| `/api/users/profile` | `PUT` | Authenticated |
| `/api/users/account` | `DELETE` | Authenticated |
| `/api/admin/users` | `GET` | `ADMIN` |
| `/api/admin/users/{id}/role` | `PUT` | `ADMIN` |
| `/api/admin/users/{id}/status` | `PATCH` | `ADMIN` |

Static assets are public:

- `/css/**`
- `/js/**`
- `/images/**`
- `/fonts/**`

## Token Policy

- Login returns `accessToken` and `refreshToken`.
- Only one refresh token is kept per user in the current project scope.
- Refresh rotates the refresh token:
  old token is deleted, new access token and new refresh token are issued.
- Logout revokes the supplied refresh token.
- Changing password revokes all refresh tokens of the user.
- Resetting password revokes all refresh tokens of the user.
- Blocking or deleting a user revokes all refresh tokens of the user.
- Only users with status `ACTIVE` can authenticate or refresh.

## Frontend Session State

- The current login page now stores:
  `accessToken`, `refreshToken`, and a lightweight `currentUser` object in `localStorage`.
- The login page is wired for:
  login, register, and forgot-password request.
- The repository does not yet contain a protected frontend area that automatically retries requests with `/api/auth/refresh`.
- Because of that, the backend refresh flow is now correct, but a shared frontend request interceptor is still the next step once protected pages are added.

## Current User Rules

- Endpoints that depend on the current user now require authentication at the security layer.
- Controllers resolve the current username through `SecurityUtils.requireCurrentUsername(...)`.
- JWT authentication will not populate the security context for disabled, blocked, or deleted users.

## Error Contract

The backend now distinguishes the main error groups as follows:

| Type | Status |
| --- | --- |
| Validation error | `400` |
| Business rule / invalid request data | `400` |
| Authentication failure | `401` |
| Access denied | `403` |
| Resource not found | `404` |
| Duplicate data | `409` |
| Unexpected server error | `500` |

Error response shape remains:

```json
{
  "timestamp": "2026-04-18T23:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Refresh token has expired",
  "path": "/api/auth/refresh"
}
```

## Database Notes

- `users`, `roles`, `user_roles`, and `refresh_tokens` are managed by JPA entities.
- `password_resets` is now also managed by JPA through `PasswordResetToken`.
- Mandatory roles are seeded on startup:
  `ADMIN`, `CUSTOMER`, `SELLER`

This project still uses `spring.jpa.hibernate.ddl-auto=update`, so it is more stable than before but not yet a full versioned migration setup. A future step should replace this with Flyway or Liquibase once the schema is finalized.
