# Product decisions (review before each implementation phase)

Last updated: 2026-07-16. Revisit when starting paper trading or last-day replay.

## Users & admin

| Decision | Choice |
|----------|--------|
| Roles | `ADMIN` (you), `USER` (paper traders) |
| Signup | Admin creates users (no public self-signup v1) |
| Auth | Session cookie login (simple). Not JWT. “Auth light” — no OAuth. |
| Admin powers | Create users, set passwords, seed cash, reset cash, enable/disable trading, deactivate |
| Default admin | `admin` / `TIP_ADMIN_PASSWORD` (default `admin`) |
| Starting cash | ₹100,000 INR (configurable `tip.users.default-seed-cash`) |
| Storage | PostgreSQL `app_users` + `cash_ledger` |

## Watchlist

| Decision | Choice |
|----------|--------|
| Scope | **Shared global** watchlist |
| Mutate | **ADMIN only** (add/remove) |
| Read | Any authenticated user |

## Paper trading (next phase — not built yet)

| Decision | Choice |
|----------|--------|
| Market | NSE equities on watchlist, INR |
| Orders | Market only |
| Fill | Live LTP |
| Short | Allowed |
| Costs | Simplified brokerage (to define at implement time) |
| Square-off | Intraday square-off allowed |
| Reset | Admin only |
| UI | Chart page only |
| Alerts | Actionable later |

## Deferred

- Full strategy **backtesting** (later)
- **Last trading day replay** (after paper trading)
- Real broker / live orders

## Implementation order

1. **Users** (this phase)  
2. Paper trading  
3. Last-day replay  
4. Backtesting (later)
