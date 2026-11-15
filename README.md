# F26_3354_Team9
Project repo for Team 9 - Occupan in CS 3354
Delieverable #1

## Overview
Occupan is a hospital bed and medical resource management system. 
It is designed to help clinical staff and administrators track room availabnility, 
assign beds and equipment to patients, and update resource statuses in real time.

## Key Features
- Staff login with role-based permissions
- Live dashboard showing bed, room, and equipment availability
- Resource status tracking (Available, Occupied, Reserved, Cleaning, Maintenance, Unavailable)
- Assign and release resources using simulated patient IDs
- Search and filter resources by type, status, and floor/location
- History log tracking all status changes and assignments

## Team Members
- H Maqsood         -> @HumzaMaqsoodUTD 
- S Patil
- M Amanullah       -> @88muaaz88-cloud 
- S Pineda          -> @Pinedas654
- Noman
- A Nguyen
- S Bislig


## Running the Occupan app (Deliverable 2)

The app is in `occupan-app/` (Java). The unit and its JUnit tests are in `junit-tests/`.

### 1. Install dependencies
- Install JDK 17 or later (for example Temurin 21 from https://adoptium.net).
- Check it with `java -version` and `javac -version`.
- Nothing else is needed. There is no Maven or Gradle, and the JUnit jar is included in `junit-tests/lib`.

### 2. Configure environment variables
None are required. Optional (defaults in parentheses): `OCCUPAN_PORT` (8000), `OCCUPAN_DEMO_PASSWORD` (occupan-demo), `OCCUPAN_SESSION_HOURS` (8), `OCCUPAN_STATIC_DIR` (static).

Example in PowerShell: `$env:OCCUPAN_PORT = "8080"`. On macOS/Linux: `export OCCUPAN_PORT=8080`.

### 3. Run locally
- Windows PowerShell: `cd occupan-app`, then `powershell -ExecutionPolicy Bypass -File .\run.ps1`
- macOS/Linux: `cd occupan-app`, then `bash run.sh`
- Open http://127.0.0.1:8000. Demo logins: `admin` or `nurse`, password `occupan-demo`.

### 4. Run the tests
- Windows PowerShell: `cd occupan-app`, then `powershell -ExecutionPolicy Bypass -File .\test.ps1`
- macOS/Linux: `cd occupan-app`, then `bash test.sh`
- This runs 49 JUnit tests, including the 8 original unit tests (TC-05-01 to TC-05-08).
- More details are in `occupan-app/README.md`.
