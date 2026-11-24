# ADR-002: Depend on a RegulatoryValidator Interface Instead of Checking Limits Directly in RepaymentPlan

- **Status:** Accepted
- **Date:** 2026-09-29
- **CP2 status update:** Carried forward. `StandardRegulatoryValidator` is implemented; the planned `StrictStateRegulatoryValidator` was not built.

## Context
`RepaymentPlan` needs to check that a plan's new monthly amount and term actually fall within regulatory limits before it gets approved. But those limits aren't fixed; they're different by state and by institution, and they change over time as regulations get updated. If I put that logic straight into `RepaymentPlan`, every new state or every regulation change would mean editing a class whose whole job is supposed to be repayment math, not compliance rules.

## Decision
I made a `RegulatoryValidator` interface with one method, `validate()`. `RepaymentPlan` depends on that interface instead of doing the check itself. The actual rules live in separate classes (`StandardRegulatoryValidator`, `StrictStateRegulatoryValidator`) that get handed to `RepaymentPlan` from outside.

## Consequences
- \+ I can test `RepaymentPlan` on its own using a fake validator, without needing real regulatory data.
- \+ Adding a new state's rules just means writing a new class; `RepaymentPlan` itself never has to change.
- − I have to make sure the right validator actually gets passed in when a plan is created.
- − It adds a bit of extra indirection for something that's a pretty simple check right now.
