package vn.covie.performance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class HotQueryPlanValidationTest {
    @Test void refusesJsonWithoutAnExplainPlan() {
        assertThatThrownBy(()->HotQueryPlanIntegrationTest.assertHealthyPlan("[]"))
                .isInstanceOf(AssertionError.class);
    }
    @Test void rejectsPrettyPrintedOutboxSequentialScan() {
        assertThatThrownBy(()->HotQueryPlanIntegrationTest.assertHealthyPlan("""
                [{"Plan": {"Node Type": "Seq Scan", "Relation Name": "outbox_events"}}]
                """)).isInstanceOf(AssertionError.class);
    }
    @Test void rejectsNestedNotificationScanRegardlessOfFieldOrder() {
        assertThatThrownBy(()->HotQueryPlanIntegrationTest.assertHealthyPlan("""
                [{"Plan":{"Node Type":"Limit","Plans":[
                  {"Relation Name":"notifications","Alias":"n","Node Type":"Seq Scan"}]}}]
                """)).isInstanceOf(AssertionError.class);
    }
    @Test void acceptsIndexedPlansAndUnrelatedSmallRelationScan() {
        assertThatCode(()->HotQueryPlanIntegrationTest.assertHealthyPlan("""
                [{"Plan":{"Node Type":"Nested Loop","Plans":[
                  {"Node Type":"Index Scan","Relation Name":"notifications"},
                  {"Node Type":"Seq Scan","Relation Name":"categories"}]}}]
                """)).doesNotThrowAnyException();
    }
}
