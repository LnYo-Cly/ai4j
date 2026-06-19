package io.github.lnyocly.ai4j.agent.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultContextProjector implements ContextProjector {

    @Override
    public ContextProjection project(List<Object> items, ContextBudget budget) {
        List<Object> source = copyItems(items);
        int sourceApproxChars = approximateChars(source);
        List<String> notes = new ArrayList<String>();
        List<Object> projected = copyItems(source);
        boolean itemLimitApplied = false;
        boolean characterLimitApplied = false;

        if (budget != null && budget.getMaxItems() != null && budget.getMaxItems() >= 0) {
            List<Object> limited = applyItemLimit(projected, budget.getMaxItems(), budget.resolvedPinnedPrefixItems());
            itemLimitApplied = limited.size() < projected.size();
            if (itemLimitApplied) {
                notes.add("maxItems applied: " + budget.getMaxItems());
            }
            projected = limited;
        }

        if (budget != null && budget.getMaxApproxChars() != null && budget.getMaxApproxChars() >= 0) {
            List<Object> limited = applyCharacterLimit(projected, budget.getMaxApproxChars(), budget.resolvedPinnedPrefixItems());
            characterLimitApplied = limited.size() < projected.size() || approximateChars(limited) < approximateChars(projected);
            if (characterLimitApplied) {
                notes.add("maxApproxChars applied: " + budget.getMaxApproxChars());
            }
            projected = limited;
        }

        ContextReport report = ContextReport.builder()
                .sourceItemCount(source.size())
                .projectedItemCount(projected.size())
                .droppedItemCount(Math.max(0, source.size() - projected.size()))
                .sourceApproxChars(sourceApproxChars)
                .projectedApproxChars(approximateChars(projected))
                .itemLimitApplied(itemLimitApplied)
                .characterLimitApplied(characterLimitApplied)
                .notes(notes)
                .build();
        return ContextProjection.of(projected, report);
    }

    private List<Object> applyItemLimit(List<Object> items, int maxItems, int pinnedPrefixItems) {
        if (maxItems < 0 || items.size() <= maxItems) {
            return copyItems(items);
        }
        if (maxItems == 0) {
            return new ArrayList<Object>();
        }
        int prefixCount = Math.min(Math.min(pinnedPrefixItems, maxItems), items.size());
        int tailCount = maxItems - prefixCount;
        List<Object> result = new ArrayList<Object>(maxItems);
        for (int i = 0; i < prefixCount; i++) {
            result.add(items.get(i));
        }
        int tailStart = Math.max(prefixCount, items.size() - tailCount);
        for (int i = tailStart; i < items.size(); i++) {
            result.add(items.get(i));
        }
        return result;
    }

    private List<Object> applyCharacterLimit(List<Object> items, int maxApproxChars, int pinnedPrefixItems) {
        if (maxApproxChars < 0 || approximateChars(items) <= maxApproxChars) {
            return copyItems(items);
        }
        if (maxApproxChars == 0) {
            return new ArrayList<Object>();
        }
        int prefixCount = Math.min(pinnedPrefixItems, items.size());
        List<Object> prefix = new ArrayList<Object>();
        int used = 0;
        for (int i = 0; i < prefixCount; i++) {
            Object item = items.get(i);
            int size = approximateChars(item);
            if (used + size <= maxApproxChars || prefix.isEmpty()) {
                prefix.add(item);
                used += size;
            }
        }
        List<Object> tail = new ArrayList<Object>();
        for (int i = items.size() - 1; i >= prefixCount; i--) {
            Object item = items.get(i);
            int size = approximateChars(item);
            if (used + size > maxApproxChars && !tail.isEmpty()) {
                continue;
            }
            if (used + size <= maxApproxChars || tail.isEmpty()) {
                tail.add(item);
                used += size;
            }
        }
        Collections.reverse(tail);
        List<Object> result = new ArrayList<Object>(prefix.size() + tail.size());
        result.addAll(prefix);
        result.addAll(tail);
        return result;
    }

    private int approximateChars(List<Object> items) {
        int count = 0;
        if (items != null) {
            for (Object item : items) {
                count += approximateChars(item);
            }
        }
        return count;
    }

    private int approximateChars(Object item) {
        return item == null ? 0 : String.valueOf(item).length();
    }

    private List<Object> copyItems(List<Object> source) {
        return source == null ? new ArrayList<Object>() : new ArrayList<Object>(source);
    }
}
