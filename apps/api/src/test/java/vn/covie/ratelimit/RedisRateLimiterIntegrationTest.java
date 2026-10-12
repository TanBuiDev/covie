package vn.covie.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class RedisRateLimiterIntegrationTest {
    @Test void concurrentRequestsConsumeExactlyTheCapacityAndKeepBucketsIndependent() throws Exception {
        try(var redis=new GenericContainer<>(DockerImageName.parse("redis:8-alpine")).withExposedPorts(6379)) {
            redis.start();
            var connections=new LettuceConnectionFactory(redis.getHost(),redis.getMappedPort(6379));
            connections.afterPropertiesSet(); connections.start();
            try {
                var template=new StringRedisTemplate(connections);
                var policy=new RateLimitPolicy(5,5,Duration.ofHours(1),true);
                var limiter=new RedisTokenBucketRateLimiter(template,new RateLimitProperties(true,"test-only",Map.of(
                        RateLimitGroup.AUTH_LOGIN_IP,policy,RateLimitGroup.AUTH_LOGIN_EMAIL,policy,
                        RateLimitGroup.PUBLIC_DISCOVERY,new RateLimitPolicy(5,5,Duration.ofHours(1),false))));
                var start=new java.util.concurrent.CountDownLatch(1);
                var accepted=new java.util.concurrent.atomic.AtomicInteger();
                var denied=new java.util.concurrent.atomic.AtomicInteger();
                try(var workers=java.util.concurrent.Executors.newFixedThreadPool(20)) {
                    var jobs=new java.util.ArrayList<java.util.concurrent.Future<?>>();
                    for(int index=0;index<20;index++) jobs.add(workers.submit(()->{
                        start.await();
                        try { limiter.require(RateLimitGroup.AUTH_LOGIN_IP,"shared"); accepted.incrementAndGet(); }
                        catch(RateLimitExceededException expected) { denied.incrementAndGet(); }
                        return null;
                    }));
                    start.countDown();
                    for(var job:jobs) job.get(10,java.util.concurrent.TimeUnit.SECONDS);
                }
                assertThat(accepted.get()).isEqualTo(5);
                assertThat(denied.get()).isEqualTo(15);
                assertThatCodeAllowed(limiter,RateLimitGroup.AUTH_LOGIN_IP,"another");
                assertThatCodeAllowed(limiter,RateLimitGroup.AUTH_LOGIN_EMAIL,"shared");
                assertThat(template.getExpire("covie:rl:AUTH_LOGIN_IP:shared")).isPositive();
                template.opsForHash().put("covie:rl:AUTH_LOGIN_IP:shared","last_refill_ms",Long.toString(System.currentTimeMillis()-Duration.ofHours(2).toMillis()));
                for(int index=0;index<5;index++) assertThat(limiter.require(RateLimitGroup.AUTH_LOGIN_IP,"shared").remaining()).isEqualTo(4-index);
                org.assertj.core.api.Assertions.assertThatThrownBy(()->limiter.require(RateLimitGroup.AUTH_LOGIN_IP,"shared"))
                        .isInstanceOf(RateLimitExceededException.class);
                // Actual Redis WRONGTYPE makes Lua fail without mocking its transport or decision.
                template.opsForValue().set("covie:rl:AUTH_LOGIN_IP:broken","wrong-type");
                org.assertj.core.api.Assertions.assertThatThrownBy(()->limiter.require(RateLimitGroup.AUTH_LOGIN_IP,"broken"))
                        .isInstanceOf(RateLimiterUnavailableException.class);
                template.opsForValue().set("covie:rl:PUBLIC_DISCOVERY:broken","wrong-type");
                var degraded=limiter.require(RateLimitGroup.PUBLIC_DISCOVERY,"broken");
                assertThat(degraded.allowed()).isTrue();
                assertThat(degraded.degraded()).isTrue();
                assertThat(degraded.remaining()).isEqualTo(-1);
            } finally { connections.destroy(); }
        }
    }
    private void assertThatCodeAllowed(RedisTokenBucketRateLimiter limiter,RateLimitGroup group,String key) {
        org.assertj.core.api.Assertions.assertThatCode(()->limiter.require(group,key)).doesNotThrowAnyException();
    }
}
