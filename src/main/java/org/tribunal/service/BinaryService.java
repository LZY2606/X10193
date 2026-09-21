package org.tribunal.service;

import java.util.List;
import org.tribunal.model.ParsedBinary;

public interface BinaryService {
    ParsedBinary importBinary(String filename, byte[] payload);

    ParsedBinary parseInMemory(String filename, byte[] payload);

    List<ParsedBinary> list();

    byte[] payload(long id);
}
