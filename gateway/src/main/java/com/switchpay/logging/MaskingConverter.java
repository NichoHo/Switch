package com.switchpay.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskingConverter extends MessageConverter {

    // Matches any sequence of 13 to 19 digits (with optional spaces/dashes that we ignore, 
    // but the simplest is just matching the raw digits if they are printed).
    // PANs in logs are typically logged raw or accidentally dumped.
    private static final Pattern PAN_PATTERN = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");
    
    // Also redact Authorization and API key headers if accidentally logged.
    // NR-7: "Authorization: Bearer <token>" has a scheme word between the separator and the
    // actual secret: without consuming it, the capture group greedily grabs "Bearer" itself
    // and leaves the real token sitting in the log line untouched. The optional non-capturing
    // group eats a known scheme word first, so group 2 is always the credential, not the scheme.
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "(?i)(api[-_]?key|authorization|cvv|secret)[\\s\"':=]+(?:(?:Bearer|Basic|Token)\\s+)?([^\\s\"',}]+)");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null) {
            return null;
        }

        Matcher panMatcher = PAN_PATTERN.matcher(message);
        if (panMatcher.find()) {
            message = panMatcher.replaceAll("Pan[****]");
        }

        Matcher headerMatcher = HEADER_PATTERN.matcher(message);
        if (headerMatcher.find()) {
            message = headerMatcher.replaceAll("$1=***");
        }

        return message;
    }
}
