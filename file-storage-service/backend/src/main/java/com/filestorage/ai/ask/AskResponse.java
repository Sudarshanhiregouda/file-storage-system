package com.filestorage.ai.ask;

import java.util.List;

/**
 * @param answer    the model's answer, citing sources as [1], [2], ...
 * @param sources   the numbered sources the model was given, in citation order
 * @param latencyMs end-to-end time for retrieval plus generation
 */
public record AskResponse(String answer, List<SourceRef> sources, long latencyMs) {
}
