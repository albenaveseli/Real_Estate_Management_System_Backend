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
## Request Lifecycle — Real Estate Management System

---

### Frontend Overview

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

### Tomcat Acceptance

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

### Servlet Filter Chain

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

### Spring Security Filter Chain

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

### JwtAuthFilter Details

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

### PermissionAuthorizationFilter Details

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

###  DispatcherServlet Controller Service

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

###  Multitenancy Schema Routing

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

### DB Queries in Tenant Schema

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

###  Response Returnes Back

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

###  JWT Structure and Validation

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

###  Access Token vs Refresh Token

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

###  TenantContext ThreadLocal Lifecycle

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

###  Multitenancy Isolation

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

## Impersonation Lifecycle 

---

###  Impersonation Overview

```mermaid
flowchart LR
    A(["👤 Admin\nklikon Impersonate\nte Anita id=30"]) --> B["AuthProvider\nstartImpersonation\n30"]
    B --> C["api.post\nadmin impersonate 30\nme admin token"]
    C --> D["Backend\nvalidizon\ngeneron token"]
    D --> E["Impersonation\nToken\nme impersonatedBy=29"]
    E --> F["localStorage\nswap tokens"]
    F --> G(["👤 Sistemi\nfunksionon\nsi Anita"])

    style A fill:#2d4a22,color:#fff
    style E fill:#4a3010,color:#fff
    style F fill:#1a3a4a,color:#fff
    style G fill:#4a1a1a,color:#fff
```

---

### Frontend — Before Request

```mermaid
flowchart LR
    A(["AdminAllAgents.jsx\nbuton Impersonate"]) --> B["AuthProvider\nstartImpersonation\nuserId=30"]
    B --> C["api.post\nadmin impersonate 30"]
    C --> D["axios interceptor\nAuthorization\nBearer adminToken\nuserId=29"]
    D --> E(["POST\nadmin impersonate 30\nme admin JWT"])

    style A fill:#2d4a22,color:#fff
    style D fill:#4a3010,color:#fff
    style E fill:#1a3a4a,color:#fff
```

---

### Filter Chain with Admin Token

```mermaid
flowchart LR
    A(["▶️ POST\nadmin impersonate 30"]) --> B["CorsFilter\nOrigin OK"]
    B --> C["LoggingFilter\nstartTime\nPAUSE"]
    C --> D["JwtAuthFilter\nparse adminToken\nuserId=29\nrole=ADMIN"]
    D --> E["TenantContext.set\nuserId=29\ntenantId=8\nschema=tenant_X\nrole=ADMIN"]
    E --> F["AuthorizationFilter\nauthenticated OK"]
    F --> G["PermissionFilter\nJDBC permissions\nuser_id=29\nPOST impersonate OK"]
    G --> H(["ImpersonationController\nimpersonate 30"])

    style A fill:#1a3a4a,color:#fff
    style D fill:#4a1a1a,color:#fff,stroke:#ff6b6b,stroke-width:2px
    style E fill:#1a4a3a,color:#fff
    style G fill:#2d1a4a,color:#fff
    style H fill:#2d4a22,color:#fff
```

---

### ImpersonationController — Validation

```mermaid
flowchart LR
    A(["impersonate\nuserId=30"]) --> B{"TenantContext\ngetRole\n= ADMIN?"}
    B -->|"Jo"| C(["403\nOnly ADMIN\ncan impersonate"])
    B -->|"Po"| D["userRepository\nfindById 30\nSELECT public.users"]
    D --> E{"target.getRole\n= ADMIN?"}
    E -->|"Po"| F(["403\nCannot impersonate\nanother ADMIN"])
    E -->|"Jo AGENT"| G{"target.getTenant\n= TenantContext\ngetTenantId?"}
    G -->|"Jo cross-tenant"| H(["403\nCannot impersonate\ndifferent tenant"])
    G -->|"Po 8 = 8"| I["schemaRegistry\nfindByTenant_Id 8\nschema=tenant_X"]
    I --> J(["✅ Gjenero\nImpersonation Token"])

    style A fill:#2d4a22,color:#fff
    style C fill:#4a1a1a,color:#fff
    style F fill:#4a1a1a,color:#fff
    style H fill:#4a1a1a,color:#fff
    style J fill:#1a3a4a,color:#fff
```

---

### JWT Impersonation Token Generation

```mermaid
flowchart LR
    A(["✅ Validimi\nKaloi"]) --> B["jwtUtil\ngenerateImpersonation\nToken"]
    B --> C["Payload\nsub=30\nemail=anita\ntenantId=8\nschema=tenant_X\nrole=AGENT\nimpersonatedBy=29"]
    C --> D["HMACSHA256\nheader.payload\nSECRET_KEY"]
    D --> E["eyJhbGci...\nimpersonationToken"]
    E --> F["log.warn\nIMPERSONATION STARTED\nadmin=29\nimpersonating=30"]
    F --> G(["HTTP 200\ntoken\nemail\nrole\nfull_name"])

    style A fill:#2d4a22,color:#fff
    style C fill:#4a3010,color:#fff
    style E fill:#1a3a4a,color:#fff
    style F fill:#4a1a1a,color:#fff
    style G fill:#2d4a22,color:#fff
```

---

### Frontend — Token Swap

```mermaid
flowchart LR
    A(["HTTP 200\ndata.token\ndata.email\ndata.role"]) --> B["localStorage.set\nadmin_token\n= access_token backup"]
    B --> C["localStorage.set\nadmin_user_info\n= user_info backup"]
    C --> D["localStorage.set\naccess_token\n= impersonationToken"]
    D --> E["localStorage.set\nuser_info\nid=30 role=agent"]
    E --> F["localStorage.set\nimpersonating\nemail=anita role=AGENT"]
    F --> G["setUser\nimpersonatedUser"]
    G --> H["setImpersonating\nemail role"]
    H --> I(["window.location\nhref = /agent\nFULL RELOAD"])

    style A fill:#1a3a4a,color:#fff
    style B fill:#4a3010,color:#fff
    style C fill:#4a3010,color:#fff
    style D fill:#4a1a1a,color:#fff
    style I fill:#2d4a22,color:#fff
```

---

### During Impersonation — Every Request

```mermaid
flowchart LR
    A(["Çdo request\nsi Anita"]) --> B["axios interceptor\nAuthorization\nBearer impersonationToken"]
    B --> C["JwtAuthFilter\nparse token\nsub=30\nrole=AGENT\nimpersonatedBy=29"]
    C --> D["log.warn\nIMPERSONATION ACTIVE\nadmin=29 acting\nas userId=30"]
    D --> E["TenantContext.set\nuserId=30\nrole=AGENT\nschema=tenant_X"]
    E --> F["PermissionFilter\nJDBC permissions\nuser_id=30\nAGENT permissions"]
    F --> G(["Sistemi funksionon\nplotesisht si Anita\nAGENT permissions only"])

    style A fill:#4a1a1a,color:#fff
    style C fill:#4a3010,color:#fff
    style D fill:#4a1a1a,color:#fff,stroke:#ff6b6b,stroke-width:2px
    style E fill:#1a4a3a,color:#fff
    style G fill:#2d4a22,color:#fff
```

---

### Exit Impersonation — Frontend

```mermaid
flowchart LR
    A(["👤 Admin klikon\nStop Impersonating"]) --> B["AuthProvider\nexitImpersonation"]
    B --> C["api.post\nadmin impersonate exit\nme impersonationToken"]
    C --> D["Backend log.info\nIMPERSONATION EXIT\nuserId=30\nreturn 204"]
    D --> E["localStorage.set\naccess_token\n= admin_token restore"]
    E --> F["localStorage.set\nuser_info\n= admin_user_info restore"]
    F --> G["localStorage.remove\nadmin_token\nadmin_user_info\nimpersonating"]
    G --> H["setUser\nadminUserInfo"]
    H --> I["setImpersonating\nnull"]
    I --> J(["window.location\nhref = /admin\nFULL RELOAD"])

    style A fill:#2d4a22,color:#fff
    style D fill:#1a3a4a,color:#fff
    style E fill:#4a3010,color:#fff
    style G fill:#4a1a1a,color:#fff
    style J fill:#2d4a22,color:#fff
```

---

### After Exit - Admin is back

```mermaid
flowchart LR
    A(["Çdo request\npas exit"]) --> B["axios interceptor\nAuthorization\nBearer adminToken\noriginal"]
    B --> C["JwtAuthFilter\nparse adminToken\nsub=29\nrole=ADMIN\nimpersonatedBy=null"]
    C --> D{"impersonatedBy\nnull?"}
    D -->|"Po"| E["TenantContext.set\nuserId=29\nrole=ADMIN\nschema=tenant_X"]
    D -->|"Jo"| F["log.warn\nIMPERSONATION ACTIVE"]
    E --> G["PermissionFilter\nADMIN permissions\nplots"]
    G --> H(["✅ Admin\ni rikthyer\nplotesisht"])

    style A fill:#2d4a22,color:#fff
    style C fill:#4a3010,color:#fff
    style E fill:#1a4a3a,color:#fff
    style F fill:#4a1a1a,color:#fff
    style H fill:#2d4a22,color:#fff
```

---

## localStorage State

## Para / Gjate / Pas Impersonation

```mermaid
flowchart LR
    subgraph PARA[Para Impersonation]
        A1["access_token\nadminToken\nuserId=29"]
        A2["refresh_token\nadminRefresh"]
        A3["user_info\nid=29 role=admin"]
    end

    subgraph GJATE[Gjate Impersonation]
        B1["access_token\nimpersonToken\nuserId=30"]
        B2["refresh_token\nadminRefresh"]
        B3["user_info\nid=30 role=agent"]
        B4["admin_token\nBACKUP adminToken"]
        B5["admin_user_info\nBACKUP id=29"]
        B6["impersonating\nemail=anita\nrole=AGENT"]
    end

    subgraph PAS[Pas Exit]
        C1["access_token\nadminToken\nuserId=29"]
        C2["refresh_token\nadminRefresh"]
        C3["user_info\nid=29 role=admin"]
    end

    A1 --> B1
    A2 --> B2
    A3 --> B3
    B4 --> C1
    B2 --> C2
    B5 --> C3

    style B1 fill:#4a1a1a,color:#fff
    style B4 fill:#4a3010,color:#fff
    style B5 fill:#4a3010,color:#fff
    style B6 fill:#4a1a1a,color:#fff
    style A1 fill:#1a3a4a,color:#fff
    style A2 fill:#1a3a4a,color:#fff
    style A3 fill:#1a3a4a,color:#fff
    style C1 fill:#2d4a22,color:#fff
    style C2 fill:#2d4a22,color:#fff
    style C3 fill:#2d4a22,color:#fff
```

---

### Security 

```mermaid
flowchart LR
    A(["POST\nimpersonate\nuserId"]) --> B{"Kush\nben kerkesen?"}
    B -->|"Jo ADMIN"| C(["403\nOnly ADMIN\ncan impersonate"])
    B -->|"ADMIN"| D{"Target\eshte ADMIN?"}
    D -->|"Po"| E(["403\nCannot impersonate\nADMIN"])
    D -->|"Jo"| F{"Same\nTenant?"}
    F -->|"Jo"| G(["403\nCross-tenant\nbllokuar"])
    F -->|"Po"| H["✅ Te gjitha\nkontrollet kaluan"]
    H --> I["Gjenero token\nme impersonatedBy\nclaim per audit"]
    I --> J(["Token i ri\njeton 1 ore\nautomatik skadon"])

    style C fill:#4a1a1a,color:#fff
    style E fill:#4a1a1a,color:#fff
    style G fill:#4a1a1a,color:#fff
    style H fill:#2d4a22,color:#fff
    style J fill:#1a3a4a,color:#fff
```
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

# Caching Architecture — DashboardService

## How @Cacheable works (Cache Hit and Cache Miss)

```mermaid
flowchart TD
    A[AdminController\nthërret getStats] --> B

    B{CGLIB Proxy\nndërkap thirrjen\nkey = tenantId = 8}

    B -->|kontrollo| C[(Redis\nGET dashboard-stats 8)]

    C -->|HIT ~1ms\nzero DB queries| G[Response\nte Controller]

    C -->|MISS\nnuk ekziston| D[DashboardService\nmetoda reale]

    D --> E[(PostgreSQL\n7 queries\ncountByStatus x4\ncountLeads\ntotalRevenue)]

    E -->|rezultati| D

    D -->|Map rezultati| F[(Redis\nSET dashboard-stats 8\nbytes JSON)]

    F --> G

    style B fill:#534AB7,color:#EEEDFE
    style C fill:#BA7517,color:#FAEEDA
    style D fill:#185FA5,color:#E6F1FB
    style E fill:#5F5E5A,color:#F1EFE8
    style F fill:#BA7517,color:#FAEEDA
    style G fill:#0F6E56,color:#E1F5EE
    style A fill:#0F6E56,color:#E1F5EE
```

> **Cache Miss** — First time: Redis doesn’t have it → 7 queries → stored in Redis → ~50ms  
> **Cache Hit** — Subsequent times: Redis has it → returned directly → zero DB queries → ~1ms

---

## How @CacheEvict works (evict and evictAll)

```mermaid
flowchart LR
    subgraph services["Services që ndryshojnë të dhëna"]
        S1[PropertyService\nupdateStatus / delete]
        S2[PaymentService\nmarkAsPaid / create]
        S3[ContractService\ncreate / updateStatus]
    end

    S1 --> P
    S2 --> P
    S3 --> P

    P{CGLIB Proxy\n@CacheEvict\nkey = tenantId = 8}

    P -->|DEL dashboard-stats 8| R1[(Redis\nvetëm tenant 8\nfshihet)]

    subgraph scheduler["Scheduler — pa TenantContext"]
        SC[SchedulerService\nmarkOverduePayments\nevictAll]
    end

    SC --> P2{CGLIB Proxy\nallEntries = true}

    P2 -->|DEL të gjitha| R2[(Redis\ntë gjitha keys\nfshihen)]

    style P fill:#534AB7,color:#EEEDFE
    style P2 fill:#534AB7,color:#EEEDFE
    style R1 fill:#A32D2D,color:#FCEBEB
    style R2 fill:#A32D2D,color:#FCEBEB
    style S1 fill:#185FA5,color:#E6F1FB
    style S2 fill:#185FA5,color:#E6F1FB
    style S3 fill:#185FA5,color:#E6F1FB
    style SC fill:#BA7517,color:#FAEEDA
```

> **evict()** — Removes one specific cached value based on a key (from TenantContext)  
> **evictAll()** — Removes all entries inside the cache namespace. (used by Scheduler)

---

## How CGLIB Proxy works

```mermaid
flowchart TD
    subgraph noproxy["Pa proxy — @Cacheable injorohet"]
        A1[Controller] -->|thirrje direkte| B1[DashboardService]
        B1 --> C1[(DB — gjithmonë)]
    end

    subgraph withproxy["Me proxy — Spring e ndërkap thirrjen"]
        A2[Controller] -->|mendon se flet me Service| PX

        PX["CGLIB Proxy\nextends DashboardService\n\n1. kontrollo Redis\n2. nëse HIT → kthe direkt\n3. nëse MISS → thirr super"]

        PX -->|HIT| RD[(Redis\nkthe direkt)]
        PX -->|MISS\nthirr super.getStats| B2[DashboardService\norigjinali\n7 queries]
        B2 --> DB2[(PostgreSQL)]
        DB2 --> B2
        B2 -->|rezultati| PX
        PX -->|ruaj + kthe| A2
    end

    style PX fill:#534AB7,color:#EEEDFE
    style RD fill:#BA7517,color:#FAEEDA
    style B2 fill:#185FA5,color:#E6F1FB
    style DB2 fill:#5F5E5A,color:#F1EFE8
    style B1 fill:#185FA5,color:#E6F1FB
    style C1 fill:#5F5E5A,color:#F1EFE8
    style A1 fill:#0F6E56,color:#E1F5EE
    style A2 fill:#0F6E56,color:#E1F5EE
```

> **The controller thinks it is talking to the DashboardService — but in reality, it is talking to the proxy.** 
> Spring generates automatically `DashboardService$$SpringCGLIB$$0 extends DashboardService`

---

## What happens “behind the scenes” — CGLIB Proxy

```java
// Ti shkruan:
@Cacheable(...)
public Map<String, Object> getStats() { ... }

// Spring gjeneron automatikisht (nuk e sheh ti):
class DashboardService$$SpringCGLIB$$0 extends DashboardService {

    @Override
    public Map<String, Object> getStats() {

        // HAPI 1: Gjenero key
        Long tenantId = TenantContext.getTenantId(); // → 8
        String cacheKey = "dashboard-stats::" + tenantId;

        // HAPI 2: Kontrollo Redis
        Object cached = redis.get(cacheKey);

        if (cached != null) {
            // CACHE HIT — metoda origjinale NUK ekzekutohet
            return deserialize(cached); // ~1ms
        }

        // CACHE MISS — ekzekuto metodën origjinale
        Map<String, Object> result = super.getStats(); // ~50ms, 7 queries

        // HAPI 3: Ruaj në Redis
        redis.set(cacheKey, serialize(result));

        return result;
    }
}

// Spring inject-on PROXY-n (jo origjinalin):
// @Autowired DashboardService service;
// → në realitet është DashboardService$$SpringCGLIB$$0
```

---

## Performance

| Scenario                        | DB Queries | Time      | When it happens                |
| ------------------------------- | ---------- | --------- | ------------------------------ |
| Cache MISS                      | 7 queries  | ~50ms     | First time, after evict()      |
| Cache HIT                       | 0 queries  | ~1ms      | Every subsequent call          |
| 10 admins simultaneously (HIT)  | 0 queries  | ~1ms each | All users get data from Redis  |
| 10 admins simultaneously (MISS) | 7 queries  | ~50ms     | Only one executes, others wait |

---

## Background Jobs — Spring Scheduler

| Job | Trigger | What it does |
|-----|---------|-------------|
| `markOverduePayments` | every day at **00:00** | Finds all PENDING payments past due date → marks them OVERDUE → notifies admins |
| `checkExpiringContracts` | every day at **08:00** | Finds ACTIVE leases expiring within 30 days → notifies admins |
| `weeklyAdminReport` | every **Monday at 09:00** | Sends a combined summary: overdue count + expiring contracts + unassigned leads |
| `logSystemStats` | every **6 hours** | Logs active lease count per tenant (monitoring only, no notifications) |
| `healthCheck` | every **60 seconds** | Logs a heartbeat so ops can confirm the scheduler thread is alive |

---
# Scheduler Internals — How Spring Knows When to Run

No HTTP request triggers these jobs. Spring wakes up a background thread at the
exact configured time, completely on its own. Here is exactly how that works.

---

## 1. Startup — registering tasks

When the application starts, `@EnableScheduling` tells Spring to scan every
`@Service` and `@Component` for `@Scheduled` methods and register them into a
`DelayQueue` ordered by next execution time.

```mermaid
flowchart LR
    A[Application starts\n@EnableScheduling] --> B[Spring scans all\n@Scheduled methods]
    B --> C[Builds ScheduledTask list\nmethod + trigger + next run time]
    C --> D[(DelayQueue\nordered by next execution time)]

    style A fill:#534AB7,color:#EEEDFE
    style D fill:#BA7517,color:#FAEEDA
```

---

## 2. The DelayQueue — priority queue by time

All scheduled tasks live in a single `DelayQueue`. The item with the nearest
execution time is always at the front. The timer thread blocks on `.take()` —
it does not spin or consume CPU — it simply sleeps until the front item is ready.

```mermaid
block-beta
  columns 1
  Q["DelayQueue (ordered by next run time)"]
  block:entries
    E1["[00:00:00]  markOverduePayments  — cron 0 0 0 * * *"]
    E2["[08:00:00]  checkExpiringContracts — cron 0 0 8 * * *"]
    E3["[09:00 MON] weeklyAdminReport — cron 0 0 9 * * MON"]
    E4["[now + 55s]  healthCheck — fixedDelay 60 000 ms"]
    E5["[now + 6h]   logSystemStats — fixedDelay 21 600 000 ms"]
  end
```

---

## 3. cron vs fixedDelay — key difference

```mermaid
flowchart TD
    subgraph cron["cron — fires at a wall-clock time"]
        C1[job starts\n00:00:00] --> C2[job runs\n10 minutes]
        C2 --> C3[CronTrigger calculates\nnext run = tomorrow 00:00:00]
        C3 --> C4[re-queued regardless\nof how long job took]
    end

    subgraph fixed["fixedDelay — fires N ms after the previous run ends"]
        F1[job starts] --> F2[job runs\n50 ms]
        F2 --> F3[wait exactly\n60 000 ms]
        F3 --> F4[fire again\ntotal gap = 60 050 ms]
    end

    style C1 fill:#185FA5,color:#E6F1FB
    style F1 fill:#0F6E56,color:#E1F5EE
```

> `cron` is anchored to the clock — always midnight, regardless of runtime.
> `fixedDelay` is anchored to the previous execution — the gap is always the same *after* the job finishes.

---

## 4. Runtime architecture — timer thread + thread pool

```mermaid
flowchart TD
    subgraph executor["ScheduledExecutorService"]
        TT["Timer Thread\n(sleeps inside DelayQueue.take)\nwakes only when next item is ready"]
        TT -->|item ready| TP

        subgraph pool["Thread Pool  poolSize = 3"]
            T1["S-Thread-1\nBUSY — markOverduePayments"]
            T2["S-Thread-2\nBUSY — healthCheck"]
            T3["S-Thread-3\nFREE — waiting"]
        end
    end

    DQ[(DelayQueue)] -->|unblocks .take| TT

    style TT fill:#534AB7,color:#EEEDFE
    style T1 fill:#A32D2D,color:#FCEBEB
    style T2 fill:#A32D2D,color:#FCEBEB
    style T3 fill:#0F6E56,color:#E1F5EE
    style DQ fill:#BA7517,color:#FAEEDA
```

> The timer thread itself never runs job logic — it only picks tasks off the queue
> and hands them to the thread pool. Job logic runs on pool threads (`S-Thread-N`).

---

## 5. TenantContext isolation between parallel threads

Because `TenantContext` is a `ThreadLocal`, two threads running at the same time
never share tenant state. Each thread has its own private slot.

```mermaid
flowchart LR
    subgraph T1["S-Thread-1"]
        A1[TenantContext\n= tenant_prestige_8]
        A2[SET search_path\nTO tenant_prestige_8]
        A3[SELECT * FROM properties\n→ prestige_8.properties]
    end

    subgraph T2["S-Thread-2"]
        B1[TenantContext\n= tenant_rose_1]
        B2[SET search_path\nTO tenant_rose_1]
        B3[SELECT * FROM properties\n→ rose_1.properties]
    end

    A1 --> A2 --> A3
    B1 --> B2 --> B3

    style A1 fill:#534AB7,color:#EEEDFE
    style B1 fill:#185FA5,color:#E6F1FB
```

> `ThreadLocal` guarantees `S-Thread-1.TenantContext ≠ S-Thread-2.TenantContext`.
> Zero interference between tenants even when jobs run in parallel.

---

## 6. What happens with poolSize = 1 (default)

```mermaid
gantt
    title Thread contention with poolSize = 1
    dateFormat mm:ss
    axisFormat %M:%S

    section S-Thread-1
    markOverduePayments (20s) : active, 00:00, 20s
    healthCheck (waiting)     : crit,   00:00, 20s
    healthCheck runs          : active, 00:20, 1s
```

> With `poolSize=1`, `healthCheck` must wait for `markOverduePayments` to finish.

---

## 7. After execution — the re-queue loop

```mermaid
flowchart LR
    A[job finishes] --> B{trigger type?}
    B -->|cron| C[CronTrigger.nextExecutionTime\ncalculates next wall-clock time]
    B -->|fixedDelay| D[wait exactly N ms\nafter this finish time]
    C --> E[(re-insert into DelayQueue)]
    D --> E
    E --> F[timer thread sleeps\nuntil item is ready]
    F --> G[cycle repeats forever]

    style E fill:#BA7517,color:#FAEEDA
```

---

## Configuration

```properties
# application.yml

# Allow jobs to run in parallel (default = 1)
spring.task.scheduling.pool.size=3
```

```java
// BackendApplication.java — required to activate @Scheduled
@EnableScheduling
@SpringBootApplication
public class BackendApplication { }
```
---

## Why TenantContext is set manually

Normal HTTP requests have a JWT token. `JwtAuthFilter` decodes it and calls
`TenantContext.set(userId, tenantId, schemaName, role)` automatically.

The scheduler has **no JWT and no HTTP request**. If `TenantContext` were left empty,
`TenantIdentifierResolver` would return `null`, Hibernate would route every query to the
`public` schema, and no tenant data would be found.

The fix is to set it manually before each tenant and clear it in `finally`:

```java
for (var schema : activeSchemas()) {
    try {
        // Manually route Hibernate to this tenant's schema
        TenantContext.set(null, null, schema.getSchemaName(), "SYSTEM");
        //             ↑     ↑      ↑                         ↑
        //          userId tenantId  schema name             role
        //          null   null   "tenant_prestige_8"      "SYSTEM"
        //
        // Hibernate now runs:
        //   SET LOCAL search_path TO "tenant_prestige_8", public
        // All queries go to the correct tenant tables.

        // ... job logic ...

    } catch (Exception e) {
        log.error("[Scheduler] Error for schema={}: {}", schema.getSchemaName(), e.getMessage());
        // Do NOT rethrow — the loop must continue for the remaining tenants.

    } finally {
        TenantContext.clear();
        // Always runs, even on exception.
        // The thread goes back to the pool completely clean.
    }
}
```

---

## Why evictAll() instead of evict()

```java
// evict() uses the current tenant's ID as the cache key:
@CacheEvict(key = "T(TenantContext).getTenantId()")
// → TenantContext.getTenantId() returns null in the scheduler (no tenantId set)
// → cache key becomes "dashboard-stats::null" — wrong or broken

// evictAll() ignores the key entirely:
@CacheEvict(allEntries = true)
// → deletes every entry in "dashboard-stats"
// → safe to call with no TenantContext
dashboardService.evictAll(); // used by scheduler
dashboardService.evict();    // used by regular service calls (has tenantId)
```
---

## Cron syntax reference

```
"0 0 0 * * *"
 │ │ │ │ │ └── day of week  (* = every day)
 │ │ │ │ └──── month        (* = every month)
 │ │ │ └────── day of month (* = every day)
 │ │ └──────── hour         (0 = midnight)
 │ └────────── minute       (0)
 └──────────── second       (0)

Examples used in this project:
  "0 0 0 * * *"     → every day at 00:00:00
  "0 0 8 * * *"     → every day at 08:00:00
  "0 0 9 * * MON"   → every Monday at 09:00:00
  fixedDelay=60000  → 60 seconds after the previous execution ends
  fixedDelay=21600000 → 6 hours after the previous execution ends
```

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

**JDBC direct in security middleware** — `PermissionAuthorizationFilter` and `PermissionRepository` use raw JDBC via `DataSource` instead of extending `JpaRepository` for three reasons: (1) Hibernate routes all queries through `CurrentTenantIdentifierResolver` which would apply the tenant `search_path` to permission tables that must always read from `public`; (2) the filter executes before any Spring-managed transaction exists, making `EntityManager` unavailable; (3) raw JDBC skips JPQL parsing, entity mapping, and schema resolution — reducing overhead on the most frequently called query in the application.

**Permission-based authorization over @PreAuthorize** — Permissions are stored in the database and checked at the middleware level. This means access control can be modified at runtime without code changes or application restarts. Granting or revoking a permission is a single SQL statement.

**Commission payments are immutable records** — When a contract completes, the system creates explicit Payment or SalePayment rows for each recipient. These records are never modified retroactively. This creates a clear audit trail of who received what and when.

**Soft deletes on core entities** — Properties, rental listings, and sale listings use a `deleted_at` timestamp column instead of hard deletes. All queries filter by `deleted_at IS NULL`. This preserves historical data and prevents orphaned references in contracts and applications.



**BaseController for response consistency** — All controllers extend `BaseController` which provides unified helpers for building HTTP responses. This enforces consistent response patterns across the entire API and reduces boilerplate by 30-40% per controller with zero impact on endpoints or Swagger documentation.
