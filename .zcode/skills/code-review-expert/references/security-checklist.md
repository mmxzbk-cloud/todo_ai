# Security and Reliability Checklist

## Input/Output Safety

- **XSS**: Unsafe HTML injection, `dangerouslySetInnerHTML`, unescaped templates, innerHTML assignments
- **Injection (JS)**: SQL/NoSQL/command/GraphQL injection via string concatenation or template literals
- **Injection (Java)**: MyBatis `${}` interpolation instead of `#{}` parameter binding; JPQL/HQL string concatenation; `Runtime.exec()` with unsanitized input
- **SSRF**: User-controlled URLs reaching internal services without allowlist validation
- **Path traversal**: User input in file paths without sanitization (`../` attacks)
- **Prototype pollution (JS)**: Unsafe object merging (`Object.assign`, spread with user input)
- **Unsafe deserialization (Java)**: `ObjectInputStream.readObject()` on untrusted data; Jackson `@JsonTypeInfo` with `DefaultTyping`; Fastjson `autoType`
- **SpEL injection (Java)**: User input evaluated as Spring Expression Language (`#{...}`)

## AuthN/AuthZ

- Missing tenant or ownership checks for read/write operations
- New endpoints without auth guards or RBAC enforcement
- Trusting client-provided roles/flags/IDs
- Broken access control (IDOR - Insecure Direct Object Reference)
- Session fixation or weak session management
- **(Java)** Missing `@PreAuthorize` / `@Secured` on new Controller endpoints
- **(Java)** `@TenantId` not applied on multi-tenant entities; tenant filter bypassed in custom SQL
- **(Java)** Spring Security filter chain misconfiguration (`.permitAll()` on sensitive paths)

## JWT & Token Security

- Algorithm confusion attacks (accepting `none` or `HS256` when expecting `RS256`)
- Weak or hardcoded secrets
- Missing expiration (`exp`) or not validating it
- Sensitive data in JWT payload (tokens are base64, not encrypted)
- Not validating `iss` (issuer) or `aud` (audience)
- **(Java)** `jjwt` / `nimbus-jose-jwt` misconfiguration; token parsed without signature verification

## Secrets and PII

- API keys, tokens, or credentials in code/config/logs
- Secrets in git history or environment variables exposed to client
- Excessive logging of PII or sensitive payloads
- Missing data masking in error messages

## Supply Chain & Dependencies

- Unpinned dependencies allowing malicious updates
- Dependency confusion (private package name collision)
- Importing from untrusted sources or CDNs without integrity checks
- Outdated dependencies with known CVEs
- **(Java)** Maven/Gradle dependency with known vulnerabilities; SNAPSHOT dependencies in production builds

## CORS & Headers

- Overly permissive CORS (`Access-Control-Allow-Origin: *` with credentials)
- Missing security headers (CSP, X-Frame-Options, X-Content-Type-Options)
- Exposed internal headers or stack traces

## Runtime Risks

- Unbounded loops, recursive calls, or large in-memory buffers
- Missing timeouts, retries, or rate limiting on external calls
- **(JS)** Blocking operations on request path (sync I/O in async context)
- **(Java)** Blocking the event loop or request thread with heavy computation; missing `@Async` for long-running tasks
- Resource exhaustion (file handles, connections, memory)
- **(Java)** JDBC connection leak (not closed in `finally` / not using try-with-resources); thread pool exhaustion
- ReDoS (Regular Expression Denial of Service)

## Cryptography

- Weak algorithms (MD5, SHA1 for security purposes)
- Hardcoded IVs or salts
- Using encryption without authentication (ECB mode, no HMAC)
- Insufficient key length

## Race Conditions

Race conditions are subtle bugs that cause intermittent failures and security vulnerabilities. Pay special attention to:

### Shared State Access
- Multiple threads/goroutines/async tasks accessing shared variables without synchronization
- Global state or singletons modified concurrently
- Lazy initialization without proper locking (double-checked locking issues)
- Non-thread-safe collections used in concurrent context
- **(Java)** `HashMap`/`ArrayList` shared across threads without `ConcurrentHashMap`/`CopyOnWriteArrayList`; Spring singleton bean holding mutable instance fields

### Check-Then-Act (TOCTOU)
- `if (exists) then use` patterns without atomic operations
- `if (authorized) then perform` where authorization can change
- File existence check followed by file operation
- Balance check followed by deduction (financial operations)
- Inventory check followed by order placement

### Database Concurrency
- Missing optimistic locking (`version` column, `updated_at` checks)
- Missing pessimistic locking (`SELECT FOR UPDATE`)
- Read-modify-write without transaction isolation
- Counter increments without atomic operations (`UPDATE SET count = count + 1`)
- Unique constraint violations in concurrent inserts
- **(Java)** `@Transactional` propagation/isolation level misconfigured; self-invocation bypassing proxy (same-class `@Transactional` call); `@Version` field missing on concurrent-write entities

### Distributed Systems
- Missing distributed locks for shared resources
- Leader election race conditions
- Cache invalidation races (stale reads after writes)
- Event ordering dependencies without proper sequencing
- Split-brain scenarios in cluster operations

### Common Patterns to Flag
```
# Dangerous patterns (pseudocode):
if not exists(key):       # TOCTOU
    create(key)

value = get(key)          # Read-modify-write
value += 1
set(key, value)

if user.balance >= amount:  # Check-then-act
    user.balance -= amount
```

```java
// Dangerous patterns (Java):
// Spring singleton with mutable state
@Service
public class CounterService {
    private int count = 0;  // shared mutable state in singleton!
    public void increment() { count++; }
}

// @Transactional self-invocation (proxy bypassed)
public void outer() {
    this.inner(); // inner()'s @Transactional is ignored
}
@Transactional
public void inner() { ... }
```

### Questions to Ask
- "What happens if two requests hit this code simultaneously?"
- "Is this operation atomic or can it be interrupted?"
- "What shared state does this code access?"
- "How does this behave under high concurrency?"

## Data Integrity

- Missing transactions, partial writes, or inconsistent state updates
- Weak validation before persistence (type coercion issues)
- Missing idempotency for retryable operations
- Lost updates due to concurrent modifications
- **(Java)** Missing `@Valid` / `@Validated` on Controller `@RequestBody`; `@TableLogic` soft-delete not considered in unique constraints; Flyway migration not idempotent

## Spring-Specific Security (Java)

- CSRF disabled without justification (`http.csrf().disable()`)
- Actuator endpoints exposed without auth (`/actuator/**`)
- `@CrossOrigin("*")` on sensitive endpoints
- Sensitive config values not encrypted in `application.yml` (passwords, secrets in plain text)
- Missing request validation: `@RequestParam` / `@PathVariable` without range or pattern constraint
- Feign/RestTemplate calling internal services without propagating auth context
