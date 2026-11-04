package com.yusufnazim.deliverydispatch.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

final class RequestLogCapture implements AutoCloseable {

    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
        @Override
        protected void append(ILoggingEvent event) {
            entries.add(new Entry(event.getFormattedMessage(), Map.copyOf(event.getMDCPropertyMap()),
                    event.getThrowableProxy() != null));
        }
    };

    RequestLogCapture() {
        appender.start();
        logger.addAppender(appender);
    }

    List<Entry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }

    record Entry(String message, Map<String, String> context, boolean hasThrowable) {
    }
}
