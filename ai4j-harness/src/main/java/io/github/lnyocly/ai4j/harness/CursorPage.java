package io.github.lnyocly.ai4j.harness;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic keyset/cursor page for CQRS projection reads.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CursorPage<T> {

    @Builder.Default
    private List<T> items = new ArrayList<T>();

    private String nextCursor;

    private boolean hasMore;

    @Builder.Default
    private long totalCount = -1L;

    public CursorPage(List<T> items, String nextCursor, boolean hasMore) {
        this(items, nextCursor, hasMore, -1L);
    }

    public static <T> CursorPage<T> empty() {
        return new CursorPage<T>(Collections.<T>emptyList(), null, false, 0L);
    }

    public boolean hasTotalCount() {
        return totalCount >= 0L;
    }

    public int size() {
        return items == null ? 0 : items.size();
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
