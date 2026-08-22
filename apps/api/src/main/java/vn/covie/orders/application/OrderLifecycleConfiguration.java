package vn.covie.orders.application;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OrderLifecycleProperties.class)
class OrderLifecycleConfiguration {}
