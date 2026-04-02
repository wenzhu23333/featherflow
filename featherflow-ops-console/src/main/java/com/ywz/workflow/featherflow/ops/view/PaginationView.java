package com.ywz.workflow.featherflow.ops.view;

public class PaginationView {

    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
    private final boolean hasPrevious;
    private final boolean hasNext;
    private final int previousPage;
    private final int nextPage;

    public PaginationView(
        int page,
        int size,
        int totalPages,
        long totalElements,
        boolean hasPrevious,
        boolean hasNext,
        int previousPage,
        int nextPage
    ) {
        this.page = page;
        this.size = size;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.hasPrevious = hasPrevious;
        this.hasNext = hasNext;
        this.previousPage = previousPage;
        this.nextPage = nextPage;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public boolean isHasPrevious() {
        return hasPrevious;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public int getPreviousPage() {
        return previousPage;
    }

    public int getNextPage() {
        return nextPage;
    }
}
