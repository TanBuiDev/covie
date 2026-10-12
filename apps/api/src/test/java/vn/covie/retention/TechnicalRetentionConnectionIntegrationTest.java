package vn.covie.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import vn.covie.ops.OperationalMetrics;
import vn.covie.support.PostgresIntegrationTest;

class TechnicalRetentionConnectionIntegrationTest extends PostgresIntegrationTest {
    @Autowired DataSource datasource;

    @Test
    void skipsSweepWhenAnotherWorkerHoldsTheLock() throws Exception {
        try (Connection holder = datasource.getConnection()) {
            var lockJdbc = new JdbcTemplate(new SingleConnectionDataSource(holder, true));
            var metrics = mock(OperationalMetrics.class);
            assertThat(lockJdbc.queryForObject("select pg_try_advisory_lock(591000010)", Boolean.class)).isTrue();
            try {
                new TechnicalRetentionService(new JdbcTemplate(datasource),
                        new TechnicalRetentionProperties(true, 500, Duration.ofDays(7), Duration.ofDays(30)),
                        metrics).sweepOnce();
                verifyNoInteractions(metrics);
            } finally {
                lockJdbc.queryForObject("select pg_advisory_unlock(591000010)", Boolean.class);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sweepReleasesLockEvenWhenThePoolRotatesConnections(boolean failDuringSweep) throws Exception {
        try(Connection first=datasource.getConnection(); Connection second=datasource.getConnection();
                Connection observer=datasource.getConnection()) {
            var calls=new AtomicInteger();
            DataSource rotating=new AbstractDataSource() {
                @Override public Connection getConnection() {
                    Connection delegate=calls.getAndIncrement()%2==0 ? first : second;
                    return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),
                            new Class<?>[]{Connection.class},(proxy,method,args)-> {
                                if(method.getName().equals("close")) return null;
                                try { return method.invoke(delegate,args); }
                                catch(InvocationTargetException error) { throw error.getCause(); }
                            });
                }
                @Override public Connection getConnection(String user,String password) { return getConnection(); }
            };
            var metrics=mock(OperationalMetrics.class);
            if(failDuringSweep) doThrow(new IllegalStateException("Controlled sweep failure"))
                    .when(metrics).retentionSuccess();
            var service=new TechnicalRetentionService(new JdbcTemplate(rotating),
                    new TechnicalRetentionProperties(true,500,Duration.ofDays(7),Duration.ofDays(30)),
                    metrics);
            var check=new JdbcTemplate(new SingleConnectionDataSource(observer,true));
            try {
                if(failDuringSweep) assertThatThrownBy(service::sweepOnce)
                        .isInstanceOf(IllegalStateException.class).hasMessage("Controlled sweep failure");
                else service.sweepOnce();
                assertThat(check.queryForObject("select pg_try_advisory_lock(591000010)",Boolean.class))
                        .as("retention lock must be released on the connection that acquired it").isTrue();
            } finally {
                for(Connection connection:new Connection[]{first,second,observer}) {
                    try(var statement=connection.createStatement()) { statement.execute("select pg_advisory_unlock_all()"); }
                }
            }
        }
    }
}
