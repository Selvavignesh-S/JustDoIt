package com.justdoit.backend.dto;

import java.util.List;

// Infinite scroll only needs "is there another page?" (UI plan 8.4); page numbers start at 0
public record PageResponse<T>(List<T> items, int page, boolean hasNext) {
}
