package com.filestorage.ai.ask;

/** The chat model failed or timed out. Mapped to HTTP 503. */
public class AnswerUnavailableException extends RuntimeException {

    public AnswerUnavailableException(Throwable cause) {
        super("The answer service is unavailable right now", cause);
    }
}
