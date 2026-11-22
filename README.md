# Partner

Loan repayment communication platform.

Design docs: [`docs/domain-model.mmd`](docs/domain-model.mmd).

## Docker (build and run)

**Prerequisites:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running (`docker info` shows a healthy **Server** section).

From the root:

```bash
./build-up.sh
```

That runs `docker compose up --build`: builds the **app** and **server** images, starts **db**, waits for Postgres to be healthy, then starts the API and UI. Logs stay attached to your terminal; stop with `Ctrl+C`.

**Stop and remove containers** (keeps the database volume):

```bash
docker compose down
```

| Service                    | URL                                                      |
| -------------------------- | -------------------------------------------------------- |
| App                        | http://localhost:3000                                    |
| Server API                 | http://localhost:8081/api/health                         |
| PostgreSQL (from your Mac) | `localhost:5433` — user / password / database: `partner` |

Host ports **8081** and **5433** are used so the stack can run alongside other services on 8080 and 5432. Inside Compose, the server still uses port 8080 and Postgres 5432.

**Quick check:**

```bash
curl http://localhost:8081/api/health
curl http://localhost:3000/api/health
```

Both should return `{"status":"ok"}`. The app page at http://localhost:3000 shows the same health check in the UI.

Equivalent without the script: `docker compose up --build` (add `-d` to detach). Extra Compose flags can be passed through, e.g. `./build-up.sh -d --force-recreate`.

## Run locally without Docker (no database needed)

**Prerequisites:** Java 21 and Maven 3.9+ (`java -version`, `mvn -version`).

**Run the tests** (from the `server` folder):

```bash
cd server
mvn test
```

**Start the server** with the `local` profile, which skips PostgreSQL and keeps data in memory:

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The API is then at http://localhost:8080. Data resets every time the server restarts. Stop it with `Ctrl+C`.

**Try the repayment API** (in a second terminal). The server starts with three sample loans: `L-100` ($1,000, delinquent borrower), `L-200` ($1,200, not delinquent), and `L-300` ($15,000, delinquent).

```bash
# List loans
curl http://localhost:8080/api/repayment/loans

# Create a 10-month repayment plan
curl -X POST http://localhost:8080/api/repayment/loans/L-100/plan \
  -H "Content-Type: application/json" -d '{"termMonths": 10}'

# Generate an incentive offer (expires 14 days after the date given)
curl -X POST http://localhost:8080/api/repayment/loans/L-100/plan/offer \
  -H "Content-Type: application/json" -d '{"date": "2026-11-01"}'

# Borrower accepts the offer
curl -X POST http://localhost:8080/api/repayment/loans/L-100/plan/offer/accept \
  -H "Content-Type: application/json" -d '{"date": "2026-11-15"}'

# Approve the plan (checks regulatory limits)
curl -X POST http://localhost:8080/api/repayment/loans/L-100/plan/approve
```

Invalid requests are rejected with an error message: a bad input such as `{"termMonths": 0}` returns **400**, a rule violation such as a second plan or an expired offer returns **409**, and an unknown loan returns **404**.

**Try the delinquency-detection API.** The server starts with five sample loans (`D-100` to `D-500`), each with a $10,000 principal and one installment. A loan is **delinquent** when any installment is six or more calendar months past due, or when unpaid missed payments are more than 25% of the principal. Add `?date=YYYY-MM-DD` to evaluate "as of" a date (default: today).

```bash
# Status of every loan
curl "http://localhost:8080/api/delinquency/loans?date=2026-11-22"

# $2,600 unpaid on a $10,000 loan (over 25%) -> DELINQUENT
curl "http://localhost:8080/api/delinquency/loans/D-200?date=2026-11-22"

# Six-month boundary: due 2026-04-15, so NON_DELINQUENT on 10-14 and DELINQUENT on 10-15
curl "http://localhost:8080/api/delinquency/loans/D-300?date=2026-10-14"
curl "http://localhost:8080/api/delinquency/loans/D-300?date=2026-10-15"
```

An unknown loan returns **404**; a date not in `YYYY-MM-DD` format returns **400**.

**Try the officer-assignment API.** Two officers are seeded: `O-1` (Dana Lee) and `O-2` (Marcus Reed). Borrowers come from the repayment sample loans (`B-100`, `B-200`, `B-300`). Only delinquent borrowers can be assigned, each borrower has at most one officer, and an officer's caseload is capped at 25.

```bash
# List officers and their caseloads
curl http://localhost:8080/api/officers

# Assign delinquent borrower B-100 to officer O-1
curl -X POST http://localhost:8080/api/officers/O-1/borrowers \
  -H "Content-Type: application/json" -d '{"borrowerId": "B-100"}'

# O-1 can view B-100's case
curl http://localhost:8080/api/officers/O-1/borrowers/B-100

# O-2 is not assigned to B-100 -> 403
curl http://localhost:8080/api/officers/O-2/borrowers/B-100
```

Assigning a non-delinquent or already-assigned borrower, or exceeding the caseload cap, returns **409**; an unknown officer or borrower returns **404**.

## Project layout

```text
partner/
├── app/              # React + Vite + TypeScript
├── server/           # Spring Boot (REST, JPA, PostgreSQL)
├── docs/             # Domain model and design artifacts
├── build-up.sh       # Bring up Compose stack
└── docker-compose.yml
```
