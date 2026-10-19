package vn.covie.billing.api;

import java.util.List;
import vn.covie.billing.application.AdminBillingReadService;

final class BillingInvoiceCsv {
    private BillingInvoiceCsv() {}

    static String render(List<AdminBillingReadService.Invoice> invoices) {
        StringBuilder csv = new StringBuilder("invoiceId,merchantId,invoiceNumber,status,amountVnd,createdAtUtc,paidAtUtc\r\n");
        for (var invoice : invoices) {
            String[] fields = {invoice.id().toString(), invoice.merchantId().toString(),
                invoice.invoiceNumber(), invoice.status(), Long.toString(invoice.amountVnd()),
                invoice.createdAt().toString(), invoice.paidAt() == null ? "" : invoice.paidAt().toString()};
            csv.append(java.util.Arrays.stream(fields).map(BillingInvoiceCsv::cell)
                .collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }
        return csv.toString();
    }

    private static String cell(String value) {
        String stripped = value.stripLeading();
        boolean formula = !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0;
        boolean control = !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0;
        String safe = formula || control ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
