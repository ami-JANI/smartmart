package com.smartmart.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

class ApiExceptionHandlerTest {

    record Line(@Min(value = 1, message = "Quantity must be at least 1") int quantity) {
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    static class OutOfStock extends RuntimeException {
        OutOfStock() {
            super("Not enough stock for Miniket rice");
        }
    }

    @RestController
    static class SampleController {

        @PostMapping("/api/v1/lines")
        void add(@Valid @RequestBody Line line) {
        }

        @GetMapping("/api/v1/lines")
        void find(@RequestParam int quantity) {
        }

        @GetMapping("/api/v1/conflict")
        void conflict() {
            throw new OutOfStock();
        }

        @GetMapping("/api/v1/broken")
        void broken() {
            throw new BadSqlGrammarException("select", "SELECT * FROM secret_table",
                    new SQLException("relation secret_table does not exist"));
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void validationErrorUsesTheFieldMessage() throws Exception {
        mvc.perform(post("/api/v1/lines").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Quantity must be at least 1"))
                .andExpect(jsonPath("$.path").value("/api/v1/lines"));
    }

    @Test
    void unreadableJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/lines").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request has a missing or badly formed value"));
    }

    @Test
    void missingParameterKeepsSpringsStatus() throws Exception {
        mvc.perform(get("/api/v1/lines"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Bad Request"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatusAndMessage() throws Exception {
        mvc.perform(get("/api/v1/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Not enough stock for Miniket rice"));
    }

    @Test
    void unexpectedErrorHidesSqlAndStackTrace() throws Exception {
        mvc.perform(get("/api/v1/broken"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong. Please try again."))
                .andExpect(content().string(not(containsString("secret_table"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }
}
