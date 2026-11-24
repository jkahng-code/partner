# ADR-003: Don't Add a Factory or Workflow Interface for Incentive Offers and Plan Approval Yet

- **Status:** Accepted
- **Date:** 2026-10-17
- **CP2 status update:** Carried forward. The additional abstractions were not introduced.

## Context
When I asked an AI assistant to make `RepaymentPlan` more SOLID, it suggested adding an `IncentiveOfferFactory` and an `ApprovalWorkflow` interface so new offer types and approval processes could be added later. But right now we only have one kind of incentive offer and one way to approve a plan, and nothing in our requirements asks for more.

## Decision
We kept `generateIncentiveOffer()` and `approve()` as simple methods on `RepaymentPlan`. We'll only add an interface if a second offer type or a second approval process actually shows up in our requirements.

## Consequences
- \+ The design stays simpler and easier to read, with fewer classes to build and test.
- \+ We avoid building for a change that might never happen.
- − If a new offer type or approval process does get added later, we'll have to refactor `RepaymentPlan` at that point.
