# NOTES.md — Patch Exercise Submission

## Approach

I set up the app locally following the README, ran both the backend (Spring Boot on port 8080) and frontend (Vite on port 5173), and manually tested it before reading any code. This helped me see the symptoms first before looking for root causes.

I used Claude (claude.ai) and GitHub Copilot to help reason through the code and draft fixes. All changes were reviewed and understood before applying. No other external tools were needed — the README was sufficient for setup.

---

## Bugs Fixed

### Bug 1 — Artificial delay making the app feel slow (TaskController.java)

The controller had a `Thread.sleep()` call that introduced an artificial delay on every API request. The delay was calculated based on query length, but the formula was inverted — shorter queries (including the initial empty-search page load) waited the longest, up to 1 full second. Longer queries were instant. This is the opposite of what makes sense.

I found this by noticing the app felt sluggish on load but fast when typing long search terms. Traced it to the controller.

Fix: Removed the `Thread.sleep()` entirely. Artificial delays do not belong in a request thread. Kept the logging line so request details are still visible in the backend console.

---

### Bug 2 — SQL operator precedence breaking search and status filter (TaskRepository.java)

The native query in `TaskRepository` had a WHERE clause without parentheses around the OR condition:

```
WHERE archived = FALSE AND LOWER(title) LIKE :term
OR LOWER(description) LIKE :term AND (:status IS NULL OR status = :status)
```

Because AND binds tighter than OR in SQL, this parsed incorrectly. Archived tasks could appear in description-match results, and the status filter was silently ignored for title matches. Search results were inconsistent.

Fix: Added parentheses to group the OR correctly:

```
WHERE archived = FALSE
AND (LOWER(title) LIKE :term OR LOWER(description) LIKE :term)
AND (:status IS NULL OR status = :status)
```

Also added indexes on `status`, `title`, and `description` columns in `schema.sql` to improve query performance at scale.

---

### Bug 3 — Status filter not sorting results consistently (TaskTable.jsx + TaskRepository.java)

Selecting Open, Done, or In Progress filtered tasks but the order of results was unpredictable. The backend query only sorted by `created_at DESC`, so filtered results appeared in creation order regardless of status or priority.

Fix: Updated the backend query ORDER BY to `status ASC, id ASC` for consistent ordering. Also added client-side sort in `TaskTable.jsx` as a safety net so the display order is always predictable even if the backend order changes.

---

### Bug 4 — Loading state stuck forever on API error (useTasks.js)

In the React hook `useTasks.js`, `setLoading(false)` was only called inside the `.then()` block (success path). If the backend was down or returned an error, the `.catch()` block ran but never cleared the loading flag. The UI showed "Loading tasks..." permanently with no error message visible.

Fix: Moved `setLoading(false)` into `.finally()` so it always runs regardless of success or failure. Also added `setTasks([])` and `setTotal(0)` in the catch block to clear stale data.

---

### Bug 5 — Page not resetting when search or filter changes (App.jsx)

If a user navigated to page 3 and then changed the search term or status filter, the app fetched page 3 of the new results. If the new results had fewer than 3 pages, the table appeared empty even though page 1 had results.

Fix: Added `setPage(1)` inside both `handleQueryChange` and `handleStatusChange` so pagination resets to page 1 whenever the search parameters change.

---

## Issues Not Fully Resolved

- **Frontend npm vulnerabilities** — `npm install` reported 3 vulnerabilities (2 moderate, 1 high). Did not run `npm audit fix` as it may introduce breaking changes; flagging for the team.
- **No search debounce** — Every keystroke fires an API call. A 300ms debounce would reduce unnecessary requests but this is a UX improvement, not a bug.
- **spring.jpa.open-in-view warning** — The backend logs a warning about open-in-view being enabled. Should set `spring.jpa.open-in-view=false` in `application.properties` for production use.

---

## Biggest Remaining Risk

The SQL query uses `LIKE '%term%'` with a leading wildcard on both title and description. This disables index usage entirely and will cause full table scans at scale. At a few hundred tasks this is fine; at tens of thousands it becomes a bottleneck. The right fix is a full-text search index or a dedicated search service.

---

## Assumptions Made

- The Oracle PL/SQL package in `db/oracle/` is a reference artifact only and does not run against H2 locally. Applied the same SQL parentheses fix there for consistency.
- Port conflict (8080 already in use) is an environment issue, not a code bug. Documented it but did not change the default port in code.
- Sorting by `status ASC, id ASC` is a reasonable default. The product team may want a different sort order.
