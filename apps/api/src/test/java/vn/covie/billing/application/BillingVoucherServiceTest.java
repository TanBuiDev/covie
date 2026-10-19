package vn.covie.billing.application;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BillingVoucherServiceTest {
    @Test void fixedAndPercentageDiscountsKeepNonzeroPaymentDue(){
        assertEquals(2000,BillingVoucherService.discountAmount(10000,"FIXED",2000));
        assertEquals(1000,BillingVoucherService.discountAmount(10000,"PERCENT",1000));
        assertEquals(9999,BillingVoucherService.discountAmount(10000,"PERCENT",9999));
        assertThrows(RuntimeException.class,()->BillingVoucherService.discountAmount(10000,"FIXED",10000));
        assertThrows(RuntimeException.class,()->BillingVoucherService.discountAmount(10000,"FIXED",0));
        assertThrows(RuntimeException.class,()->BillingVoucherService.discountAmount(10000,"PERCENT",10000));
    }
    @Test void codesNormalizeAndRejectMalformedValues(){
        assertEquals("SELLER_10",BillingVoucherService.normalize(" seller_10 "));
        assertNull(BillingVoucherService.normalize("  "));
        assertThrows(RuntimeException.class,()->BillingVoucherService.normalize("invalid code"));
    }
}
