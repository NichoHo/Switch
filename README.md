# Switch Payment Gateway

A payment gateway simulation. It demonstrates system design, fault tolerance, and financial patterns.

[![CI Build](https://github.com/switchpay/switch/actions/workflows/build.yml/badge.svg)](https://github.com/switchpay/switch/actions)
[![Mutation Score](https://img.shields.io/badge/Mutation%20Score-94%25-brightgreen.svg)](#)

## Quick Start (Local)

Start the whole system with one command:

```bash
docker compose up -d
```

This starts the PostgreSQL database, the `gateway` application, and the `acquirer-sim` service.

When the services are up, seed the demo data:

```bash
./seed.sh
```

The seed plays a fixed script of 23 payments over three days. It covers every payment state, all four acquirers, risk allow, challenge and deny decisions, 3DS results, refunds, a dispute, and settled batches. A second run does nothing. To start over, run `docker compose down -v`, then `docker compose up -d` and `./seed.sh`.

Then open the operator console: [http://localhost:8080/dashboard](http://localhost:8080/dashboard)

## Features

- **Vault**: Tokenization of Primary Account Numbers (PANs). It uses Luhn validation and AES-GCM encryption.
- **Payment Core**: Append-only event sourcing for the Authorize, Capture, Void, and Refund states.
- **Idempotency**: A strict idempotency layer for API safety.
- **Acquirer Routing and Resilience**: Failover, Resilience4j circuit breakers, and simulated capabilities.
- **Ledger**: Double-entry accounting with a strict trial balance invariant.
- **Risk and 3DS**: Weighted risk scoring, Shadow and Active rule modes, and a 3-D Secure simulation.
- **Settlement and Recon**: A nightly batch compares acquirer files against local records. It flags exceptions automatically.
- **Operator Console**: A server-rendered Thymeleaf dashboard. See the section below.

## Operator Console

The console is server-rendered Thymeleaf. It uses no JavaScript framework and no client-side build step.

| Page | What it shows |
| --- | --- |
| Payments | The 50 most recent payments, newest first. |
| Payment detail | Amounts, routing, and the append-only event timeline for one payment. |
| Risk | Recent assessments with the score, decision, thresholds, and ruleset version. |
| Ledger | The trial balance for each currency. |
| Settlement | Settlement batches and a count of open reconciliation exceptions. |
| Acquirers | The routing directory: brands, currencies, countries, priority, and cost. |

### Not built yet

These parts of the console are incomplete. The engines behind them work, but the UI does not reach them.

- **Fault injection controls.** The simulator accepts faults at `PUT /admin/faults/{acquirerId}`. The gateway has no endpoint to forward that call, so the console cannot trigger a fault.
- **Live circuit breaker state.** The acquirers page lists the directory only. It does not show breaker state or call statistics.
- **Ledger entry browsing.** The page computes the real trial balance. It does not list individual entries.
- **Dispute pages.** A payment shows its dispute state. There is no dispute queue.

### Design system

`docs/design/index.html` holds the design system. Open it in a browser to see the tokens and every
component in one page. It also keeps the two directions that were not chosen, for reference.

The console stylesheet is `gateway/src/main/resources/static/css/dashboard.css`. Its `:root` block is
the token set. To restyle the console, change those tokens.

Two rules keep the console consistent:

- Monospace marks a status enum, for example a payment state. Everything else uses the sans face.
- Figures use tabular numerals, so columns stay aligned.

## Deployment

You can deploy the system on free-tier infrastructure.

### Render (Web Services)

The repository root holds a `render.yaml` blueprint. Connect your repository to Render to deploy it.
The blueprint configures two services, `gateway` and `acquirer-sim`. It sets JVM flags for low memory
(`-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1`).

**Cold starts:** Free Render services stop after 15 minutes without traffic. The first request after
that can take up to a minute while the service starts. The `/actuator/health` endpoints are public, so
a ping service such as `cron-job.org` can keep a service awake.

**Ping schedule:** Render gives 750 free instance-hours each month, shared by all services in the
account. Two services that are always awake use approximately 1,460 hours. Do not keep the services
awake all day. Ping them only for the nightly jobs. The times are UTC. If your ping service uses a
different time zone, convert them.

| Service | Crontab (UTC) | Reason |
|---------|---------------|--------|
| `gateway` | `*/5 1-2 * * *` | The settlement job runs at 02:00 and the recon job runs at 02:30. These jobs run only when the gateway is awake. |
| `acquirer-sim` | `20,25 2 * * *` | The recon job gets the settlement file from `acquirer-sim` at 02:30. |

These schedules use approximately 80 hours each month. At other times, each service starts on the
first request, so the first page load can take up to a minute. The first payment after `acquirer-sim`
starts can time out after 5 seconds and go to `AUTH_UNKNOWN`. The status probe job resolves the
payment when `acquirer-sim` is available.

Agora does not use this Render copy. Agora runs its own copy of Switch on its Oracle server. Refer to
`deploy/oracle/README.md` step 6 in the Agora repository.

### Neon (Database)

Use Neon for a serverless PostgreSQL instance. Set these connection variables in Render:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Hardening and Rate Limiting

The API includes `Bucket4j` rate limiting. It applies limits by API key or by IP address, to protect
public endpoints from abuse. The tokenization endpoint accepts test BINs only, for example `411111`.
Therefore you cannot vault real card data.

## Testing and Coverage

The project uses Pitest for mutation testing. If mutation coverage drops below 80%, the CI pipeline
fails. To run the tests locally:

```bash
mvn clean test
```

```bash
mvn pitest:mutationCoverage
```
