package com.filestorage.ai.ask;

import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RFC 7807 problem details for the AI endpoints only; existing file endpoints keep their format.
 * Extending ResponseEntityExceptionHandler turns malformed JSON, wrong media types etc. into ProblemDetail too.
 */
@RestControllerAdvice(assignableTypes = AskController.class)
public class AskExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(AnswerUnavailableException.class)
    public ProblemDetail handleUnavailable(AnswerUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "The answer service is unavailable right now");
        problem.setTitle("Answer service unavailable");
        problem.setType(URI.create("urn:securedocs:problem:answer-unavailable"));
        return problem;
    }

    @ExceptionHandler(InvalidQuestionException.class)
    public ProblemDetail handleInvalidQuestion(InvalidQuestionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid question");
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .findFirst()
                .orElse("Invalid request");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid question");
        return ResponseEntity.badRequest().body(problem);
    }
}
