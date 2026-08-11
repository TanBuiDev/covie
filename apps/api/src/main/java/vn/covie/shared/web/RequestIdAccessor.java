package vn.covie.shared.web;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public final class RequestIdAccessor {
    public String current() { return MDC.get(RequestIdFilter.MDC_KEY); }
}
