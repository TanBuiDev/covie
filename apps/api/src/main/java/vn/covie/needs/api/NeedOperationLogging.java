package vn.covie.needs.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import vn.covie.needs.api.dto.CreateNeedResponseRequest;
import vn.covie.needs.api.dto.NeedDetailResponse;
import vn.covie.needs.api.dto.NeedResponseResponse;
import vn.covie.shared.web.ApiErrorResponse;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdFilter;

@ControllerAdvice
public final class NeedOperationLogging extends RequestBodyAdviceAdapter
        implements ResponseBodyAdvice<Object>, WebMvcConfigurer, HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeedOperationLogging.class);
    private static final String ATTRIBUTE = NeedOperationLogging.class.getName();

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).order(-100);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !needController(method.getBeanType())) return true;
        Metadata metadata = new Metadata();
        metadata.requestId = MDC.get(RequestIdFilter.MDC_KEY);
        metadata.operation = method.getBeanType().getSimpleName() + "." + method.getMethod().getName();
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() instanceof Jwt jwt) {
            metadata.userId = uuid(jwt.getSubject());
        }
        Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (variables instanceof Map<?, ?> paths) {
            metadata.merchantId = uuid(paths.get("merchantId"));
            metadata.needRequestId = uuid(paths.get("needRequestId"));
            metadata.responseId = uuid(paths.get("responseId"));
        }
        metadata.merchantLocationId = uuid(request.getParameter("locationId"));
        request.setAttribute(ATTRIBUTE, metadata);
        return true;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return needController(parameter.getContainingClass());
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter,
            Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getAttribute(ATTRIBUTE) instanceof Metadata metadata
                && body instanceof CreateNeedResponseRequest create) {
            metadata.merchantLocationId = create.merchantLocationId();
        }
        return body;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servlet)
                || !(servlet.getServletRequest().getAttribute(ATTRIBUTE) instanceof Metadata metadata)) return body;
        if (body instanceof NeedResponseResponse needResponse) {
            metadata.needRequestId = needResponse.needRequestId();
            metadata.responseId = needResponse.id();
            metadata.merchantId = needResponse.merchantId();
            metadata.merchantLocationId = needResponse.merchantLocationId();
        } else if (body instanceof NeedDetailResponse need) {
            metadata.needRequestId = need.id();
        } else if (body instanceof ApiErrorResponse error && error.error() != null) {
            metadata.errorCode = ErrorCode.valueOf(error.error().code()).name();
        }
        return body;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception failure) {
        if (!(request.getAttribute(ATTRIBUTE) instanceof Metadata metadata)) return;
        request.removeAttribute(ATTRIBUTE);
        boolean failed = failure != null || response.getStatus() >= 400;
        if (failed && metadata.errorCode == null) metadata.errorCode = ErrorCode.INTERNAL_ERROR.name();
        LOGGER.info("need_operation requestId={} userId={} merchantId={} merchantLocationId={} needRequestId={} responseId={} operation={} result={} errorCode={}",
                metadata.requestId, metadata.userId, metadata.merchantId, metadata.merchantLocationId,
                metadata.needRequestId, metadata.responseId, metadata.operation,
                failed ? "HTTP_ERROR" : "HTTP_SUCCESS", metadata.errorCode);
    }

    private static boolean needController(Class<?> controller) {
        return controller == MeNeedController.class || controller == NeedResponseController.class
                || controller == MerchantNeedDiscoveryController.class;
    }

    private static UUID uuid(Object value) {
        if (!(value instanceof String text)) return null;
        try { return UUID.fromString(text); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static final class Metadata {
        private String requestId;
        private UUID userId;
        private UUID merchantId;
        private UUID merchantLocationId;
        private UUID needRequestId;
        private UUID responseId;
        private String operation;
        private String errorCode;
    }
}
