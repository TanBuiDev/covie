package vn.covie.ads;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AdRulesTest {
    @Test void refusesExecutableAndProtocolRelativeTargets() {
        assertThrows(RuntimeException.class, () -> AdRules.validateTarget("javascript:alert(1)"));
        assertThrows(RuntimeException.class, () -> AdRules.validateTarget("//evil.test"));
        assertThrows(RuntimeException.class, () -> AdRules.validateTarget("https://user:pass@evil.test"));
        assertDoesNotThrow(() -> AdRules.validateTarget("/items/123"));
        assertDoesNotThrow(() -> AdRules.validateTarget("https://example.com/product"));
    }
    @Test void impressionRequiresOneContinuousSecond() {
        assertFalse(AdRules.acceptsEvent("IMPRESSION", 999));
        assertTrue(AdRules.acceptsEvent("IMPRESSION", 1000));
        assertTrue(AdRules.acceptsEvent("CLICK", 0));
        assertFalse(AdRules.acceptsEvent("OTHER", 1000));
    }
}
