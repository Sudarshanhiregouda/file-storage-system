package com.filestorage.ai.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic 768-dimension embeddings for tests, so CI never needs Ollama.
 * <p>
 * Hashing-trick bag of words: each lower-cased word adds 1 to the dimension its hash maps to,
 * then the vector is L2-normalised. Texts sharing words get high cosine similarity, which is
 * enough to exercise real pgvector SQL, ranking and metadata filters.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 768;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> inputs = request.getInstructions();
        for (int i = 0; i < inputs.size(); i++) {
            embeddings.add(new Embedding(vectorFor(inputs.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    public static float[] vectorFor(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text != null) {
            for (String token : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (!token.isEmpty()) {
                    vector[Math.floorMod(token.hashCode(), DIMENSIONS)] += 1f;
                }
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm == 0) {
            vector[0] = 1f; // avoid a zero vector: cosine distance is undefined for it
            return vector;
        }
        float inv = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= inv;
        }
        return vector;
    }
}
