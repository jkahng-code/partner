# ADR-004: Keep the Delinquency and Repayment Models Separate During Checkpoint 2

- **Status:** Accepted (temporary)
- **Date:** 2026-11-22

## Context
During Checkpoint 2, I found that the delinquency feature and repayment features used separate `Loan` and `Borrower` classes. The delinquency classes were in `com.partner.domain`, while the repayment and officer assignment classes were in `com.partner.repayment`.

Ideally, these features would share the same loan and borrower information. However, after the project became a solo effort, I had less time to finish the remaining requirements. Merging the classes would have required changing several parts of the application and rerunning tests to make sure the existing features still worked.

## Decision
I decided to keep the two sets of `Loan` and `Borrower` classes separate for Checkpoint 2 instead of refactoring them into a shared model.

This allowed me to focus on completing and testing the existing REST API features without introducing a larger architectural change near the deadline. I also decided to document the missing integration rather than claim that the features were fully connected.

## Alternatives Considered
- **Merge the models now:** This would create a more consistent design and allow delinquency detection to update the same borrower records used by officer assignment and repayment plans. However, it would require more implementation and regression testing than I could complete within the remaining time.
- **Keep the models separate temporarily:** This avoids disrupting the working features and allows them to be tested independently. The downside is that delinquency status does not automatically carry over between the features.

## Consequences
Keeping the models separate reduced the risk of introducing new bugs during the final development period. It also made it possible to finish testing the four main features.

However, the application now has duplicate `Loan` and `Borrower` concepts. The repayment feature relies on borrower delinquency values entered into sample data instead of using the result from the delinquency detection feature. This limitation is recorded as D-06 in the Checkpoint 2 defect log.

Before Partner can support a complete real-world workflow, the models will need to be unified or connected through a reliable integration layer. This decision is temporary rather than the intended long-term architecture.
