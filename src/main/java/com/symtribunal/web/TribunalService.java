package com.symtribunal.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.symtribunal.engine.ComparisonResult;
import com.symtribunal.engine.LinkPolicy;
import com.symtribunal.engine.ResolutionReport;
import com.symtribunal.engine.ResolverEngine;
import com.symtribunal.store.BinaryRecord;
import com.symtribunal.store.BinaryStore;
import com.symtribunal.store.SchemeRecord;
import com.symtribunal.store.SchemeStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TribunalService {

    private final BinaryStore binaries;
    private final SchemeStore schemes;
    private final ResolverEngine engine;
    private final ObjectMapper mapper;

    public TribunalService(BinaryStore binaries, SchemeStore schemes,
                           ResolverEngine engine, ObjectMapper mapper) {
        this.binaries = binaries;
        this.schemes = schemes;
        this.engine = engine;
        this.mapper = mapper;
    }

    public BinaryRecord upload(String fileName, byte[] data) {
        return binaries.save(fileName, data);
    }

    public List<BinaryRecord> binaries() {
        return binaries.list();
    }

    public ResolutionReport runScheme(String name, String policy, String machine,
                                      List<String> binaryIds) {
        List<ResolverEngine.Binary> inputs = new ArrayList<>();
        int order = 0;
        for (String id : binaryIds) {
            BinaryRecord record = binaries.get(id);
            if (record == null) {
                throw new IllegalArgumentException("unknown binary id: " + id);
            }
            ResolverEngine.Binary binary = new ResolverEngine.Binary();
            binary.id = record.id;
            binary.fileName = record.fileName;
            binary.data = record.data;
            inputs.add(binary);
            order++;
        }
        LinkPolicy linkPolicy;
        try {
            linkPolicy = LinkPolicy.valueOf(policy == null ? "GNU_STATIC" : policy);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown policy: " + policy);
        }
        String id = "sch_" + UUID.randomUUID().toString().substring(0, 12);
        ResolutionReport report = engine.resolve(id,
                name == null || name.isBlank() ? ("方案-" + id) : name,
                linkPolicy, machine == null ? "x86-64" : machine, inputs);
        SchemeRecord record = new SchemeRecord();
        record.id = id;
        record.name = report.schemeName;
        record.policy = report.policy;
        record.machine = report.machine;
        record.createdAt = report.createdAt;
        record.inputOrder = String.join(",", binaryIds);
        try {
            record.reportJson = mapper.writeValueAsString(report);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        schemes.save(record);
        return report;
    }

    public List<SchemeRecord> schemes() {
        return schemes.list();
    }

    public ResolutionReport scheme(String id) {
        SchemeRecord record = schemes.get(id);
        if (record == null) {
            return null;
        }
        try {
            return mapper.readValue(record.reportJson, ResolutionReport.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public ComparisonResult compare(String idA, String idB) {
        ResolutionReport a = scheme(idA);
        ResolutionReport b = scheme(idB);
        if (a == null || b == null) {
            throw new IllegalArgumentException("unknown scheme id");
        }
        return engine.compare(a, b);
    }

    public static String randomToken() {
        return HexFormat.of().formatHex(UUID.randomUUID().toString().getBytes()).substring(0, 8);
    }

    public Instant now() {
        return Instant.now();
    }
}
