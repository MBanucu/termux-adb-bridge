package io.github.mbanucu.termux;

/** Callback for one command result, delivered by the library adapters. */
public interface TermuxResultCallback {
    void onTermuxResult(TermuxResult result);
}
