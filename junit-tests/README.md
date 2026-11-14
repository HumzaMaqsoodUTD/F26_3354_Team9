# Resource assignment unit tests

This standalone Java sample supports Deliverable #2, Section 4. It implements the FR-5 resource assignment rule and expands the TC-05 assignment/conflict test from the requirements traceability matrix.

## Run

Install JDK 17 or later, then run from the repository root:

```sh
bash junit-tests/run-tests.sh
```

The runner compiles the implementation and tests, executes JUnit, and saves console output to `evidence/junit-output.txt` and XML reports to `evidence/junit/`. The bundled JUnit Platform Console Standalone 1.11.4 includes JUnit Jupiter 5.11.4. No Maven installation or dependency download is needed. JUnit's legal notices remain inside the original JAR.

## Test cases

| Test | Behavior checked |
| --- | --- |
| TC-05-01 | Assign an Available resource and change its status to Occupied. |
| TC-05-02 | Reject Occupied, Reserved, Cleaning, Maintenance, and Unavailable resources. |
| TC-05-03 | Reject a second assignment while preserving the first. |
| TC-05-04 | Reject null, empty, and blank patient identifiers. |
| TC-05-05 | Reject null, empty, and blank staff identifiers. |
| TC-05-06 | Reject an unknown resource without changing existing resources. |
| TC-05-07 | Allow exactly one of two competing requests through one service instance. |
| TC-05-08 | Leave unrelated resources unchanged. |

All eight test methods passed on November 14, 2026 using OpenJDK 25.0.1 with Java 17 as the compilation target. The earlier report run on November 12 also passed all eight methods; elapsed time can vary between runs.

## Scope

The implementation uses in-memory data and simulated identifiers. It is not integrated into the complete application. Synchronization prevents duplicate assignments within the same service instance; a shared database needs its own transaction or uniqueness rule. Authentication, role enforcement, release operations, and audit-history recording are outside this sample's scope.

The sample implementation and tests were developed with ChatGPT assistance.
