package com.example.symbolcourt.engine;

import com.example.symbolcourt.domain.Models.ArchiveFile;
import com.example.symbolcourt.domain.Models.LoadOrderEntry;
import com.example.symbolcourt.domain.Models.ParsedObject;

import java.util.List;

public record LinkRequest(String planId, List<ParsedObject> explicitObjects,
                          List<ArchiveFile> archives, List<LoadOrderEntry> loadOrder,
                          LinkPolicy policy) {
}
