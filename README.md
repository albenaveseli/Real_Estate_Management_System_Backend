# Real Estate Management System — Backend

A multi-tenant real estate management platform built with Spring Boot 3.5 and PostgreSQL. The system supports property listings, rental and sale workflows, lease contracts, payments, maintenance requests, lead management, notifications, and AI-powered features.

---

## Architecture Overview

The backend follows a layered architecture pattern with strict separation of concerns. Each layer communicates only with the layer directly below it, ensuring maintainability and testability.

```
Client (React)
      |
      | HTTP/REST
      v
Controllers  — receive and validate HTTP requests, delegate to services
             — extend BaseController for unified response handling (OOP/DRY)
Services     — contain all business logic, orchestrate data access
Repositories — data access layer using Spring Data JPA + JDBC
Entities     — JPA-mapped domain models
Database     — PostgreSQL with schema-based multi-tenancy
```

### Multi-Tenancy

The system implements schema-based multi-tenancy using Hibernate's `MultiTenantConnectionProvider` interface. Each company (tenant) gets its own isolated PostgreSQL schema at registration time. The `public` schema holds shared data — users, tenants, roles, permissions, refresh tokens. Every tenant schema holds per-company data — properties, contracts, payments, leads, notifications, and maintenance requests.

The tenant is identified from the JWT token on every request. The `JwtAuthFilter` extracts `tenantId`, `schemaName`, `userId`, and `role` from the token, sets them on `TenantContext` (a `ThreadLocal` wrapper), and Hibernate uses the schema name to route all queries to the correct schema automatically.

Schema provisioning runs via Flyway when a new tenant registers. Migrations in `db/migration/tenant` are applied to the new schema automatically. On application startup, `TenantMigrationService` runs pending migrations for all existing tenant schemas.

## How Multi-Tenancy Works

```mermaid
flowchart TD

    classDef default fill:#1F2937,stroke:#9CA3AF,color:#F9FAFB,stroke-width:1.5px;
    classDef highlight fill:#14532D,stroke:#4ADE80,color:#FFFFFF,stroke-width:2px;
    classDef warning fill:#78350F,stroke:#FBBF24,color:#FFFFFF,stroke-width:2px;

    A1["🏢 Anvogue<br/>slug: anvogue · id: 1"]
    A2["🏢 EliteRealty<br/>slug: eliterealty · id: 2"]
    A3["🏢 HomePro<br/>slug: homepro · id: 3"]

    REQ["HTTP Request<br/>GET /api/properties · Authorization: Bearer eyJhbGci..."]

    JWT["JWT Token — all routing info packed inside<br/>userId · tenantId · schemaName · role"]

    subgraph FILTERS["Spring Security Filter Chain"]
        F1["JwtAuthFilter<br/>1. validate token<br/>2. extract claims<br/>3. set TenantContext<br/>4. set Authentication"]

        F2["PermissionAuthorizationFilter<br/>1. get userId from TenantContext<br/>2. JDBC → public.permissions<br/>3. AntPathMatcher check<br/>4. 403 or continue"]
    end

    TC["TenantContext — ThreadLocal<br/>userId · tenantId · schema=tenant_eliterealty_2 · role"]

    HB["SchemaMultiTenantConnectionProvider<br/>SET search_path TO tenant_eliterealty_2, public"]

    subgraph DB["PostgreSQL — realestate_db"]
        PUB["public schema — shared<br/>users · tenants · roles · permissions"]

        S1["tenant_anvogue_1<br/>properties · contracts · payments · leads"]

        S2["tenant_eliterealty_2 ← ACTIVE<br/>properties · contracts · payments · leads"]

        S3["tenant_homepro_3<br/>properties · contracts · payments · leads"]
    end

    A1 --> REQ
    A2 --> REQ
    A3 --> REQ

    REQ --> JWT
    JWT --> F1
    F1 --> F2
    F2 --> TC
    TC --> HB

    HB --> PUB
    HB --> S1
    HB --> S2
    HB --> S3

    class JWT,TC warning
    class HB,S2 highlight
```
# Request Lifecycle — Real Estate Management System

---

## Frontend Overview

```mermaid
flowchart LR
    A(["👤 User klikon\nDashboard"]) --> B["AdminDashboard.jsx\nuseEffect fires"]
    B --> C["api.get\ndashboard/stats"]
    C --> D["axios interceptor\nshton Authorization\nBearer eyJhbGci..."]
    D --> E["XMLHttpRequest\nbrowser sends"]
    E --> F(["🌐 HTTP GET\nlocalhost:8080\ndashboard/stats"])

    style A fill:#2d4a22,color:#fff
    style F fill:#1a3a4a,color:#fff
    style D fill:#4a3a10,color:#fff
```

---

## Tomcat Acceptance

```mermaid
flowchart LR
    A(["🌐 TCP Packets\nlocalhost:8080"]) --> B["Tomcat NIO\nConnector\nNioEndpoint"]
    B --> C["Http11Processor\nparse HTTP\nparse headers"]
    C --> D["StandardContext\nValve\ngjen aplikacionin"]
    D --> E["Application\nFilterChain\nkrijon filtrat"]
    E --> F(["▶️ Filter Chain\nfillon"])

    style A fill:#1a3a4a,color:#fff
    style F fill:#2d4a22,color:#fff
    style B fill:#3a2a10,color:#fff
    style E fill:#3a2a10,color:#fff
```

---

## Servlet Filter Chain

```mermaid
flowchart LR
    A(["▶️ Start"]) --> B["CharacterEncoding\nFilter\nUTF-8"]
    B --> C["CorsFilter\nOrigin check\nACCO headers"]
    C --> D["LoggingFilter\nComponent\nstartTime = now\nPAUSE"]
    D --> E["Delegating\nFilterProxy\nSecurity Chain"]
    E --> F["Permission\nAuthorization\nFilter\nComponent"]
    F --> G(["DispatcherServlet"])

    style A fill:#1a3a4a,color:#fff
    style D fill:#4a3010,color:#fff
    style E fill:#2d1a4a,color:#fff
    style G fill:#2d4a22,color:#fff
```

---

## Spring Security Filter Chain

```mermaid
flowchart LR
    A(["▶️ Enter\nSecurity"]) --> B["DisableEncode\nUrlFilter"]
    B --> C["SecurityContext\nHolderFilter\nkrijon context"]
    C --> D["HeaderWriter\nFilter"]
    D --> E["LogoutFilter\nnuk eshte logout\nkalon"]
    E --> F["JwtAuthFilter\nKRYESOR"]
    F --> G["Anonymous\nAuthFilter"]
    G --> H["Authorization\nFilter\nauthenticated"]
    H --> I(["▶️ Permission\nFilter"])

    style A fill:#2d1a4a,color:#fff
    style F fill:#4a1a1a,color:#fff,stroke:#ff6b6b,stroke-width:3px
    style H fill:#1a3a1a,color:#fff
    style I fill:#2d4a22,color:#fff
```

---

## JwtAuthFilter Details

```mermaid
flowchart LR
    A(["🔑 Token\nne header"]) --> B{"Header\nekziston?"}
    B -->|"Jo"| C(["kalon pa\nautentikimi"])
    B -->|"Po"| D["heq Bearer\nprefix"]
    D --> E["JJWT\nverify\nHMAC-SHA256"]
    E --> F{"Valid?"}
    F -->|"Skaduar"| G(["401\nExpiredJwt"])
    F -->|"Invalid"| H(["401\nJwtException"])
    F -->|"OK"| I["Ekstrakt Claims\nuserId=29\ntenantId=8\nschema=tenant_X\nrole=ADMIN"]
    I --> J["TenantContext.set\nThreadLocal"]
    J --> K["SecurityContext\nROLE_ADMIN"]
    K --> L(["✅ chain\ndoFilter"])

    style A fill:#4a3010,color:#fff
    style G fill:#4a1a1a,color:#fff
    style H fill:#4a1a1a,color:#fff
    style I fill:#1a3a4a,color:#fff
    style J fill:#1a4a3a,color:#fff
    style L fill:#2d4a22,color:#fff
```

---

## PermissionAuthorizationFilter Details

```mermaid
flowchart LR
    A(["▶️ Enter"]) --> B{"shouldNot\nFilter?\npublic paths?"}
    B -->|"Po public"| C(["SKIP\nkalon direkt"])
    B -->|"Jo"| D["userId =\nTenantContext\ngetUserId"]
    D --> E{"userId\nnull?"}
    E -->|"Po"| F(["kalon\npa kontroll"])
    E -->|"Jo"| G["JDBC query\npublic.permissions\nWHERE user_id=29"]
    G --> H["AntPathMatcher\nGET dashboard\nstats"]
    H --> I{"Match\ngjetur?"}
    I -->|"Jo"| J(["403\nForbidden"])
    I -->|"Po"| K["log Access granted\nuserId=29"]
    K --> L(["✅ chain\ndoFilter"])

    style A fill:#2d1a4a,color:#fff
    style C fill:#2d4a22,color:#fff
    style F fill:#2d4a22,color:#fff
    style G fill:#1a3a4a,color:#fff
    style J fill:#4a1a1a,color:#fff
    style L fill:#2d4a22,color:#fff
```

---

##  DispatcherServlet Controller Service

```mermaid
flowchart LR
    A(["▶️ Dispatcher\nServlet"]) --> B["RequestMapping\nHandlerMapping\nlookup registry"]
    B --> C["DashboardController\ngetStats matched"]
    C --> D["DashboardController\nextends BaseController\nok - stats"]
    D --> E["DashboardService\ngetStats"]
    E --> F{"Cacheable\nRedis check\ndashboard-stats 8"}
    F -->|"HIT 1ms"| G(["return Map\n0 DB queries"])
    F -->|"MISS"| H(["Hibernate\nqueries start"])

    style A fill:#2d1a4a,color:#fff
    style F fill:#4a3010,color:#fff
    style G fill:#2d4a22,color:#fff
    style H fill:#1a3a4a,color:#fff
```

---

##  Multitenancy Schema Routing

```mermaid
flowchart LR
    A(["▶️ Hibernate\nhap Session"]) --> B["CurrentTenant\nIdentifierResolver\nresolve"]
    B --> C["TenantContext\ngetSchemaName\nThreadLocal"]
    C --> D["tenant_prestige\n_estates_8"]
    D --> E["SchemaMultiTenant\nConnectionProvider\ngetConnection"]
    E --> F["HikariCP\nmerr connection\nnga pool"]
    F --> G["SET search_path TO\ntenant_prestige\n_estates_8 public"]
    G --> H(["✅ Connection\nme schema\nte sakte"])

    style A fill:#1a3a4a,color:#fff
    style C fill:#4a3010,color:#fff
    style D fill:#1a4a3a,color:#fff
    style G fill:#2d1a4a,color:#fff
    style H fill:#2d4a22,color:#fff
```

---

## DB Queries in Tenant Schema

```mermaid
flowchart LR
    A(["▶️ search_path\ntenant_prestige\n_estates_8"]) --> B["COUNT properties\nAVAILABLE\nrezultat 22"]
    B --> C["COUNT properties\nSOLD\nrezultat 4"]
    C --> D["COUNT lease\nACTIVE\nrezultat 4"]
    D --> E["COUNT payments\nOVERDUE\nrezultat 1"]
    E --> F["COUNT leads\nNEW\nrezultat 3"]
    F --> G["SUM payments\nPAID\nrezultat 3250.00"]
    G --> H["Redis.set\ndashboard-stats 8\nTTL=600s"]
    H --> I(["✅ Map stats\ngati"])

    style A fill:#2d1a4a,color:#fff
    style H fill:#4a3010,color:#fff
    style I fill:#2d4a22,color:#fff
```

---

##  Response Returnes Back

```mermaid
flowchart LR
    A(["✅ Map stats"]) --> B["ResponseEntity\nok Map\nHTTP 200"]
    B --> C["Jackson\nserializon\nMap to JSON"]
    C --> D["Security Chain\nunwinds\nHeaderWriter\nSecurityContext clear"]
    D --> E["LoggingFilter\nduration=45ms\nlog 200 OK"]
    E --> F["TenantContext\nclear\nThreadLocal\ncleanup"]
    F --> G["TCP Response\nHTTP 200 OK\napplication/json"]
    G --> H["axios\nstatus 200\nkalon direkt"]
    H --> I["setStats\nres.data"]
    I --> J(["⚛️ React\nre-render\nUI update"])

    style A fill:#2d4a22,color:#fff
    style D fill:#2d1a4a,color:#fff
    style E fill:#4a3010,color:#fff
    style F fill:#4a1a1a,color:#fff
    style J fill:#2d4a22,color:#fff
```

---

##  JWT Structure and Validation

```mermaid
flowchart LR
    A(["🔑 JWT Token\neyJhbGci..."]) --> B["Header\nbase64url\nalg HS256\ntyp JWT"]
    B --> C["Payload\nbase64url\nsub 29\ntenantId 8\nschema tenant_X\nrole ADMIN\nexp +1 ore"]
    C --> D["Signature\nHMACSHA256\nheader.payload\nSECRET_KEY"]
    D --> E["Rillogarit\nHMAC me\nSECRET_KEY"]
    E --> F{"Perputhjet\nme signature\nne token?"}
    F -->|"Jo"| G(["❌ 401\nInvalid"])
    F -->|"Po"| H{"exp\n> now?"}
    H -->|"Jo"| I(["❌ 401\nExpired"])
    H -->|"Po"| J(["✅ Valid\nclaims\nekstraktohen"])

    style A fill:#4a3010,color:#fff
    style D fill:#1a3a4a,color:#fff
    style G fill:#4a1a1a,color:#fff
    style I fill:#4a1a1a,color:#fff
    style J fill:#2d4a22,color:#fff
```

---

##  Access Token vs Refresh Token

```mermaid
flowchart LR
    A(["🔐 LOGIN"]) --> B["Access Token\nJWT 1 ore\nStateless\nLocalStorage"]
    A --> C["Refresh Token\nJWT 7 dite\nDB refresh_tokens\nrevoked=false"]

    B --> D{"Token\nskadon?"}
    D -->|"Jo"| E(["✅ Request OK"])
    D -->|"Po"| F["axios 401\ninterceptor"]
    F --> G["POST auth\nrefresh"]
    G --> H{"DB check\nrevoked?"}
    H -->|"true"| I(["❌ Logout\nforced"])
    H -->|"false"| J["Gjenero\ntoken te ri"]
    J --> K(["✅ Retry OK"])

    C --> L["LOGOUT\nUPDATE SET\nrevoked=true"]
    L --> M(["🚫 Bllokuar\npas 1 ore"])

    style A fill:#2d4a22,color:#fff
    style B fill:#1a3a4a,color:#fff
    style C fill:#4a3010,color:#fff
    style I fill:#4a1a1a,color:#fff
    style M fill:#4a1a1a,color:#fff
    style K fill:#2d4a22,color:#fff
    style E fill:#2d4a22,color:#fff
```

---

##  TenantContext ThreadLocal Lifecycle

```mermaid
flowchart LR
    A(["🔑 JwtAuthFilter\nekzekuton"]) --> B["TenantContext.set\nuserId=29\ntenantId=8\nschema=tenant_X\nrole=ADMIN"]
    B --> C["ThreadLocal\nizoluar per\nkete thread"]
    C --> D["PermissionFilter\nlexon userId"]
    D --> E["TenantIdentifier\nResolver lexon\nschemaName"]
    E --> F["SchemaProvider\nperdor schemaName"]
    F --> G["Service\nlexon userId role"]
    G --> H["Response\nkthehet"]
    H --> I["TenantContext\nclear\nremove ThreadLocals"]
    I --> J(["♻️ Thread i paster\nper request tjeter"])

    style A fill:#4a3010,color:#fff
    style B fill:#1a4a3a,color:#fff
    style C fill:#2d1a4a,color:#fff
    style I fill:#4a1a1a,color:#fff
    style J fill:#2d4a22,color:#fff
```

---

##  Multitenancy Isolation

```mermaid
flowchart LR
    A(["👤 Admin\nTenant 8"]) --> B["JWT\nschema tenant\nprestige 8"]
    C(["👤 Admin\nTenant 9"]) --> D["JWT\nschema tenant\nacme 9"]

    B --> E["TenantContext\nThread 1"]
    D --> F["TenantContext\nThread 2"]

    E --> G["SET search_path\ntenant_prestige\n_estates_8"]
    F --> H["SET search_path\ntenant_acme_9"]

    G --> I[("tenant_prestige\n_estates_8\nproperties\ncontracts")]
    H --> J[("tenant_acme_9\nproperties\ncontracts")]

    I -. "izoluar plotesisht" .- J

    style A fill:#2d4a22,color:#fff
    style C fill:#1a3a4a,color:#fff
    style E fill:#4a3010,color:#fff
    style F fill:#4a3010,color:#fff
    style I fill:#2d1a4a,color:#fff
    style J fill:#2d1a4a,color:#fff
```

## Technology Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 |
| Language | Java 21 |
| Database | PostgreSQL 15 |
| ORM | Hibernate / Spring Data JPA |
| Migrations | Flyway |
| Security | Spring Security + JWT (JJWT 0.12) |
| Authorization | Permission-Based RBAC (Middleware) |
| Caching | Redis (Spring Cache) |
| Background Jobs | Spring Scheduler |
| AI Integration | Groq API (llama-3.1-8b-instant) |
| Documentation | SpringDoc OpenAPI / Swagger UI |
| Build | Maven |

---

## Domain Model

The system is organized around these core domains:

**Properties** — The central entity. Properties have a type, status, listing type (SALE/RENT/BOTH), address, images, features, and price history. Agents create and manage properties. Full-text search is powered by PostgreSQL `tsvector` with a generated column.

**Rental Flow** — An agent creates a RentalListing for a property. Clients submit RentalApplications. When approved, the agent creates a LeaseContract. The contract generates commission Payment records automatically on activation.

**Sale Flow** — An agent creates a SaleListing. Clients submit SaleApplications. The agent creates a SaleContract. On completion, the system generates commission SalePayment records automatically based on whether the property came from a client lead (Scenario 1) or is company-owned (Scenario 2).

**Leads** — Clients submit PropertyLeadRequests expressing interest. Admins assign leads to agents. Agents accept (NEW to IN_PROGRESS), work on them, and close them (DONE or REJECTED). Agents can also decline a lead without rejecting it, which returns the lead to the unassigned pool.

**Payments and Commission** — When a LeaseContract is activated or a SaleContract is completed, the system automatically creates the correct payment records based on the commission model. Scenario 1 applies when the property was sold/rented by a client through the lead system (the original property owner gets a share). Scenario 2 applies to company-owned properties.

**Notifications** — Thirteen service triggers create notifications automatically across the system. MaintenanceService, LeaseContractService, SaleService, LeadService, and RentalService all inject NotificationService and create contextual notifications for agents, clients, and technicians at key workflow steps.

**AI Features** — Six AI-powered endpoints powered by the Groq API. Property description generation, price estimation, client chat assistant, contract summarizer, payment risk analysis, and lead-to-property matching.

---

### Registration — Invitation Only

The system uses invitation-only registration. Public signup is disabled.
Admins generate a secure single-use token via POST /api/invites, which 
produces a link in the format /register?token=abc123.

The token is stored in public.invite_tokens with a 7-day expiry and 
single-use enforcement. When a user registers, the token is marked as 
used inside the same @Transactional block as user creation — if 
registration fails, the token remains valid (automatic rollback).

Navigating to /register without a token redirects immediately to login.

---

## Authentication and Authorization

### Authentication
Authentication uses stateless JWT tokens. On login or register, the system returns an access token (1 hour) and a refresh token (7 days stored in the database). The `JwtAuthFilter` validates every request, extracts claims, populates `TenantContext`, and sets the Spring Security `Authentication` object.

### Authorization — Permission-Based RBAC
Authorization is enforced entirely through middleware — zero `@PreAuthorize` annotations in controllers. The system uses a database-driven RBAC model:

```
REQUEST
    ↓
JwtAuthFilter — validate token, set TenantContext
    ↓
PermissionAuthorizationFilter — query DB, check METHOD + PATH
    ↓
CONTROLLER — zero @PreAuthorize, zero authorization logic
```

Permissions are stored in the `public` schema and consist of an HTTP method and an API path pattern. `AntPathMatcher` handles wildcard matching (e.g. `/api/properties/*`). The permission check uses JDBC directly — not Hibernate — to avoid schema routing conflicts in the multi-tenant setup.

```sql
-- Structure
users → user_roles → roles → role_permissions → permissions(http_method, api_path)
```

Permissions can be granted or revoked at runtime without restarting the application. The `PermissionAdminController` exposes endpoints for managing roles and permissions dynamically.

Roles are `ADMIN`, `AGENT`, and `CLIENT`. Every new user is automatically added to `user_roles` on registration based on their assigned role.

---

## Permission-Based Authorization — RBAC

```mermaid
erDiagram
    users {
        bigint id PK
        string email
        string role
        bigint tenant_id FK
    }

    roles {
        bigint id PK
        string name
        string description
        boolean is_active
    }

    permissions {
        bigint id PK
        string name
        string http_method
        string api_path
        string description
    }

    user_roles {
        bigint user_id FK
        bigint role_id FK
    }

    role_permissions {
        bigint role_id FK
        bigint permission_id FK
    }

    users ||--o{ user_roles : "has"
    roles ||--o{ user_roles : "assigned to"
    roles ||--o{ role_permissions : "grants"
    permissions ||--o{ role_permissions : "granted via"
```

### How authorization flows at runtime

```mermaid
sequenceDiagram
    participant C as Client
    participant J as JwtAuthFilter
    participant P as PermissionAuthorizationFilter
    participant DB as public schema (PostgreSQL)
    participant API as Controller

    C->>J: HTTP Request + Bearer JWT
    J->>J: validate token (JJWT)
    J->>J: extract userId, schemaName, role
    J->>J: set TenantContext (ThreadLocal)
    J->>P: pass to next filter

    P->>DB: SELECT p.http_method, p.api_path\nFROM permissions p\nJOIN role_permissions rp ON rp.permission_id = p.id\nJOIN user_roles ur ON ur.role_id = rp.role_id\nWHERE ur.user_id = ?

    DB-->>P: list of allowed METHOD + PATH pairs

    alt permission found (AntPathMatcher)
        P->>API: request passes through
        API-->>C: 200 OK + data
    else no matching permission
        P-->>C: 403 Forbidden
    end
```

---

### Impersonation — Admin Acting as Agent/Client

Admins can impersonate any user within the same tenant via:

POST /api/auth/impersonate/{userId}

The endpoint returns a new JWT token scoped to the target user's 
role and schema. The token contains an additional claim 
(impersonated_by: adminId) for audit purposes. JwtAuthFilter 
logs a WARN when an impersonation token is detected:

IMPERSONATION ACTIVE — admin=7 acting as userId=15

Impersonation is tenant-scoped — admins cannot impersonate users 
from other tenants.

---

## BaseController — OOP/DRY Pattern

All controllers except `AuthController` extend `BaseController`, which centralizes common response-building logic:

```java
public abstract class BaseController {
    protected <T> ResponseEntity<T> ok(T body)          // 200 OK
    protected <T> ResponseEntity<T> created(T body)     // 201 Created
    protected ResponseEntity<Void> noContent()           // 204 No Content
    protected PageRequest page(int page, int size)
    protected PageRequest page(int page, int size, String sortBy, String sortDir)
}
```

This eliminates repeated `ResponseEntity.ok(...)`, `PageRequest.of(...)`, and `HttpStatus.CREATED` boilerplate across all controllers — roughly 30-40% less code per controller — with zero impact on endpoints, Swagger documentation, or frontend behavior.

`AuthController` does not extend `BaseController` because it contains specific logic (`getClientIp()`) that belongs only to the authentication flow.

---

## Caching — Redis

Notification counts, and dashboard statistics 
are cached in Redis with a 10-minute TTL:

User → GET /api/dashboard/stats → miss → aggregate DB queries → cache per tenant
User → GET /api/dashboard/stats → hit  → return from Redis (cache key = tenantId)

@Cacheable is applied to read-heavy endpoints. @CacheEvict invalidates 
the cache automatically on create, update, and delete operations.
Dashboard stats cache is evicted automatically when underlying data changes.
@EnableCaching is configured on BackendApplication.

---

## Background Jobs — Spring Scheduler

Four scheduled jobs run automatically in the background across all active tenant schemas:

| Job | Schedule | Action |
|---|---|---|
| `markOverduePayments` | Daily 00:00 | Marks PENDING payments past due date as OVERDUE |
| `checkExpiringContracts` | Daily 08:00 | Logs contracts expiring within 30 days |
| `logSystemStats` | Every 6 hours | Logs active lease count per tenant |
| `healthCheck` | Every 60 seconds | Logs active schema count |

Each job iterates all provisioned tenant schemas, sets `TenantContext`, executes the operation, and clears the context in a `finally` block. `@EnableScheduling` is configured on `BackendApplication`.

---

## Commission Logic

**Rental (triggered on LeaseContract PENDING_SIGNATURE to ACTIVE):**

```
Commission Total = Monthly Rent x 3%
Owner Amount     = Monthly Rent x 97%

Scenario 1 (property came from a completed lead):
  RENT                 -> property owner (97%)
  COMMISSION 50%       -> company
  AGENT_COMMISSION 40% -> agent
  CLIENT_BONUS 10%     -> property owner

Scenario 2 (company-owned property):
  RENT                 -> company (97%)
  COMMISSION 60%       -> company
  AGENT_COMMISSION 40% -> agent
```

**Sale (triggered on SaleContract PENDING to COMPLETED):**

Same structure as rental but applied to the total sale price instead of monthly rent.

---

## API Overview

The API exposes over 60 REST endpoints. All endpoints require a Bearer JWT token except `/api/auth/**`.

| Module | Base Path |
|---|---|
| Authentication | /api/auth |
| Properties | /api/properties |
| Property Images | /api/properties/{id}/images |
| Rental Listings | /api/rentals/listings |
| Rental Applications | /api/rentals/applications |
| Lease Contracts | /api/contracts/lease |
| Payments | /api/payments |
| Sale Listings | /api/sales/listings |
| Sale Applications | /api/sales/applications |
| Sale Contracts | /api/sales/contracts |
| Sale Payments | /api/sales/payments |
| Leads | /api/leads |
| Maintenance | /api/maintenance |
| Notifications | /api/notifications |
| Users and Profiles | /api/users |
| Tenants | /api/admin/tenants |
| Permission Management | /api/admin |
| AI Features | /api/ai |

Full interactive documentation is available at `http://localhost:8080/swagger-ui.html` when the application is running.

---

## Setup and Running

### Prerequisites

- Java 21
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+

### Database Setup

```sql
CREATE USER realestate_user WITH PASSWORD 'realestate_pass';
CREATE DATABASE realestate_db OWNER realestate_user;
GRANT ALL PRIVILEGES ON DATABASE realestate_db TO realestate_user;
```

### Configuration

The application reads from `src/main/resources/application.yml`. Key configuration values:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/realestate_db
    username: realestate_user
    password: realestate_pass
  cache:
    type: redis
    redis:
      time-to-live: 600000
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: your-256-bit-secret-key-minimum-32-characters
  expiration-ms: 3600000
  refresh-expiration-ms: 604800000

groq:
  api:
    key: your-groq-api-key   # use "placeholder" for mock responses

app:
  upload:
    dir: ./uploads
```

### Running

```bash
mvn spring-boot:run
```

Flyway runs automatically on startup and applies all pending migrations. New tenant schemas are provisioned on first registration.

---

## Key Design Decisions

**Schema isolation over row-level security** — Each tenant gets a fully isolated PostgreSQL schema. This eliminates the risk of data leakage between tenants through query bugs and makes it straightforward to back up or delete a single tenant's data without affecting others.

**Cross-schema foreign keys via Long columns** — Entities that reference `public.users` (such as `agent_id`, `client_id`) store the ID as a plain `Long` column rather than a JPA `@ManyToOne`. This avoids Hibernate attempting to join across schema boundaries, which would fail at the connection level.

**JWT contains all routing information** — The token carries `userId`, `tenantId`, `schemaName`, and `role`. This means every request is self-contained. No database lookup is needed to identify the tenant or authorize the user at the filter level.

**JDBC direct in security middleware** — `PermissionAuthorizationFilter` and `PermissionRepository` use JDBC directly instead of JPA/Hibernate. This prevents Hibernate from applying the tenant `search_path` to permission queries, which must always read from the `public` schema regardless of the current tenant context.

**Permission-based authorization over @PreAuthorize** — Permissions are stored in the database and checked at the middleware level. This means access control can be modified at runtime without code changes or application restarts. Granting or revoking a permission is a single SQL statement.

**Commission payments are immutable records** — When a contract completes, the system creates explicit Payment or SalePayment rows for each recipient. These records are never modified retroactively. This creates a clear audit trail of who received what and when.

**Soft deletes on core entities** — Properties, rental listings, and sale listings use a `deleted_at` timestamp column instead of hard deletes. All queries filter by `deleted_at IS NULL`. This preserves historical data and prevents orphaned references in contracts and applications.

**BaseController for response consistency** — All controllers extend `BaseController` which provides unified helpers for building HTTP responses. This enforces consistent response patterns across the entire API and reduces boilerplate by 30-40% per controller with zero impact on endpoints or Swagger documentation.
