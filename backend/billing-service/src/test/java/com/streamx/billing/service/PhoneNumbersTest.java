package com.streamx.billing.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumbersTest {

    @ParameterizedTest
    @CsvSource({
            "0712345678, 254712345678",
            "0112345678, 254112345678",
            "+254712345678, 254712345678",
            "254712345678, 254712345678",
            "+254112345678, 254112345678",
            "'0712 345 678', 254712345678",
            "'+254-712-345-678', 254712345678",
            "' (0712) 345678 ', 254712345678"
    })
    void normalizesAcceptedFormats(String input, String expected) {
        assertEquals(expected, PhoneNumbers.normalize(input).orElseThrow());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0812345678", "071234567", "07123456789", "254812345678", "+1712345678",
            "712345678", "07l2345678", "2547123456789", "abc"})
    void rejectsInvalidNumbers(String input) {
        assertTrue(PhoneNumbers.normalize(input).isEmpty(), () -> "should reject " + input);
    }

    @Test
    void masksMiddleDigits() {
        assertEquals("2547****5678", PhoneNumbers.mask("254712345678"));
        assertEquals("2541****5678", PhoneNumbers.mask("254112345678"));
        assertEquals(null, PhoneNumbers.mask(null));
    }
}
