# Phase 1 — Authentication

## Goal
Users can register, log in (receive JWT), log out, view sessions, reset password.
All protected routes require a valid Bearer token.

## Definition of Done
- [ ] `POST /api/auth/register` → 201 + token
- [ ] `POST /api/auth/login` → 200 + token  
- [ ] `POST /api/auth/logout` → 204 (token invalidated)
- [ ] `GET /api/sessions` returns list of sessions for authenticated user
- [ ] Login/Register Angular components work end-to-end
- [ ] `sbt test` green (AuthServiceSpec, JwtServiceSpec)

## Tests to Write First (before implementation)

```scala
// backend/test/services/AuthServiceSpec.scala
"register with valid data" → returns JWT token
"register with duplicate email" → returns Left("Email already registered")
"register with duplicate username" → returns Left("Username already taken")
"login with correct credentials" → returns Right(token)
"login with wrong password" → returns Left("invalid")
"login with unknown email" → returns Left("invalid")
```

```scala
// backend/test/controllers/AuthControllerSpec.scala
"POST /api/auth/register with valid body" → 201
"POST /api/auth/register with missing fields" → 400
"POST /api/auth/register with duplicate email" → 409
"POST /api/auth/login with valid credentials" → 200 + token
"POST /api/auth/login with wrong password" → 401
"GET /api/sessions without token" → 401
"GET /api/sessions with valid token" → 200
```

## Implementation Steps

1. Implement `UserRepository.create()` and `findByEmailOrUsername()`
2. Implement `AuthService.register()` and `login()` (bcrypt hash/verify)
3. Complete `JwtService` — already scaffolded, verify tests pass
4. Implement `AuthFilter` to protect non-auth routes  
5. Add `AuthFilter` to `application.conf`: `play.http.filters = filters.AuthFilter`
6. Implement `SessionController.list()` (query user_sessions table)
7. Wire Angular `AuthService` end-to-end test: register → redirects to /chat

## Key Files
- `backend/app/services/AuthService.scala`
- `backend/app/repositories/UserRepository.scala`
- `backend/app/services/JwtService.scala`
- `backend/app/filters/AuthFilter.scala` (create)
- `frontend/src/app/core/auth/auth.service.ts`
- `frontend/src/app/features/auth/login/login.component.ts`
