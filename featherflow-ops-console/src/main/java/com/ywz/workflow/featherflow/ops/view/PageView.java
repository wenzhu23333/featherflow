package com.ywz.workflow.featherflow.ops.view;

import java.util.List;

public class PageView<T> {

    private final List<T> items;
    private final PaginationView pagination;

    public PageView(List<T> items, PaginationView pagination) {
        this.items = items;
        this.pagination = pagination;
    }

    public List<T> getItems() {
        return items;
    }

    public PaginationView getPagination() {
        return pagination;
    }
}
