# File Storage Management System

A full-lifecycle file storage service — upload, retrieval, soft-delete, archival,
and restore — with JWT authentication, OAuth2 (Google) login, and role-based
access control (RBAC). Built with Spring Boot 3 / Java 17 on the backend and a
React SPA on the frontend, containerized with Docker and deployable to
Kubernetes.

## Features

- **File lifecycle**: real binary upload/download (multipart, stored on disk),
  soft-delete ("archive"), restore, metadata update, paginated listing with
  owner filtering.
- **Audit logging**: every lifecycle event (`UPLOADED`, `ARCHIVED`, `RESTORED`,
  `UPDATED`) is recorded in `file_audit_log`, which has **no foreign key** to
  `files` — so audit history survives the scheduled purge job even after the
  source record is hard-deleted.
- **Scheduled purge**: `FilePurgeService` runs nightly (`@Scheduled`, 2 AM),
  permanently deleting files archived more than 30 days ago (DB row + the
  stored binary), while leaving the audit trail intact.
- **JWT authentication**: `/api/auth/register` and `/api/auth/login` issue a
  signed, stateless JWT (`io.jsonwebtoken`); every subsequent request carries
  it as `Authorization: Bearer <token>`.
- **OAuth2 authorization**: Google login via `spring-boot-starter-oauth2-client`.
  On success, the app finds-or-creates a local user record and issues the same
  kind of JWT the local login path issues, so the rest of the API doesn't need
  to know which login method was used. Inactive by default (see below) so the
  app runs out of the box without real Google credentials.
- **RBAC**: two roles, `USER` and `ADMIN`.
  - Regular users can only see/update/archive/restore/download **their own**
    files; the owner is derived from the authenticated principal, not client
    input.
  - `/api/files/stats`, `/api/files/audit`, and `/api/files/deleted` are
    `ADMIN`-only, enforced at the security-filter-chain level.
  - Admins can act on any user's files and reassign ownership.
- **Service layer behind an interface** (`FileService`, `AuthService`):
  `FileServiceImpl` is unit-tested with **pure Mockito** (`FileServiceImplTest`)
  — no `@SpringBootTest`, no embedded context, just the repository/storage
  mocks and the service under test.
- **PostgreSQL indexing**: indexes on `file_type`, `owner_name`, `is_deleted`
  for fast metadata lookups (see `schema.sql`).

## Project layout

```
file-storage-service/
  backend/    Spring Boot 3 REST API (Java 17)
  frontend/   React SPA
  k8s/        Deployment, Service, ConfigMap, Secret template
.github/workflows/ci.yml   CI: backend tests, frontend build, Docker image build
```

## Running locally

### Backend

Requires Java 17, Maven, and a PostgreSQL instance.

```bash
cd file-storage-service/backend
# point at your own Postgres, or override via env vars (see below)
mvn spring-boot:run
```

Environment variables (all have safe local defaults — see `application.properties`):

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL connection | `jdbc:postgresql://localhost:5432/filestorage`, `postgres`, `password` |
| `JWT_SECRET` | HMAC signing key for JWTs | dev-only placeholder — **override before any real deployment** |
| `STORAGE_LOCATION` | Where uploaded binaries are stored on disk | `./uploads` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` / `ADMIN_EMAIL` | Seeded admin account (first boot only) | `admin` / `admin123` / `admin@filestorage.local` |
| `CORS_ALLOWED_ORIGIN`, `FRONTEND_URL` | Frontend origin for CORS + OAuth2 redirect | `http://localhost:3000` |

On first boot, an `ADMIN` account is seeded automatically (`AdminSeeder`) so the
admin-only endpoints are reachable immediately — log in with `admin` /
`admin123` (or whatever you set via env vars) and change the password from
there in a real deployment.

**Enabling Google OAuth2 login** (optional): set `GOOGLE_CLIENT_ID` and
`GOOGLE_CLIENT_SECRET`, then run with the `oauth2` profile active
(`SPRING_PROFILES_ACTIVE=oauth2`). Without it, the app runs JWT-only and
`.oauth2Login()` is never wired up.

### Frontend

```bash
cd file-storage-service/frontend
npm install
npm start
```

Opens at `http://localhost:3000`. Register a user, or log in as the seeded
admin to see the Stats / Deleted Files / Audit Log tabs.

### Tests

```bash
cd file-storage-service/backend
mvn test
```

`FileServiceImplTest` mocks `FileRepository`, `FileAuditLogRepository`, and
`FileStorageService` directly — no Spring context is started — and covers
ownership enforcement (RBAC), soft-delete/restore/update flows, and audit log
writes.

## Docker

```bash
docker build -t file-storage-backend:latest file-storage-service/backend
docker run -p 8080:8080 --env-file .env file-storage-backend:latest
```

## Kubernetes

See [file-storage-service/k8s/README.md](file-storage-service/k8s/README.md) for the full apply order. Manifests
included: `Deployment`, `Service`, `ConfigMap`, a `Secret` template
(`secret.example.yaml` — copy and fill in real values, never commit the real
one), a `PersistentVolumeClaim` for uploaded binaries, and a convenience
Postgres deployment for local/demo clusters.

## CI

`.github/workflows/ci.yml` runs on every push/PR: backend build + Mockito unit
tests (Maven), frontend production build (npm), and a Docker image build of
the backend.
