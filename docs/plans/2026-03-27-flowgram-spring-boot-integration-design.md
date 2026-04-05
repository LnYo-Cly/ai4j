# FlowGram Spring Boot Integration Design

Date: 2026-03-27

## Problem

`ai4j-agent` already contains a Java-side FlowGram runtime kernel, but the project still lacks a production-shaped integration layer that a Spring Boot application can depend on to serve a real FlowGram frontend. The missing layer is not the workflow execution core; it is the server-side adapter around it:

- no dedicated Spring Boot starter for FlowGram frontend integration
- no stable HTTP API contract aligned to FlowGram frontend expectations
- no SSE or other progress push adapter
- no request/auth hook model for third-party business systems
- no demo application proving frontend-to-Java runtime connectivity

Without that adapter layer, the current FlowGram support is only a runtime building block, not a reusable site-building solution.

## Current State

The repository already has these capabilities in `ai4j-agent`:

- `FlowGramRuntimeService` with `runTask`, `validateTask`, `getTaskReport`, `getTaskResult`, and `cancelTask`
- built-in node execution for `Start`, `End`, `LLM`, `Condition`, and `Loop`
- `Ai4jFlowGramLlmNodeRunner` that routes LLM nodes through ai4j Agent and `AgentModelClient`
- custom node extension via `registerNodeExecutor(...)`
- JUnit coverage for simple run, condition, loop, and cancel scenarios

The main gaps are:

- no Spring Boot auto-configuration layer
- no first-class artifact that third-party projects can import directly
- no stable web contract for FlowGram frontend docking
- no persistence abstraction for tasks and reports
- no progressive productionization plan

## Decision

Keep FlowGram runtime execution inside `ai4j-agent`, and add two new modules in the current multi-module Maven repository:

1. `ai4j-flowgram-spring-boot-starter`
2. `ai4j-flowgram-demo`

This keeps the kernel reusable, the Spring/web adapter isolated, and the demo disposable.

## Goals

- Allow third-party Spring Boot projects to integrate FlowGram frontend by adding one starter dependency.
- Preserve `ai4j-agent` as a framework-neutral workflow/runtime kernel.
- Keep JDK 8 compatibility end to end.
- Support fast MVP site assembly first, then controlled production hardening.
- Provide explicit extension points for authentication, access control, custom nodes, and future task persistence.

## Non-Goals

- Do not build a full Coze/Dify alternative in this phase.
- Do not introduce distributed execution, task recovery, or multi-region scheduling in the MVP.
- Do not require Spring Security, JWT libraries, or any specific auth framework.
- Do not tightly bind the backend canonical model to FlowGram frontend implementation details beyond the adapter boundary.

## Constraints

- Java target remains 1.8.
- Existing Maven Central publishing configuration in `ai4j/pom.xml` must remain unchanged.
- Existing `ai4j-agent` FlowGram runtime behavior must not regress.
- The starter must be usable without forcing a specific business auth or storage stack.
- The new publishable starter artifact must add its own BOM/release wiring without rewriting existing `ai4j/pom.xml` release semantics.

## Proposed Module Layout

### `ai4j-agent`

Responsibilities:

- FlowGram runtime kernel
- node execution contracts
- runtime schema/model
- task execution lifecycle
- default in-memory execution behavior

FlowGram packages remain under:

- `io.github.lnyocly.ai4j.agent.flowgram`

### `ai4j-flowgram-spring-boot-starter`

Responsibilities:

- Spring Boot auto-configuration
- REST API adapter
- optional SSE progress stream
- protocol mapping
- exception mapping
- auth/access hooks
- automatic custom node registration
- property binding

Recommended package root:

- `io.github.lnyocly.ai4j.flowgram.springboot`

Recommended internal packages:

- `autoconfigure`
- `config`
- `controller`
- `dto`
- `adapter`
- `exception`
- `security`
- `events`
- `support`

### `ai4j-flowgram-demo`

Responsibilities:

- minimal runnable sample application
- sample properties
- one or two sample workflows
- demo documentation for frontend connection

## Architecture

### Layering

The design is explicitly layered:

1. FlowGram frontend
2. FlowGram Spring Boot adapter
3. ai4j FlowGram runtime kernel
4. ai4j model clients / agent runtime

The frontend never talks directly to `FlowGramRuntimeService`. It talks to a stable HTTP contract exposed by the starter.

### Main runtime path

1. Frontend submits a FlowGram workflow task request.
2. Controller receives the request DTO.
3. Protocol adapter converts the request into internal `FlowGramTaskRunInput`.
4. Auth/access hooks resolve the caller and validate the action.
5. Runtime facade delegates to `FlowGramRuntimeService`.
6. Task metadata records caller ownership.
7. Response mapper returns a frontend-oriented result object.
8. If SSE is enabled, task progress events are published.

## Main Components

### `FlowGramRuntimeFacade`

Purpose:

- thin orchestration layer used by the web adapter
- hides direct `FlowGramRuntimeService` usage from controllers
- central place for metadata recording, access checks, and protocol conversions

Core responsibilities:

- execute run/validate/report/result/cancel operations
- coordinate metadata and task ownership
- normalize exceptions

### `FlowGramProtocolAdapter`

Purpose:

- isolate FlowGram frontend contract from internal runtime model

Responsibilities:

- request DTO to `FlowGramTaskRunInput`
- internal report/result to frontend DTO
- status mapping
- error payload shaping

### Default LLM Service Resolution

Purpose:

- make the starter-provided default `FlowGramLlmNodeRunner` work with the existing ai4j multi-service registry

Resolution order:

- explicit node input `serviceId`
- explicit node input `aiServiceId`
- starter property `ai4j.flowgram.default-service-id`

Protocol note:

- MVP should default to a chat-backed model client
- later `responses` support can be added on top of the same `AiServiceRegistry` without changing node `modelName` semantics

Failure behavior:

- the starter should fail fast if no service id can be resolved
- the starter should fail fast if the resolved service cannot supply the required client protocol

### `FlowGramTaskStore`

Purpose:

- decouple adapter-side task metadata retention from the runtime's in-memory executor

Initial implementation:

- `InMemoryFlowGramTaskStore`

Planned later:

- `RedisFlowGramTaskStore`
- `JdbcFlowGramTaskStore`

Stored data:

- task id
- caller metadata
- tenant metadata
- created time
- status snapshot
- final result snapshot
- expiration metadata

Scope note:

- In P0/P1, this store is not the source of truth for in-flight execution state.
- Cross-process task survival requires later runtime refactoring and is not delivered by the first store abstraction alone.

### `FlowGramEventPublisher`

Purpose:

- publish task and node lifecycle updates to SSE subscribers

Responsibilities:

- task started
- node started
- node finished
- task finished
- task canceled
- task failed

MVP note:

- The first milestone can ship without SSE if REST polling is enough to prove end-to-end integration.
- The event abstraction should still be introduced early to avoid controller rewrites later.
- Ordered task/node push requires runtime-level listener hooks; starter-only polling diffs are not a substitute for real lifecycle events.

## Spring Boot Starter Behavior

### Auto-configured Beans

The starter should auto-configure:

- `FlowGramRuntimeService`
- default `FlowGramLlmNodeRunner` backed by `AiServiceRegistry`
- `FlowGramRuntimeFacade`
- `FlowGramProtocolAdapter`
- `FlowGramTaskStore` default implementation
- default auth/access hook implementations
- `FlowGramTaskController`
- optional `FlowGramSseController`

### Bean Override Policy

The starter should allow business projects to override:

- `FlowGramLlmNodeRunner`
- `FlowGramTaskStore`
- `FlowGramCallerResolver`
- `FlowGramAccessChecker`
- `FlowGramTaskOwnershipStrategy`
- `FlowGramEventPublisher`

### Custom Node Registration

Any Spring Bean implementing `FlowGramNodeExecutor` should be auto-registered into `FlowGramRuntimeService` during startup.

## HTTP API Design

### Required Endpoints

- `POST /flowgram/tasks/run`
- `POST /flowgram/tasks/validate`
- `GET /flowgram/tasks/{taskId}/report`
- `GET /flowgram/tasks/{taskId}/result`
- `POST /flowgram/tasks/{taskId}/cancel`

### Optional Endpoints

- `GET /flowgram/tasks/{taskId}/events`
- `GET /flowgram/health`
- `GET /flowgram/capabilities`

### Endpoint Semantics

#### Run

Input:

- schema payload
- runtime inputs

Output:

- task id

#### Validate

Input:

- schema payload
- optional runtime inputs

Output:

- valid or invalid
- validation error list

#### Report

Output:

- workflow status
- per-node status
- timestamps
- node errors if present

#### Result

Output:

- final outputs in the shape required by frontend consumption
- terminal status

#### Cancel

Output:

- success or failure

## Protocol Mapping

The starter must not leak internal runtime DTOs directly. The protocol adapter should map them into stable frontend DTOs.

### Status Mapping

Internal runtime currently uses:

- `pending`
- `processing`
- `success`
- `failed`
- `canceled`

The adapter should define the canonical external set and map internal values consistently. If the FlowGram frontend expects `fail` rather than `failed`, the conversion must happen in the adapter instead of changing the kernel everywhere.

### Result Mapping

Current internal `FlowGramTaskResultOutput` contains:

- `status`
- `terminated`
- `result`

If the frontend expects the final output map directly, or a different wrapper shape, that conversion belongs in the adapter layer.

### Error Mapping

Controllers should never return raw Java exception strings as the primary contract. Errors should be normalized to a stable structure:

- `code`
- `message`
- `details`
- `timestamp`

## Authentication and Access Hooks

The starter should not implement business authentication itself. It should provide extension hooks.

### `FlowGramCallerResolver`

Purpose:

- resolve the caller identity from the current request

Typical sources:

- gateway headers
- JWT claims
- session attributes
- API key lookup

Default behavior:

- anonymous caller

### `FlowGramAccessChecker`

Purpose:

- authorize actions against the current caller and optional task context

Actions:

- `RUN`
- `VALIDATE`
- `REPORT`
- `RESULT`
- `CANCEL`

Default behavior:

- allow all

### `FlowGramTaskOwnershipStrategy`

Purpose:

- persist task ownership metadata when a task is created

Default stored fields:

- `creatorId`
- `tenantId`
- `createdAt`

This lets later requests enforce ownership or tenant isolation.

## Configuration Model

Recommended properties:

- `ai4j.flowgram.enabled`
- `ai4j.flowgram.api.base-path`
- `ai4j.flowgram.default-service-id`
- `ai4j.flowgram.stream-progress`
- `ai4j.flowgram.task-retention`
- `ai4j.flowgram.task-store.type`
- `ai4j.flowgram.cors.allowed-origins`
- `ai4j.flowgram.report-node-details`
- `ai4j.flowgram.auth.enabled`
- `ai4j.flowgram.auth.header-name`

MVP defaults:

- enabled
- base path set to `/flowgram`
- default runner uses registry-backed chat protocol resolution
- in-memory task store
- polling supported
- SSE optional and off by default

## Demo Application Design

The demo module should prove that the starter is enough for a real site.

The demo should contain:

- one Spring Boot application class
- sample `application.yml`
- one configured model client path
- starter import only, no custom controller logic
- startup instructions

Success condition:

- a developer starts the demo, points FlowGram frontend to the Java backend, and can run a simple workflow end to end

## Phased Delivery Plan

### Phase P0: Frontend-Docking MVP

Scope:

- create `ai4j-flowgram-spring-boot-starter`
- create `ai4j-flowgram-demo`
- add the minimum runtime listener/error-exposure hooks required by the web adapter
- expose REST task APIs
- auto-configure runtime service and registry-backed default LLM runner
- add default auth hook contracts with permissive implementations
- support in-memory task metadata

Deliverables:

- minimal kernel hook additions
- new modules
- starter auto-configuration
- REST controller layer
- basic exception mapping
- demo application
- basic docs

Acceptance:

- Spring Boot project can depend on the starter and start successfully
- run/validate/report/result/cancel all work
- custom `FlowGramNodeExecutor` Spring Beans auto-register
- failed tasks can be mapped to normalized workflow-level error payloads
- demo proves frontend integration with polling

### Phase P1: Protocol Hardening

Scope:

- fully normalize status mapping
- align result/report DTO shapes
- improve validation error quality
- add CORS support
- add `/flowgram/health`

Acceptance:

- no controller leaks internal runtime DTOs directly
- status and error payloads remain stable across scenarios
- a frontend integrator can rely on documented DTO contracts

### Phase P2: Progressive UX

Scope:

- add SSE progress push
- add event publisher abstraction on top of runtime listener hooks
- add node lifecycle events

Acceptance:

- frontend can subscribe to task lifecycle updates without polling
- task completion and failure events arrive in order

### Phase P3: Productionization

Scope:

- task store abstraction refinement
- retention and cleanup policy
- Redis or JDBC persistence option
- ownership-aware access checks
- observability and metrics

Acceptance:

- task state survives process boundaries when a persistent store is configured
- retention cleanup is deterministic
- ownership checks can be enforced without controller rewrites

## Detailed Task List

### Module and Build Tasks

- add starter module to root `pom.xml`
- add demo module to root `pom.xml`
- add starter artifact to `ai4j-bom`
- keep demo out of the BOM because it is not a third-party dependency surface
- wire dependencies so starter depends on `ai4j-agent` and Spring MVC/autoconfigure support
- keep Java 8 compiler target
- do not touch Maven Central publishing config in `ai4j/pom.xml`
- add release/profile metadata to the new starter module instead of changing existing published modules

### Starter Tasks

- implement `FlowGramProperties`
- implement auto-configuration class
- conditionally expose default beans
- implement custom node executor auto-registration
- register Spring metadata in `META-INF`

### Web Adapter Tasks

- define request and response DTOs
- implement controller endpoints
- implement exception mapper
- implement protocol adapter
- map statuses and errors explicitly

### Security Hook Tasks

- define caller model
- define action enum
- implement resolver/checker/ownership interfaces
- provide permissive defaults
- attach task metadata on run

### Runtime Integration Tasks

- add facade over `FlowGramRuntimeService`
- isolate internal-to-external DTO translation
- expose workflow-level failure reason to adapter consumption
- add runtime listener/observer hooks for future SSE
- define service-id resolution contract for the default registry-backed LLM runner
- ensure task lookup and cancel semantics remain stable

### Demo Tasks

- add runnable demo application
- add sample configuration
- add minimal README and startup guide
- validate against a real FlowGram frontend

### Documentation Tasks

- update root `README.md`
- update docs-site FlowGram integration guide
- document starter properties
- document auth hook extension usage
- document custom node registration

## Testing Strategy

### Unit Tests

- protocol status mapping
- DTO conversions
- auth hook default behavior
- custom node auto-registration

### Integration Tests

- Spring Boot context startup
- REST endpoint happy paths
- validation failure responses
- task not found responses
- cancel behavior

### End-to-End Tests

- demo app with a simple Start -> LLM -> End workflow
- demo app with a condition or loop workflow
- frontend connectivity validation against a running FlowGram frontend

## Acceptance Criteria

The work is accepted when all of the following are true:

- a third-party Spring Boot app can integrate the starter with minimal configuration
- FlowGram frontend can run a simple workflow through the Java backend
- the starter exposes a stable documented contract instead of raw internal DTOs
- JDK 8 build still passes
- current `ai4j-agent` FlowGram tests do not regress
- business projects can plug custom auth and custom node executors without forking starter code

## Risks

- FlowGram frontend contract may evolve faster than the Java adapter
- current runtime is in-memory oriented and may create mismatch with production expectations
- cancellation remains best-effort unless deeper interruptibility is added later
- protocol drift between internal runtime states and frontend expectations can create subtle UI bugs

## Mitigations

- keep all frontend-specific mapping inside the adapter layer
- keep the kernel DTOs isolated from public web DTOs
- ship polling first, add SSE without breaking REST paths
- define task store and event publisher abstractions before production persistence work

## Open Questions

- exact frontend DTO and status expectations should be revalidated against the current FlowGram frontend build before finalizing P1
- whether SSE is required for the first live demo or polling is sufficient
- whether task ownership metadata should live only in adapter storage or also in runtime records
- whether P0 should support only chat-backed default LLM resolution, or both `chat` and `responses`

## Recommendation

Proceed with P0 in two slices first:

- P0a: add the minimum runtime hooks for workflow error exposure and lifecycle listeners
- P0b: add the starter module, demo module, and core task APIs
- keep auth hooks permissive by default but available from day one

This gives the project the fastest path from runtime kernel to a reusable Java-side FlowGram site integration story without overfitting the kernel to Spring or to a single frontend revision.
