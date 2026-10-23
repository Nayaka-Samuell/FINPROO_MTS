package com.ecoloop

import com.ecoloop.services.ApiException
import com.ecoloop.services.city
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidationTest {
    @Test fun `city accepts Jakarta aliases case insensitively and trims whitespace`() {
        listOf("DKI Jakarta", "Jakarta", "dki jakarta", "JAKARTA", "  JaKaRtA  ", "  DKI JAKARTA  ").forEach { value ->
            assertEquals(value.trim(), city(value))
        }
    }

    @Test fun `city rejects other locations and empty input`() {
        listOf("Malang", "Jakarta Selatan", "", "   ").forEach { value ->
            assertEquals("VALIDATION_ERROR", assertFailsWith<ApiException> { city(value) }.code)
        }
    }
}
