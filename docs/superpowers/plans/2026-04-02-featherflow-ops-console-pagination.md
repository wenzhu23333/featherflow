# FeatherFlow Ops Console Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add lightweight pagination to the workflow list and activity timeline, while polishing the ops console UI without changing its lightweight server-rendered architecture.

**Architecture:** Keep repository queries unchanged and apply pagination in the service layer. Expose pagination state through controller query parameters, render controls with Thymeleaf fragments, and preserve state during HTMX refresh so the UI stays shareable and easy to maintain.

**Tech Stack:** Spring Boot 2.7, Thymeleaf, HTMX, JdbcTemplate, JUnit 5, MockMvc

---

### Task 1: Add Pagination View Models And Service Support

**Files:**
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/PaginationView.java`
- Create: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/PageView.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/WorkflowDetailView.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowListPageTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowDetailPageTest.java`

- [ ] **Step 1: Write the failing tests for workflow list pagination**

```java
@Test
void shouldRenderWorkflowListPaginationControls() throws Exception {
    MvcResult result = mockMvc.perform(get("/workflows").param("page", "1").param("size", "1"))
        .andExpect(status().isOk())
        .andReturn();

    String page = result.getResponse().getContentAsString();
    assertThat(page).contains("workflow-page-size");
    assertThat(page).contains("第 1 / 2 页");
    assertThat(page).contains("共 2 条");
    assertThat(page).contains("page=2");
}
```

- [ ] **Step 2: Run the list page test to verify it fails**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowListPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- FAIL because the page does not yet contain pagination controls or page slicing

- [ ] **Step 3: Write the failing tests for activity timeline pagination**

```java
@Test
void shouldRenderTimelinePaginationControls() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/workflows/wf-detail-0001")
                .param("activityPage", "1")
                .param("activitySize", "2")
        )
        .andExpect(status().isOk())
        .andReturn();

    String page = result.getResponse().getContentAsString();
    assertThat(page).contains("activity-page-size");
    assertThat(page).contains("活动第 1 / 2 页");
    assertThat(page).contains("timeline-row-act-900");
    assertThat(page).contains("timeline-row-act-100");
    assertThat(page).doesNotContain("timeline-row-act-500");
}
```

- [ ] **Step 4: Run the detail page test to verify it fails**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowDetailPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- FAIL because the timeline still renders all activities and has no pagination summary

- [ ] **Step 5: Add minimal pagination view models**

```java
public class PaginationView {

    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;

    public PaginationView(int page, int size, long totalItems) {
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / size);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages;
    }
}
```

```java
public class PageView<T> {

    private final List<T> items;
    private final PaginationView pagination;

    public PageView(List<T> items, PaginationView pagination) {
        this.items = items;
        this.pagination = pagination;
    }
}
```

- [ ] **Step 6: Add service-layer pagination helpers**

```java
private <T> PageView<T> paginate(List<T> source, int requestedPage, int requestedSize) {
    int size = normalizePageSize(requestedSize, 10, Arrays.asList(10, 20, 50));
    int totalItems = source.size();
    int totalPages = totalItems == 0 ? 1 : (int) Math.ceil((double) totalItems / size);
    int page = Math.max(1, Math.min(requestedPage, totalPages));
    int fromIndex = Math.min((page - 1) * size, totalItems);
    int toIndex = Math.min(fromIndex + size, totalItems);
    return new PageView<T>(source.subList(fromIndex, toIndex), new PaginationView(page, size, totalItems));
}
```

- [ ] **Step 7: Extend workflow detail view to carry paged activities**

```java
public class WorkflowDetailView {
    // existing fields ...
    private final PageView<ActivityTimelineItemView> activityPage;
}
```

- [ ] **Step 8: Run the focused tests to verify the service-level pagination changes pass**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowListPageTest,WorkflowDetailPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS for pagination-aware page rendering tests, though templates may still need more work in later tasks

- [ ] **Step 9: Commit**

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
git add featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/PageView.java \
        featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/PaginationView.java \
        featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java \
        featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/view/WorkflowDetailView.java \
        featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowListPageTest.java \
        featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowDetailPageTest.java
git commit -m "feat: add ops console pagination models"
```

### Task 2: Add Workflow List Pagination To Controllers And Fragments

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowPageController.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/list.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/list-table.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowListPageTest.java`

- [ ] **Step 1: Write the failing fragment test for page switching**

```java
@Test
void shouldRenderSecondWorkflowPageFragment() throws Exception {
    MvcResult result = mockMvc.perform(get("/workflows/table").param("page", "2").param("size", "1"))
        .andExpect(status().isOk())
        .andReturn();

    String fragment = result.getResponse().getContentAsString();
    assertThat(fragment).contains("workflow-row-wf-terminated-01");
    assertThat(fragment).doesNotContain("workflow-row-wf-running-0001");
    assertThat(fragment).contains("page=1");
}
```

- [ ] **Step 2: Run the workflow list test to verify it fails**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowListPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- FAIL because the controller does not yet pass pagination state to the fragment

- [ ] **Step 3: Add pagination parameters to the workflow list controller methods**

```java
@GetMapping("/workflows")
public String workflows(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "10") int size,
    // existing filter params...
    Model model
) {
    PageView<WorkflowListItemView> workflowPage = workflowQueryService.listWorkflows(filter, page, size);
    model.addAttribute("workflowPage", workflowPage);
    model.addAttribute("pagination", workflowPage.getPagination());
    return "workflows/list";
}
```

- [ ] **Step 4: Update the list page and fragment to render pagination controls**

```html
<div class="table-toolbar">
    <div class="page-summary"
         th:text="|第 ${pagination.page} / ${pagination.totalPages} 页，共 ${pagination.totalItems} 条|">
        第 1 / 1 页，共 0 条
    </div>
    <label>
        每页
        <select id="workflow-page-size" name="size">
            <option value="10">10</option>
            <option value="20">20</option>
            <option value="50">50</option>
        </select>
    </label>
</div>
```

```html
<div class="pagination-bar">
    <a th:if="${pagination.hasPrevious}"
       th:hx-get="@{/workflows/table(page=${pagination.page - 1},size=${pagination.size},workflowId=${workflowId},bizId=${bizId},status=${status},workflowName=${workflowName},createdFrom=${createdFrom},createdTo=${createdTo},modifiedFrom=${modifiedFrom},modifiedTo=${modifiedTo})}">
        上一页
    </a>
</div>
```

- [ ] **Step 5: Run the workflow list tests to verify they pass**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowListPageTest,DefaultDemoDataPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS with list-page pagination controls rendered correctly for both test data and demo data

- [ ] **Step 6: Commit**

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
git add featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowPageController.java \
        featherflow-ops-console/src/main/resources/templates/workflows/list.html \
        featherflow-ops-console/src/main/resources/templates/workflows/list-table.html \
        featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowListPageTest.java
git commit -m "feat: paginate workflow list page"
```

### Task 3: Add Activity Timeline Pagination To Detail Page

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowPageController.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/detail.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/detail-timeline.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowDetailPageTest.java`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/DefaultDemoDataPageTest.java`

- [ ] **Step 1: Write the failing fragment test for timeline paging**

```java
@Test
void shouldRenderPagedDetailTimelineFragment() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/workflows/wf-detail-0001/timeline")
                .param("activityPage", "2")
                .param("activitySize", "2")
        )
        .andExpect(status().isOk())
        .andReturn();

    String fragment = result.getResponse().getContentAsString();
    assertThat(fragment).contains("timeline-row-act-500");
    assertThat(fragment).doesNotContain("timeline-row-act-900");
    assertThat(fragment).contains("活动第 2 / 2 页");
}
```

- [ ] **Step 2: Run the detail page test to verify it fails**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowDetailPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- FAIL because the timeline fragment still renders the full activity list and has no page state

- [ ] **Step 3: Thread activity pagination parameters through controller and service**

```java
@GetMapping("/workflows/{workflowId}/timeline")
public String workflowDetailTimeline(
    @PathVariable String workflowId,
    @RequestParam(defaultValue = "1") int activityPage,
    @RequestParam(defaultValue = "5") int activitySize,
    Model model
) {
    return workflowQueryService.getWorkflowTimeline(workflowId, activityPage, activitySize)
        .map(page -> {
            model.addAttribute("activityPageView", page);
            return "workflows/detail-timeline :: timeline(activityPageView=${activityPageView},workflowId=${workflowId})";
        })
        .orElseThrow(...);
}
```

- [ ] **Step 4: Update detail templates to preserve timeline page state during refresh**

```html
<div id="workflow-detail-timeline-container"
     th:attr="hx-get=@{/workflows/{workflowId}/timeline(workflowId=${detail.workflowId},activityPage=${detail.activityPage.pagination.page},activitySize=${detail.activityPage.pagination.size})}"
     hx-trigger="every 3s"
     hx-swap="innerHTML">
</div>
```

```html
<div class="pagination-bar">
    <span th:text="|活动第 ${activityPageView.pagination.page} / ${activityPageView.pagination.totalPages} 页，共 ${activityPageView.pagination.totalItems} 步|">
        活动第 1 / 1 页，共 0 步
    </span>
</div>
```

- [ ] **Step 5: Run the detail-related tests to verify they pass**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowDetailPageTest,DefaultDemoDataPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS with detail page and timeline fragment both rendering paged activities

- [ ] **Step 6: Commit**

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
git add featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/controller/WorkflowPageController.java \
        featherflow-ops-console/src/main/java/com/ywz/workflow/featherflow/ops/service/WorkflowQueryService.java \
        featherflow-ops-console/src/main/resources/templates/workflows/detail.html \
        featherflow-ops-console/src/main/resources/templates/workflows/detail-timeline.html \
        featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowDetailPageTest.java \
        featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/DefaultDemoDataPageTest.java
git commit -m "feat: paginate workflow activity timeline"
```

### Task 4: Polish The UI And Update Documentation

**Files:**
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/static/css/app.css`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/list.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/list-table.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/detail.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/detail-summary.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/detail-timeline.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/main/resources/templates/workflows/detail-operations.html`
- Modify: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/README.md`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowListPageTest.java`
- Test: `/Users/yangwenzhuo/Code/Codex/featherflow/featherflow-ops-console/src/test/java/com/ywz/workflow/featherflow/ops/controller/WorkflowDetailPageTest.java`

- [ ] **Step 1: Write the failing tests for the refreshed structural classes**

```java
assertThat(page).contains("class=\"page-shell\"");
assertThat(page).contains("class=\"page-card\"");
assertThat(page).contains("class=\"status-badge status-running\"");
assertThat(page).contains("class=\"pagination-bar\"");
```

- [ ] **Step 2: Run the ops console page tests to verify they fail**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dtest=WorkflowListPageTest,WorkflowDetailPageTest -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- FAIL because the current templates do not yet expose the refined structure

- [ ] **Step 3: Apply the visual refresh with modest, readable styles**

```css
.page-shell {
    max-width: 1440px;
    margin: 0 auto;
    padding: 24px;
}

.page-card {
    background: #ffffff;
    border: 1px solid #d7e0ea;
    border-radius: 14px;
    box-shadow: 0 8px 24px rgba(17, 24, 39, 0.06);
}

.status-badge.status-running { background: #e0f2fe; color: #075985; }
.status-badge.status-successful { background: #dcfce7; color: #166534; }
.status-badge.status-failed { background: #fee2e2; color: #991b1b; }
```

- [ ] **Step 4: Update the README with pagination behavior**

```md
## Pagination

- Workflow list defaults to page `1` and size `10`
- Activity timeline defaults to page `1` and size `5`
- HTMX refresh preserves current page and page size
```

- [ ] **Step 5: Run the full ops console test suite**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -pl featherflow-ops-console -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS for the full ops console module

- [ ] **Step 6: Run the full FeatherFlow test suite**

Run:

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
/tmp/apache-maven-3.9.9/bin/mvn -q -Dmaven.repo.local=/tmp/m2repo test
```

Expected:

- PASS for the full FeatherFlow multi-module build

- [ ] **Step 7: Commit**

```bash
cd /Users/yangwenzhuo/Code/Codex/featherflow
git add featherflow-ops-console/src/main/resources/static/css/app.css \
        featherflow-ops-console/src/main/resources/templates/workflows/list.html \
        featherflow-ops-console/src/main/resources/templates/workflows/list-table.html \
        featherflow-ops-console/src/main/resources/templates/workflows/detail.html \
        featherflow-ops-console/src/main/resources/templates/workflows/detail-summary.html \
        featherflow-ops-console/src/main/resources/templates/workflows/detail-timeline.html \
        featherflow-ops-console/src/main/resources/templates/workflows/detail-operations.html \
        featherflow-ops-console/README.md
git commit -m "feat: polish ops console pagination ui"
```

## Self-Review

- Spec coverage:
  - workflow list pagination: covered in Task 1 and Task 2
  - activity timeline pagination: covered in Task 1 and Task 3
  - HTMX state preservation: covered in Task 2 and Task 3
  - light UI refresh: covered in Task 4
- Placeholder scan:
  - no TBD or TODO placeholders remain
  - each task contains explicit files, commands, and expected outputs
- Type consistency:
  - `PageView` and `PaginationView` are introduced first, then reused consistently in controller, service, and templates
