package vn.covie.billing.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestSqlCounter {
    public static final String HEADER = "X-Billing-Fixture-Sql-Count";
    private static final ThreadLocal<AtomicInteger> CURRENT = new ThreadLocal<>();
    private static final ConcurrentHashMap<String, Integer> RESULTS = new ConcurrentHashMap<>();

    private RequestSqlCounter() {}

    public static int take(String requestId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (true) {
            Integer count = RESULTS.remove(requestId);
            if (count != null) return count;
            if (System.nanoTime() >= deadline) throw new AssertionError("Measured request did not finish its SQL counter.");
            Thread.sleep(5);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {
        @Bean static BeanPostProcessor countingDataSource() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof DataSource source) || !name.equals("dataSource")) return bean;
                    return new DelegatingDataSource(source) {
                        @Override public Connection getConnection() throws SQLException {
                            return connection(super.getConnection());
                        }
                        @Override public Connection getConnection(String username, String password) throws SQLException {
                            return connection(super.getConnection(username, password));
                        }
                    };
                }
            };
        }

        @Bean CountingFilter countingRequestFilter() { return new CountingFilter(); }
    }

    private static Connection connection(Connection delegate) {
        return (Connection) Proxy.newProxyInstance(RequestSqlCounter.class.getClassLoader(),
            new Class<?>[]{Connection.class}, (proxy, method, arguments) -> {
                Object result = invoke(proxy, delegate, method, arguments);
                if (result instanceof Statement statement && (method.getName().equals("createStatement")
                        || method.getName().equals("prepareStatement") || method.getName().equals("prepareCall"))) {
                    Class<?> contract = statement instanceof CallableStatement ? CallableStatement.class
                        : statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                    return Proxy.newProxyInstance(RequestSqlCounter.class.getClassLoader(), new Class<?>[]{contract},
                        (statementProxy, operation, parameters) -> {
                            AtomicInteger counter = CURRENT.get();
                            if (counter != null && operation.getName().startsWith("execute")) counter.incrementAndGet();
                            return invoke(statementProxy, statement, operation, parameters);
                        });
                }
                return result;
            });
    }

    private static Object invoke(Object proxy, Object delegate, Method method, Object[] arguments) throws Throwable {
        if (method.getName().equals("equals") && method.getDeclaringClass() == Object.class) return proxy == arguments[0];
        if (method.getName().equals("hashCode") && method.getDeclaringClass() == Object.class) return System.identityHashCode(proxy);
        try { return method.invoke(delegate, arguments); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    static final class CountingFilter extends OncePerRequestFilter implements Ordered {
        @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            String requestId = request.getHeader(HEADER);
            if (requestId == null) { chain.doFilter(request, response); return; }
            AtomicInteger counter = new AtomicInteger();
            CURRENT.set(counter);
            try { chain.doFilter(request, response); }
            finally { CURRENT.remove(); RESULTS.put(requestId, counter.get()); }
        }
    }
}
