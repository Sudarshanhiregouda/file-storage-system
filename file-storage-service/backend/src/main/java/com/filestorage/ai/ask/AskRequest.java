package com.filestorage.ai.ask;

import jakarta.validation.constraints.NotBlank;

/**
 * A question about the caller's own documents. There is deliberately no user or owner field:
 * identity always comes from the authenticated principal.
 *
 * @param topK optional number of chunks to retrieve; clamped to {@code ai.max-top-k}
 */
public record AskRequest(@NotBlank(message = "question must not be blank") String question, Integer topK) {
}
