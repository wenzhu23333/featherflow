# FeatherFlow Ops Console Pagination And UI Refresh Design

## 1. Goals

Refine the existing `featherflow-ops-console` UI so that:

- the workflow list page supports server-side pagination semantics
- the workflow detail page activity timeline supports pagination
- the visual presentation becomes clearer and more operationally friendly
- the implementation stays lightweight and easy to maintain

This is an incremental design on top of the existing ops console.

## 2. Non-goals

- No frontend-backend separation
- No new database tables
- No login or RBAC
- No websocket push
- No new client-side framework
- No SQL-level pagination in V1 of this refinement

## 3. Recommended Approach

Use lightweight server-side pagination with existing HTMX partial refresh.

Why this approach:

- It keeps the current controller and Thymeleaf structure intact.
- It avoids pushing complexity into repository SQL for this iteration.
- It is sufficient for the current built-in demo data and typical internal ops usage.
- It keeps pagination state visible in URL query parameters, which makes pages refreshable and shareable.

## 4. Workflow List Pagination

### 4.1 Route and query parameters

Route remains:

- `GET /workflows`

Add pagination query parameters:

- `page`
- `size`

Default values:

- `page = 1`
- `size = 10`

Allowed page sizes:

- `10`
- `20`
- `50`

### 4.2 Behavior

The workflow list should:

- apply existing filters first
- sort in the current repository-driven order
- paginate the filtered result in memory

The page should show:

- current page number
- total item count
- total page count
- page size selector
- previous / next buttons

HTMX refreshes must preserve current filter and pagination parameters.

### 4.3 Fragment behavior

`/workflows/table` should render:

- the workflow table
- the pagination summary
- the pagination controls

This keeps the auto-refresh area self-contained.

## 5. Activity Timeline Pagination

### 5.1 Route and query parameters

Workflow detail page remains:

- `GET /workflows/{workflowId}`

Timeline fragment route remains:

- `GET /workflows/{workflowId}/timeline`

Add timeline pagination query parameters:

- `activityPage`
- `activitySize`

Default values:

- `activityPage = 1`
- `activitySize = 5`

Allowed page sizes:

- `5`
- `10`
- `20`

### 5.2 Behavior

The timeline should:

- keep the existing activity ordering
- paginate only the activity table section
- preserve the summary section and operations section untouched

The detail page should render:

- timeline table for the current page
- total activity count
- current activity page
- total activity pages
- previous / next buttons
- activity page size selector

HTMX paging on timeline should update only the timeline container.

Auto-refresh should preserve current `activityPage` and `activitySize`.

## 6. UI Refresh Scope

The UI refinement should stay intentionally modest.

### 6.1 Workflow list page

Improve:

- page title area
- filter form grouping
- table container styling
- empty-state readability
- operations button spacing
- pagination bar placement and hierarchy

### 6.2 Workflow detail page

Improve:

- section card styling
- summary section spacing
- activity table readability
- long input/output cell rendering
- pagination bar consistency with workflow list page

### 6.3 Visual direction

Use an internal operations-console style:

- clean light background
- clear table headers
- soft card containers
- subtle borders and shadows
- colored status badges for `RUNNING`, `SUCCESSFUL`, `FAILED`, `HUMAN_PROCESSING`, `TERMINATED`

Do not introduce overly decorative effects.

## 7. Data And View Model Changes

Add lightweight pagination view models, for example:

- `PageView<T>`
- `PaginationView`

These models should contain:

- current page
- page size
- total items
- total pages
- page items
- has previous
- has next

The controller layer should pass paged views to templates instead of raw lists where pagination is needed.

## 8. Implementation Boundaries

For this iteration:

- repository interfaces may continue returning full lists
- pagination is applied in service layer
- existing filter logic remains unchanged
- existing workflow and activity query semantics remain unchanged

This keeps the refinement low-risk.

If data volume later becomes large, pagination can be moved down into repository SQL as a future optimization.

## 9. Testing Strategy

Add and update tests for:

- workflow list default page size
- workflow list page switching
- workflow list page size switching
- workflow list HTMX fragment rendering with pagination controls
- activity timeline default page size
- activity timeline page switching
- activity timeline page size switching
- detail timeline fragment rendering with pagination controls
- visual class and structure assertions where they are stable and meaningful

The tests should remain focused on behavior and rendered output, not implementation internals.

## 10. Success Criteria

This refinement is complete when:

- `/workflows` displays paginated workflow results with stable filters
- `/workflows/{workflowId}` displays paginated activity timeline results
- both pages preserve pagination state during HTMX refresh
- the UI is noticeably clearer without becoming heavier or harder to maintain
- all relevant ops console tests pass
