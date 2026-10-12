package vn.covie.barcode.support;

public final class GtinValidator {

    private GtinValidator() {}

    public static boolean isValidGtin(String code) {
        if (code == null) {
            return false;
        }
        String trimmed = code.trim();
        int len = trimmed.length();
        if (len != 8 && len != 12 && len != 13 && len != 14) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return false;
            }
        }
        int weighted = 0;
        int weight = 3;
        for (int i = len - 2; i >= 0; i--) {
            int d = trimmed.charAt(i) - '0';
            weighted += d * weight;
            weight = (weight == 3) ? 1 : 3;
        }
        int checkDigit = (10 - (weighted % 10)) % 10;
        return checkDigit == (trimmed.charAt(len - 1) - '0');
    }

    public static String toGtin14(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.length() > 14) {
            return trimmed;
        }
        return "0".repeat(14 - trimmed.length()) + trimmed;
    }
}
