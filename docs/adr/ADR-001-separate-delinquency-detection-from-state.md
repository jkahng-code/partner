# ADR-001: Separate Delinquency Detection from Delinquency State

- **Status:** Accepted
- **Date:** 2026-10-06
- **CP2 status update:** Carried forward. `Loan` (with `DelinquencyPolicy`) evaluates delinquency, and `Delinquency` tracks the resulting state.

## Context
I originally placed both `determineDelinquency` and `updateDelinquencyStatus` on `Delinquency`. However, determining delinquency depends on a loan's payment history, while `Delinquency` is mainly responsible for tracking the resulting status. These responsibilities could also change for different reasons as the system develops or if loan institutions will later be allowed to specify their delinquency standards.

## Decision
I moved `determineDelinquency` to `Loan` and kept `updateDelinquencyStatus` on `Delinquency`. `Loan` already has access to the payment history needed to make the determination, while `Delinquency` remains responsible for maintaining its own state. This decision follows the Single Responsibility Principle.

## Consequences
This gives the `Loan` class a more focused responsibility and avoids passing the full payment history into `Delinquency` just to make a status calculation. It also makes changes to delinquency rules less likely to affect the `Delinquency` class. One tradeoff is that detecting and recording delinquency now requires more communication between `Loan` and `Delinquency`.
