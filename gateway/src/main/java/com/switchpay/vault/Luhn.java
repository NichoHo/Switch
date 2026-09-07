package com.switchpay.vault;

public final class Luhn {
    
    private Luhn() {
        // Utility class
    }

    public static boolean isValid(String panStr) {
        if (panStr == null || !panStr.matches("\\d+")) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        
        for (int i = panStr.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(panStr.substring(i, i + 1));
            
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            
            sum += n;
            alternate = !alternate;
        }
        
        return (sum % 10 == 0);
    }

    public static boolean isValid(Pan pan) {
        return isValid(pan.getValue());
    }
}
