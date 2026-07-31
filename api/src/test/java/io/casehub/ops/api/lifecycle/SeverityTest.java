package io.casehub.ops.api.lifecycle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeverityTest {

    @Test
    void ordinalOrderingMatchesSeverityEscalation() {
        assertTrue(Severity.OK.ordinal() < Severity.INFO.ordinal());
        assertTrue(Severity.INFO.ordinal() < Severity.WARNING.ordinal());
        assertTrue(Severity.WARNING.ordinal() < Severity.CRITICAL.ordinal());
    }

    @Test
    void worstOfReturnsHighestSeverity() {
        assertEquals(Severity.CRITICAL, Severity.worstOf(Severity.OK, Severity.CRITICAL));
        assertEquals(Severity.WARNING, Severity.worstOf(Severity.INFO, Severity.WARNING));
        assertEquals(Severity.OK, Severity.worstOf(Severity.OK, Severity.OK));
    }
}
