package org.tribunal.service;

import java.util.List;
import org.tribunal.model.PlatformPolicy;
import org.tribunal.model.ResolutionResult;
import org.tribunal.model.Scheme;
import org.tribunal.model.SchemeComparison;

public interface SchemeService {
    Scheme create(String label, String policyName, List<Long> orderedBinaryIds);

    Scheme get(long id);

    ResolutionResult getResult(long id);

    List<Scheme> list();

    SchemeComparison compare(long schemeA, long schemeB);

    PlatformPolicy policy(String name);
}
