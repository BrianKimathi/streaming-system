package com.streamx.catalog.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** An optional artwork or trailer link: blank, or an http(s) URL of at most 2000 characters. */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Size(max = 2000)
@Pattern(regexp = "^\\s*(https?://\\S+)?\\s*$", flags = Pattern.Flag.CASE_INSENSITIVE)
public @interface MediaUrl {

    String message() default "Links must be http(s) URLs of at most 2000 characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
