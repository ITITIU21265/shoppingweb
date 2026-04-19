# Manual Auth Checklist

This checklist is scoped to the modules that currently exist in this repository.

## Public Endpoints

- `GET /api/auth/login` returns the login page.
- `POST /api/auth/login` succeeds with valid credentials.
- `POST /api/auth/login` returns `401` for wrong password.
- `POST /api/auth/register` succeeds with valid data.
- `POST /api/auth/register` returns `409` for duplicate email or username.
- `POST /api/auth/forgot-password` always returns a generic success message.
- `POST /api/auth/reset-password` returns `400` for invalid, expired, or used token.
- `POST /api/auth/refresh` returns a new access token and a rotated refresh token.

## Authenticated Endpoints

- `GET /api/auth/me` returns current user with a valid access token.
- `GET /api/auth/me` returns `401` without token.
- `PUT /api/auth/change-password` returns `401` for wrong current password.
- `POST /api/auth/logout` revokes the supplied refresh token.
- `GET /api/users/profile` returns `401` without token.
- `PUT /api/users/profile` updates fullname and email for the authenticated user.
- `DELETE /api/users/account` soft-deletes the authenticated user and revokes refresh tokens.

## Admin Endpoints

- `GET /api/admin/users` returns `403` for non-admin.
- `PUT /api/admin/users/{id}/role` changes role for admin only.
- `PATCH /api/admin/users/{id}/status` blocks and unblocks users for admin only.

## Token Lifecycle

- Login revokes the previous refresh token of the same user.
- Refresh with an old refresh token fails after rotation.
- Refresh fails with `401` for blocked or deleted users.
- After password change, the previous refresh token can no longer be used.
- After password reset, previous refresh tokens can no longer be used.
- After admin block, previously issued access tokens should stop working because JWT auth no longer accepts disabled users.

## Database Bootstrap

- Start with a database that has no roles seeded manually.
- Verify startup creates required roles:
  `ADMIN`, `CUSTOMER`, `SELLER`
- Verify password reset requests no longer fail because of a missing `password_resets` table.
