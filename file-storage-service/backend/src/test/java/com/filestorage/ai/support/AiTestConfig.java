package com.filestorage.ai.support;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real pgvector in Docker plus deterministic fake embeddings. */
@TestConfiguration(proxyBeanMethods = false)
public class AiTestConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> pgvector() {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @Primary
    EmbeddingModel fakeEmbeddingModel() {
        return new FakeEmbeddingModel();
    }
}
