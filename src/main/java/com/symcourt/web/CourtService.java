package com.symcourt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symcourt.elf.Archive;
import com.symcourt.elf.ElfFile;
import com.symcourt.engine.Adjudicator;
import com.symcourt.engine.Adjudicator.ArchiveUnit;
import com.symcourt.engine.Adjudicator.LoadUnit;
import com.symcourt.engine.Adjudicator.ObjectUnit;
import com.symcourt.engine.EngineModel.CompareEntry;
import com.symcourt.engine.EngineModel.Result;
import com.symcourt.engine.PlatformPolicy;
import com.symcourt.store.CourtStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CourtService {

    private final CourtStore store;
    private final ObjectMapper mapper = new ObjectMapper();

    public CourtService(CourtStore store) {
        this.store = store;
    }

    /** Imports one file; a corrupt file is isolated and never aborts the batch. */
    public CourtStore.ObjectRow importObject(String name, byte[] data) {
        String sha = sha256(data);
        String kind;
        String error = null;
        try {
            if (Archive.isArchive(data)) {
                Archive.parse(data);
                kind = "archive";
            } else {
                ElfFile.parse(data);
                kind = "elf";
            }
        } catch (RuntimeException e) {
            kind = "corrupt";
            error = e.getMessage();
        }
        long id = store.insertObject(name, kind, sha, data, error);
        return store.getObject(id);
    }

    public List<CourtStore.ObjectRow> listObjects() {
        return store.listObjects();
    }

    public long createScheme(String name, List<Long> objectIds, String policyName) {
        PlatformPolicy policy = PlatformPolicy.byName(policyName);
        List<LoadUnit> units = new ArrayList<>();
        List<String> usedNames = new ArrayList<>();
        for (Long id : objectIds) {
            CourtStore.ObjectRow row = store.getObject(id);
            if (row == null) {
                throw new IllegalArgumentException("object " + id + " not found");
            }
            if (row.error() != null) {
                continue; // corrupt objects stay isolated
            }
            if ("archive".equals(row.kind())) {
                units.add(new ArchiveUnit(row.name(), Archive.parse(row.data())));
            } else {
                units.add(new ObjectUnit(row.name(), ElfFile.parse(row.data())));
            }
            usedNames.add(row.name());
        }
        Result result = new Adjudicator(policy).adjudicate(units);
        try {
            String objectsJson = mapper.writeValueAsString(new SchemeObjects(objectIds, usedNames));
            return store.insertScheme(name, policy.name(), objectsJson,
                    mapper.writeValueAsString(result));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private record SchemeObjects(List<Long> ids, List<String> names) { }

    public List<CourtStore.SchemeRow> listSchemes() {
        return store.listSchemes();
    }

    public record SchemeView(CourtStore.SchemeRow scheme, Result result) { }

    public SchemeView getScheme(long id) {
        CourtStore.SchemeRow row = store.getScheme(id);
        if (row == null) {
            return null;
        }
        try {
            return new SchemeView(row, mapper.readValue(row.resultJson(), Result.class));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public List<CompareEntry> compare(long a, long b) {
        SchemeView va = getScheme(a);
        SchemeView vb = getScheme(b);
        if (va == null || vb == null) {
            throw new IllegalArgumentException("scheme not found");
        }
        return Adjudicator.compare(va.result(), vb.result());
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
