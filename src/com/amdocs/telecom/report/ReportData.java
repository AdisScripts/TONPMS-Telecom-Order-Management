package com.amdocs.telecom.report;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReportData {
    private final String title;
    private final List<String> headers;
    private final List<List<String>> rows;
    private final LocalDateTime generatedAt;
    private final Map<String, String> summaryMetrics;

    public ReportData(String title, List<String> headers, List<List<String>> rows) {
        this(title, headers, rows, new HashMap<>());
    }

    public ReportData(String title, List<String> headers, List<List<String>> rows, Map<String, String> summaryMetrics) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.headers = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(headers, "headers must not be null")));
        List<List<String>> copyRows = new ArrayList<>();
        if (rows != null) {
            for (List<String> row : rows) {
                copyRows.add(Collections.unmodifiableList(new ArrayList<>(row != null ? row : Collections.emptyList())));
            }
        }
        this.rows = Collections.unmodifiableList(copyRows);
        this.generatedAt = LocalDateTime.now();
        this.summaryMetrics = Collections.unmodifiableMap(new HashMap<>(summaryMetrics != null ? summaryMetrics : Collections.emptyMap()));
    }

    public String getTitle() { return title; }
    public List<String> getHeaders() { return headers; }
    public List<List<String>> getRows() { return rows; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public Map<String, String> getSummaryMetrics() { return summaryMetrics; }

    @Override
    public String toString() {
        return "ReportData{title='" + title + "', headers=" + headers + ", rowCount=" + rows.size() + ", generatedAt=" + generatedAt + "}";
    }
}
