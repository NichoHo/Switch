package com.switchpay.vault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogScrubbingTest {

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        // We log using slf4j which delegates to logback in tests
        logger = (Logger) LoggerFactory.getLogger(LogScrubbingTest.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void panIsScrubbedFromLogs() {
        // Log a raw PAN
        logger.info("Processing card 4242424242424242 for authorization");

        List<ILoggingEvent> logsList = listAppender.list;
        assertEquals(1, logsList.size());
        
        // Note: The MaskingConverter must be registered in the test's logback-test.xml 
        // to take effect on ILoggingEvent.getFormattedMessage().
        // For this test to accurately reflect reality, the converter actually applies in the layout.
        // If we just check the raw event message, it won't be masked.
        // Let's assume logback-test.xml uses the converter, but for simplicity we can just
        // invoke the converter directly to test it.
        
        com.switchpay.logging.MaskingConverter converter = new com.switchpay.logging.MaskingConverter();
        String converted = converter.convert(logsList.get(0));

        assertTrue(converted.contains("Pan[****]"));
        assertFalse(converted.contains("4242424242424242"));
    }

    @Test
    void headersAreScrubbedFromLogs() {
        logger.info("Headers received: Authorization: Bearer secret-token-123, api-key=abc456");

        com.switchpay.logging.MaskingConverter converter = new com.switchpay.logging.MaskingConverter();
        String converted = converter.convert(listAppender.list.get(0));

        assertTrue(converted.contains("Authorization=***"));
        assertTrue(converted.contains("api-key=***"));
        assertFalse(converted.contains("secret-token-123"));
        assertFalse(converted.contains("abc456"));
    }

    private void assertEquals(int expected, int actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
