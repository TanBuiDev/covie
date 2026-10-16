package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingIndependentRefundApprovalTest {
    private static final UUID SELLER=UUID.randomUUID(), REVIEWER=UUID.randomUUID(), APPROVER=UUID.randomUUID();
    @Test void onlyAnIndependentApproverMayConfirmAReviewedRefund(){
        assertDoesNotThrow(()->BillingFinanceCaseService.requireIndependentRefundApproval(
            "REVIEWING",APPROVER,SELLER,REVIEWER));
        assertThrows(RuntimeException.class,()->BillingFinanceCaseService.requireIndependentRefundApproval(
            "REQUESTED",APPROVER,SELLER,REVIEWER));
        assertThrows(RuntimeException.class,()->BillingFinanceCaseService.requireIndependentRefundApproval(
            "REVIEWING",REVIEWER,SELLER,REVIEWER));
        assertThrows(RuntimeException.class,()->BillingFinanceCaseService.requireIndependentRefundApproval(
            "REVIEWING",SELLER,SELLER,REVIEWER));
        assertThrows(RuntimeException.class,()->BillingFinanceCaseService.requireIndependentRefundApproval(
            "REVIEWING",APPROVER,SELLER,null));
        assertThrows(RuntimeException.class,()->BillingFinanceCaseService.requireIndependentRefundApproval(
            "REVIEWING",APPROVER,SELLER,SELLER));
    }
}
