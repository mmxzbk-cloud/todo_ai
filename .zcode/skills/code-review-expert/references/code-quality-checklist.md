# Code Quality Checklist

## Error Handling

### Anti-patterns to Flag

- **Swallowed exceptions**: Empty catch blocks or catch with only logging
  ```javascript
  // JS
  try { ... } catch (e) { }  // Silent failure
  try { ... } catch (e) { console.log(e) }  // Log and forget
  ```
  ```java
  // Java
  try { ... } catch (Exception e) { }  // Silent failure
  try { ... } catch (Exception e) { e.printStackTrace(); }  // Log to stdout, not logger
  ```
- **Overly broad catch**: Catching `Exception`/`Error` base class instead of specific types
- **Error information leakage**: Stack traces or internal details exposed to users
- **Missing error handling**: No try-catch around fallible operations (I/O, network, parsing)
- **(JS)** **Async error handling**: Unhandled promise rejections, missing `.catch()`, no error boundary
- **(Java)** **Checked exception abuse**: Catching checked exceptions only to wrap in `RuntimeException` without context; `throws Exception` on method signature instead of specific types
- **(Java)** **BizException misuse**: Using generic `BizErrorEnum.BUSINESS_ERROR` with string message instead of module-specific error enum

### Best Practices to Check

- [ ] Errors are caught at appropriate boundaries
- [ ] Error messages are user-friendly (no internal details exposed)
- [ ] Errors are logged with sufficient context for debugging
- [ ] **(JS)** Async errors are properly propagated or handled
- [ ] **(Java)** Resources closed in `finally` or via try-with-resources
- [ ] **(Java)** `@ExceptionHandler` / global handler covers new exception types
- [ ] Fallback behavior is defined for recoverable errors
- [ ] Critical errors trigger alerts/monitoring

### Questions to Ask
- "What happens when this operation fails?"
- "Will the caller know something went wrong?"
- "Is there enough context to debug this error?"

---

## Performance & Caching

### CPU-Intensive Operations

- **Expensive operations in hot paths**: Regex compilation, JSON parsing, crypto in loops
- **Blocking main thread**: Sync I/O, heavy computation without worker/async
- **Unnecessary recomputation**: Same calculation done multiple times
- **Missing memoization**: Pure functions called repeatedly with same inputs

### Database & I/O

- **N+1 queries**: Loop that makes a query per item instead of batch
  ```javascript
  // JS - Bad: N+1
  for (const id of ids) {
    const user = await db.query(`SELECT * FROM users WHERE id = ?`, id)
  }
  // JS - Good: Batch
  const users = await db.query(`SELECT * FROM users WHERE id IN (?)`, ids)
  ```
  ```java
  // Java - Bad: N+1
  for (Long id : ids) {
      userService.getById(id);        // DB call in loop
      userFacade.getUserById(id);     // Feign call in loop
  }
  // Java - Good: Batch
  Map<Long, User> userMap = userService.listByIds(ids)
      .stream().collect(Collectors.toMap(User::getId, Function.identity()));
  Map<Long, UserDTO> dtoMap = userFacade.getUserMap(ids);
  ```
- **Full-table scan (Java)**: `findAll()`, `list()`, `selectList(null)`, `lambdaQuery().list()` without conditions, then filtering in memory
- **Missing indexes**: Queries on unindexed columns
- **Over-fetching**: SELECT * when only few columns needed
- **No pagination**: Loading entire dataset into memory

### Caching Issues

- **Missing cache for expensive operations**: Repeated API calls, DB queries, computations
- **Cache without TTL**: Stale data served indefinitely
- **Cache without invalidation strategy**: Data updated but cache not cleared
- **Cache key collisions**: Insufficient key uniqueness
- **Caching user-specific data globally**: Security/privacy issue
- **(Java)** `@Cacheable` without `@CacheEvict` on update/delete paths; caching mutable objects (caller modifies cached reference)

### Memory

- **Unbounded collections**: Arrays/maps that grow without limit
- **Large object retention**: Holding references preventing GC
- **String concatenation in loops**: Use `StringBuilder`/`join` instead
- **Loading large files entirely**: Use streaming instead
- **(Java)** ThreadLocal not removed after request (`ThreadLocal.remove()` missing in filter/interceptor)
- **(Java)** Large list loaded from DB without pagination; `Stream` from MyBatis not closed

### Questions to Ask
- "What's the time complexity of this operation?"
- "How does this behave with 10x/100x data?"
- "Is this result cacheable? Should it be?"
- "Can this be batched instead of one-by-one?"

---

## Boundary Conditions

### Null/Undefined Handling

- **(JS)** **Missing null checks**: Accessing properties on potentially null objects
- **(JS)** **Truthy/falsy confusion**: `if (value)` when `0` or `""` are valid
- **(JS)** **Optional chaining overuse**: `a?.b?.c?.d` hiding structural issues
- **(JS)** **Null vs undefined inconsistency**: Mixed usage without clear convention
- **(Java)** **NullPointerException risk**: Unboxing `Integer`/`Long` without null check; chained getter calls without null guard
- **(Java)** **Optional misuse**: `Optional.get()` without `isPresent()`; `Optional` as method parameter or field type

### Empty Collections

- **Empty array not handled**: Code assumes array has items
- **Empty object edge case**: `for...in` or `Object.keys` on empty object
- **First/last element access**: `arr[0]` or `arr[arr.length-1]` without length check

### Numeric Boundaries

- **Division by zero**: Missing check before division
- **Integer overflow**: Large numbers exceeding safe integer range
- **Floating point comparison**: Using `===` instead of epsilon comparison
- **Negative values**: Index or count that shouldn't be negative
- **Off-by-one errors**: Loop bounds, array slicing, pagination

### String Boundaries

- **Empty string**: Not handled as edge case
- **Whitespace-only string**: Passes truthy check but is effectively empty
- **Very long strings**: No length limits causing memory/display issues
- **Unicode edge cases**: Emoji, RTL text, combining characters

### Common Patterns to Flag

```javascript
// JS - Dangerous: no null check
const name = user.profile.name

// JS - Dangerous: array access without check
const first = items[0]

// JS - Dangerous: division without check
const avg = total / count

// JS - Dangerous: truthy check excludes valid values
if (value) { ... }  // fails for 0, "", false
```

```java
// Java - Dangerous: NPE from unboxing
Long count = map.get(key);
long total = count + 1;  // NPE if key absent

// Java - Dangerous: chained getters
String name = user.getDepartment().getManager().getName();

// Java - Dangerous: stream on potentially null collection
list.stream().map(...)  // NPE if list is null

// Java - Dangerous: missing @Valid
@PostMapping
public Result<Long> create(@RequestBody SomeReq req) { ... }  // no @Valid
```

### Questions to Ask
- **(JS)** "What if this is null/undefined?"
- **(Java)** "What if this is null? Will it cause NPE during unboxing?"
- "What if this collection is empty or null?"
- "What's the valid range for this number?"
- "What happens at the boundaries (0, -1, MAX_INT)?"

---

## Java / Spring Boot Specific

### Architecture & Convention

- **DDD layering violation**: Controller directly calling DAO/Service instead of Aggregate; Aggregate calling another domain's DAO instead of going through Facade
- **Entity as API parameter**: Using `@TableName` entity in `@RequestBody` instead of `*Req` DTO
- **MapStruct not used**: Manual field-by-field setter copying instead of `*Conversion` interface
- **Path variable misuse**: `/{id}/members` instead of `/members/add` with ID in request body

### MyBatis Plus

- **`${}`  interpolation**: SQL injection risk — must use `#{}` for parameter binding
- **Missing `@TableLogic`**: Soft-delete entity without logical delete annotation
- **`selectList(null)`**: Full table scan without conditions
- **Wrapper misuse**: `QueryWrapper` with hardcoded column strings instead of `LambdaQueryWrapper` with method references

### Transaction & Data Consistency

- **`@Transactional` on private method**: Spring proxy cannot intercept — annotation is silently ignored
- **`@Transactional` self-invocation**: Calling `this.method()` bypasses AOP proxy
- **Too broad `@Transactional`**: Wrapping Feign calls or slow operations inside a transaction, holding DB connections too long
- **Missing `@Transactional`**: Multiple write operations without transaction guarantee

### Feign & Cross-Service

- **Assumed Feign endpoint**: Adding Feign method without verifying the target service actually exposes that endpoint
- **Single-item Feign in loop**: Calling Feign inside `for`/`forEach` instead of batch endpoint
- **Missing fallback**: Feign call without fallback or circuit breaker for non-critical paths
- **Return type mismatch**: Feign method return type not matching target Controller's actual response structure
