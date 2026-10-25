package com.trainconcierge.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the validation rules of {@link EchoRequest}. No Spring
 * context and no database required.
 */
class EchoRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("a valid message produces no violations")
    void validMessageHasNoViolations() {
        assertThat(validator.validate(new EchoRequest("platform 9 and 3/4"))).isEmpty();
    }

    @Test
    @DisplayName("a blank message violates @NotBlank")
    void blankMessageIsRejected() {
        Set<?> violations = validator.validate(new EchoRequest("   "));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().toString()).contains("message must not be blank");
    }

    @Test
    @DisplayName("a null message violates @NotBlank")
    void nullMessageIsRejected() {
        assertThat(validator.validate(new EchoRequest(null))).hasSize(1);
    }

    @Test
    @DisplayName("a message longer than 200 characters violates @Size")
    void tooLongMessageIsRejected() {
        Set<?> violations = validator.validate(new EchoRequest("x".repeat(201)));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().toString()).contains("must not exceed 200 characters");
    }
}
