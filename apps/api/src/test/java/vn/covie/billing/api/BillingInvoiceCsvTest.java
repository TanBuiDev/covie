package vn.covie.billing.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.billing.application.AdminBillingReadService;

class BillingInvoiceCsvTest {
    private AdminBillingReadService.Invoice invoice(String number, Instant paidAt) {
        return new AdminBillingReadService.Invoice(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"), number, "PAID", 10000,
            Instant.parse("2026-10-18T00:00:00Z"), paidAt, List.of("RECONCILE"));
    }

    @Test void emptyPageContainsOnlyPublicColumnHeader() {
        assertThat(BillingInvoiceCsv.render(List.of())).isEqualTo(
            "invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc\r\n");
    }

    @Test void invoiceFieldsEscapeCommasQuotesAndLineBreaksWithoutExportingActions() {
        String csv = BillingInvoiceCsv.render(List.of(invoice("CB,\"quoted\"\r\ncontinuation", null)));
        assertThat(csv).contains("\"CB,\"\"quoted\"\"\r\ncontinuation\",\"PAID\",\"10000\",\"2026-10-18T00:00:00Z\",\"\"\r\n")
            .doesNotContain("RECONCILE", "allowedActions");
    }

    @Test void spreadsheetFormulaAndControlPrefixesRemainQuotedInertText() {
        for (String number : List.of("=1+1", "+1+1", "-1+1", "@SUM(1,1)", "  =1+1", "\ttext", "\rtext", "\ntext")) {
            assertThat(BillingInvoiceCsv.render(List.of(invoice(number, Instant.parse("2026-10-18T01:00:00Z")))))
                .contains(",\"'" + number + "\",\"PAID\"")
                .endsWith("\"2026-10-18T01:00:00Z\"\r\n");
        }
    }
}
