package com.filestorage.ai.support;

import com.filestorage.ai.indexing.AsyncConfig;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared setup for integration tests: real pgvector (Testcontainers), fake embeddings,
 * a mocked ChatModel, and indexing that runs synchronously right after commit so tests
 * can assert without waiting. Ollama is never contacted.
 */
@SpringBootTest(properties = {
        "spring.ai.model.embedding=none",
        "spring.ai.model.chat=none",
        "storage.location=target/it-uploads"
})
@Import(AiTestConfig.class)
public abstract class AbstractAiIT {

    @MockitoBean
    protected ChatModel chatModel;

    /** Replaces the bounded async indexing pool with a synchronous executor. */
    @TestBean(name = AsyncConfig.INDEXING_EXECUTOR, methodName = "synchronousIndexingExecutor")
    protected Executor indexingExecutor;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    static Executor synchronousIndexingExecutor() {
        return new SyncTaskExecutor();
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM vector_store");
        jdbcTemplate.update("DELETE FROM files");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    protected static void loginAs(String username, boolean admin) {
        var authorities = List.of(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, authorities));
    }
}
