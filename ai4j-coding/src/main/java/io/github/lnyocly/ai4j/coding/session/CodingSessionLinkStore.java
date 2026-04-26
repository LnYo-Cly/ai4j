package io.github.lnyocly.ai4j.coding.session;

import java.util.List;

public interface CodingSessionLinkStore {

    CodingSessionLink save(CodingSessionLink link);

    List<CodingSessionLink> listLinks();

    List<CodingSessionLink> listLinksByParentSessionId(String parentSessionId);

    CodingSessionLink findByChildSessionId(String childSessionId);
}
