package com.filestorage.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application-level AI settings, bound from {@code ai.*}.
 *
 * @param defaultTopK         number of chunks retrieved when the caller does not ask for a specific amount
 * @param maxTopK             hard upper bound on chunks per query, whatever the caller asks for
 * @param similarityThreshold minimum similarity (0..1) a chunk needs to count as a hit
 * @param maxQuestionLength   maximum characters accepted in a question
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        @DefaultValue("5") int defaultTopK,
        @DefaultValue("10") int maxTopK,
        @DefaultValue("0.5") double similarityThreshold,
        @DefaultValue("1000") int maxQuestionLength) {
}
