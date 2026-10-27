package com.filestorage.ai.ask;

/** The question failed validation that depends on configuration (e.g. maximum length). Mapped to HTTP 400. */
public class InvalidQuestionException extends RuntimeException {

    public InvalidQuestionException(String message) {
        super(message);
    }
}
