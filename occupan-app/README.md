# Occupan app (Java)

Occupan is a hospital bed and resource management app (Team 9, CS/SE 3354). It tracks beds, rooms and
equipment, lets staff assign and release them, and keeps a history. Demo data only: patient identifiers
are simulated (like `SIM-12345`) and no real patient information is stored.

The app is built around the unit from Deliverable 2, Section 4: `AssignmentService.assignResource()` in
`junit-tests/src/AssignmentService.java`. That class decides whether an assignment is allowed, and the app
calls it for every assignment. Its original 8 JUnit tests (TC-05-01 to TC-05-08) are part of this project
and still pass.

## Requirements

- JDK 17 or later (`java -version` and `javac -version` should both work)
- Nothing else. No Maven, no downloads. The JUnit jar is already in `junit-tests/lib`.

## Run

From this folder (`occupan-app`):

- Windows PowerShell: `.\run.ps1`
  (if scripts are blocked: `powershell -ExecutionPolicy Bypass -File .\run.ps1`)
- macOS / Linux: `bash run.sh`

Then open http://127.0.0.1:8000 in your browser. Press Ctrl+C to stop.

Demo logins (password `occupan-demo`):

| Username | Role |
| --- | --- |
| `admin` | Administrator: everything, plus add, edit and retire resources |
| `nurse` | Clinical staff: dashboard, search, assign, release, change status, history, reports |

Optional settings (environment variables): `OCCUPAN_PORT` (default 8000), `OCCUPAN_DEMO_PASSWORD`
(default `occupan-demo`), `OCCUPAN_SESSION_HOURS` (default 8), `OCCUPAN_STATIC_DIR` (default `static`).
Data is kept in memory and resets to the demo data every time the server starts.

## Test

- Windows PowerShell: `.\test.ps1`
- macOS / Linux: `bash test.sh`

This compiles everything and runs 49 JUnit tests: the 8 original unit tests, 9 tests for the methods added
to `AssignmentService` (release, status change, add, remove), and 32 tests for the rules, the application
layer and the HTTP API. Output is saved to `evidence/junit-output.txt`.

To run only the original unit tests: `bash ../junit-tests/run-tests.sh`.

## How the app uses the tested unit

- `AssignmentService` holds each resource's status and active assignment. The application layer asks it to
  assign, release, change status, add and remove, so the one-active-assignment rule lives in one place.
- `assignResource()` and `snapshot()` are unchanged. The methods added at the bottom of the class
  (`addResource`, `changeStatus`, `releaseResource`, `removeResource`) are new and covered by
  `test/occupan/AssignmentServiceLifecycleTest.java`.
- The app adds what the sample leaves out: login and roles, the details of each resource, assignment
  records, status history, search, reports and the browser UI.

## Layers (layered architecture)

| Layer | Where |
| --- | --- |
| Presentation | `static/` (HTML, CSS, JS) and `src/occupan/api/` (HTTP routes, JSON) |
| Application | `src/occupan/application/` (`AuthService`, `ResourceService`, `AssignmentWorkflow`, `QueryService`) |
| Domain | `src/occupan/domain/` and `junit-tests/src/AssignmentService.java` |
| Data access | `src/occupan/data/` (`UserRepository`, `ResourceRepository`, `AssignmentRepository`, `HistoryRepository`) |
| Persistence | `src/occupan/data/Database.java` (in-memory tables) |

## Class diagram to code

| Class diagram | Code |
| --- | --- |
| User (`can`) | `domain/User.java`, `domain/Role.java` |
| Patient (`validateIdentifier`) | `domain/Patient.java` |
| Resource | `domain/Resource.java`; status is kept by `AssignmentService` |
| Assignment (assign, release) | `domain/AssignmentRecord.java`, `application/AssignmentWorkflow.java`, `AssignmentService` |
| StatusHistory | `domain/StatusChange.java`, `data/HistoryRepository.java` |
| Audit log | `data/HistoryRepository.recordAudit` |

## Requirements to code

| Requirement | Where |
| --- | --- |
| FR-1 login and roles | `AuthService`, `domain/Role.java`, every API route checks the session |
| FR-2 dashboard | `QueryService.search`, `static/` |
| FR-3 add, edit, retire (admin only) | `ResourceService` |
| FR-4 change status | `ResourceService.changeStatus` |
| FR-5 assign | `AssignmentWorkflow.assign` -> `AssignmentService.assignResource` |
| FR-6 release | `AssignmentWorkflow.release` -> `AssignmentService.releaseResource` |
| FR-7 search, filter, sort | `QueryService.search` |
| FR-8 status history | `HistoryRepository`, `QueryService.history` |
| FR-9 assignment history | `AssignmentRepository`, `QueryService.history` |
| FR-10 utilization report | `QueryService.utilization` |

## Known limits

- Data is in memory (no database file), so it resets when the server restarts.
- A resource becomes Occupied only by assigning it (not by a manual status change), and editing a
  resource changes only its location.
- Synchronization protects one running server. Several servers sharing one database would need
  transactions or a uniqueness rule.
- Demo accounts and the demo password are for the course demo only.

## AI assistance

The JUnit unit and its tests were developed with ChatGPT assistance (see `junit-tests/README.md`). The full
application code was developed with Claude assistance: Anthropic. (2026). Claude (Sonnet 5.5) [Large
language model]. https://claude.ai
