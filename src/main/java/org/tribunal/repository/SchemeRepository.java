package org.tribunal.repository;

import java.util.List;
import java.util.Optional;
import org.tribunal.model.Scheme;

public interface SchemeRepository {
    Scheme save(String label, String policy, List<Long> orderedBinaryIds, String resultJson);

    Optional<Scheme> findById(long id);

    List<Scheme> findAllMetadata();

    String findResultJson(long id);
}
