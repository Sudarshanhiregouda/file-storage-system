package com.filestorage.ai.ask;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** HTTP contract of /api/ai/ask: JSON body in, answer or RFC 7807 problem out. */
class AskControllerTest {

    private final AskService askService = mock(AskService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AskController(askService))
                .setControllerAdvice(new AskExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsAnswerAndSources() throws Exception {
        when(askService.ask(any())).thenReturn(new AskResponse("Use a supervisor [1].",
                List.of(new SourceRef(1L, "jvm-oom.md", null, 0, 0.9, "Use a supervisor")), 42));

        mvc.perform(post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"OOM?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Use a supervisor [1]."))
                .andExpect(jsonPath("$.sources[0].fileName").value("jvm-oom.md"));
    }

    @Test
    void modelFailure_returns503ProblemDetail() throws Exception {
        when(askService.ask(any())).thenThrow(new AnswerUnavailableException(new RuntimeException("boom")));

        mvc.perform(post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"OOM?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value("The answer service is unavailable right now"));
    }

    @Test
    void blankQuestion_returns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("question must not be blank"));
    }
}
