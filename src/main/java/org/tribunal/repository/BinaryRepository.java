package org.tribunal.repository;

import java.util.List;
import java.util.Optional;
import org.tribunal.model.ParsedBinary;

public interface BinaryRepository {
    ParsedBinary save(String originalName, byte[] payload, ParsedBinary parsed);

    Optional<ParsedBinary> findById(long id);

    List<ParsedBinary> findAll();

    byte[] readPayload(long id);
}
