package com.symbolcourt.engine;

public record Diagnostic(String severity, String code, String message, String objectId) {
}
