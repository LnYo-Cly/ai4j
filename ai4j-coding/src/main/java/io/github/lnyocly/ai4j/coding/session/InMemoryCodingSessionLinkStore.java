package io.github.lnyocly.ai4j.coding.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCodingSessionLinkStore implements CodingSessionLinkStore {

    private final Map<String, CodingSessionLink> linksByChildSessionId = new ConcurrentHashMap<String, CodingSessionLink>();

    @Override
    public CodingSessionLink save(CodingSessionLink link) {
        if (link == null || isBlank(link.getChildSessionId())) {
            throw new IllegalArgumentException("childSessionId is required");
        }
        CodingSessionLink stored = link.toBuilder().build();
        linksByChildSessionId.put(stored.getChildSessionId(), stored);
        return stored.toBuilder().build();
    }

    @Override
    public List<CodingSessionLink> listLinks() {
        return sort(linksByChildSessionId.values());
    }

    @Override
    public List<CodingSessionLink> listLinksByParentSessionId(String parentSessionId) {
        if (isBlank(parentSessionId)) {
            return Collections.emptyList();
        }
        List<CodingSessionLink> items = new ArrayList<CodingSessionLink>();
        for (CodingSessionLink link : linksByChildSessionId.values()) {
            if (link != null && parentSessionId.equals(link.getParentSessionId())) {
                items.add(link.toBuilder().build());
            }
        }
        sortInPlace(items);
        return items;
    }

    @Override
    public CodingSessionLink findByChildSessionId(String childSessionId) {
        CodingSessionLink link = childSessionId == null ? null : linksByChildSessionId.get(childSessionId);
        return link == null ? null : link.toBuilder().build();
    }

    private List<CodingSessionLink> sort(Iterable<CodingSessionLink> values) {
        List<CodingSessionLink> items = new ArrayList<CodingSessionLink>();
        for (CodingSessionLink value : values) {
            if (value != null) {
                items.add(value.toBuilder().build());
            }
        }
        sortInPlace(items);
        return items;
    }

    private void sortInPlace(List<CodingSessionLink> items) {
        Collections.sort(items, new Comparator<CodingSessionLink>() {
            @Override
            public int compare(CodingSessionLink left, CodingSessionLink right) {
                long leftTime = left == null ? 0L : left.getCreatedAtEpochMs();
                long rightTime = right == null ? 0L : right.getCreatedAtEpochMs();
                if (leftTime == rightTime) {
                    String leftId = left == null ? null : left.getChildSessionId();
                    String rightId = right == null ? null : right.getChildSessionId();
                    if (leftId == null) {
                        return rightId == null ? 0 : -1;
                    }
                    if (rightId == null) {
                        return 1;
                    }
                    return leftId.compareTo(rightId);
                }
                return leftTime < rightTime ? -1 : 1;
            }
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
