package com.filestorage.ai.ask;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.filestorage.ai.support.AbstractAiIT;
import com.filestorage.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** End to end through the real security filter chain: JWT in, owner-scoped cited answer out. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "ai.similarity-threshold=0.1")
class AskEndpointIT extends AbstractAiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    private String bearer(String username) {
        return "Bearer " + jwtService.generateToken(username, "USER");
    }

    private void upload(String username, String fileName, String content) throws Exception {
        mvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", fileName, "text/markdown",
                                content.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", bearer(username)))
                .andExpect(status().isCreated());
    }

    private void seed() throws Exception {
        upload("alice", "jvm-oom.md",
                "Services throw OutOfMemoryError after a deploy when a duplicate process is running. Check ps for two java processes.");
        upload("bob", "travel-policy.md",
                "The travel reimbursement limit per trip is 2500 USD unless a director approves more.");
    }

    private String ask(String question) {
        return "{\"question\":\"" + question + "\"}";
    }

    @Test
    void ask_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/ai/ask").contentType(MediaType.APPLICATION_JSON).content(ask("anything")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void alice_cannotGetAnswersFromBobsDocuments() throws Exception {
        seed();

        mvc.perform(post("/api/ai/ask").header("Authorization", bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ask("What is the travel reimbursement limit?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskService.NOT_FOUND_ANSWER))
                .andExpect(jsonPath("$.sources").isEmpty());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void alice_getsCitedAnswerFromHerOwnDocuments() throws Exception {
        seed();
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("Look for a duplicate java process [1].")))));

        mvc.perform(post("/api/ai/ask").header("Authorization", bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ask("Services throw OutOfMemoryError after a deploy. What should I check?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Look for a duplicate java process [1]."))
                .andExpect(jsonPath("$.sources[*].fileName", everyItem(is("jvm-oom.md"))));
    }

    @Test
    void ownerFieldInBodyIsIgnored() throws Exception {
        seed();

        // An extra "owner"/"username" field must not change whose documents are searched.
        mvc.perform(post("/api/ai/ask").header("Authorization", bearer("alice"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"travel reimbursement limit\",\"username\":\"bob\",\"ownerName\":\"bob\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(AskService.NOT_FOUND_ANSWER));
    }
}
