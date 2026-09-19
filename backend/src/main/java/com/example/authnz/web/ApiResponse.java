package com.example.authnz.web;

import java.util.List;

/** Minimal response payload used by every sample endpoint to show what the caller can see. */
public record ApiResponse(String endpoint, String subject, List<String> authorities) {
}
