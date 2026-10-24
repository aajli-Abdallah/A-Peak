# Deferred work (tracker)

Agreed: no separate 1.3.57. Cleanup and the project document are done once, after 1.3.60.

## To do at the end (after 1.3.60)
1. DONE in 1.3.61 - Clean weak unit tests:
   - Phase55Test.csvEscapesAndFiltersYear: convoluted last assertion, replace with a plain line-count check.
   - StockAnalyticsTest.sortModes: redundant reversed assertion.
2. Update the Claude Doc blueprint: it stops at 1.3.52. Add 1.3.53/54 (shipped inside 1.3.55), 1.3.55, 1.3.56, 1.3.58, 1.3.59, 1.3.60 and the final blueprint.
3. Re-check README feature list and the schema notes (schema is v6, commit app/schemas/6.json).

## Planned
- 1.3.58 encrypted backups (this version)
- 1.3.59 architecture step 1: StocksRepository + StocksViewModel (done)
- 1.3.60 architecture step 2: repositories + view models per area, Overview computed once (done)
- 2.0.0 app lock, privacy mode, release signing key, final blueprint

## Left after 1.3.60
- Expense Year/Month pages still recompute their statistics inside the screens on each recomposition (fine at current sizes). If data grows a lot: move them into ExpensesViewModel like the Overview, or add SQL aggregates carefully (local timezone).
- Overview shows nothing for a moment at start until the first result is ready (by design).

## Ideas parked
- Emoji category icons vs custom icons (design consistency).
- Stock prices are manual (no feed). Single currency for the whole app.
- Optional setting to forbid unencrypted sharing.
