package dev.bento.menu.core.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collected validation issues for /bento validate and reload logs.
 */
public final class ValidationReport {

    public enum Severity {
        ERROR,
        WARNING,
        INFO
    }

    public record Issue(Severity severity, String file, String path, String message) {
        @Override
        public String toString() {
            String location = file == null ? "" : file;
            if (path != null && !path.isBlank()) {
                location += (location.isEmpty() ? "" : " ") + "[" + path + "]";
            }
            return severity + " " + (location.isEmpty() ? "" : location + ": ") + message;
        }
    }

    private final List<Issue> issues = Collections.synchronizedList(new ArrayList<>());

    public void error(String file, String path, String message) {
        issues.add(new Issue(Severity.ERROR, file, path, message));
    }

    public void warning(String file, String path, String message) {
        issues.add(new Issue(Severity.WARNING, file, path, message));
    }

    public void info(String file, String path, String message) {
        issues.add(new Issue(Severity.INFO, file, path, message));
    }

    public List<Issue> issues() {
        synchronized (issues) {
            return List.copyOf(issues);
        }
    }

    public boolean hasErrors() {
        synchronized (issues) {
            return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public int errorCount() {
        synchronized (issues) {
            return (int) issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count();
        }
    }

    public int warningCount() {
        synchronized (issues) {
            return (int) issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count();
        }
    }

    public void merge(ValidationReport other) {
        synchronized (issues) {
            issues.addAll(other.issues());
        }
    }
}
