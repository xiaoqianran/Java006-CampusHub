package com.shiqian.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedRateLimit {

    String name();

    int limit();

    int windowSeconds();

    RateLimitKeyMode keyMode() default RateLimitKeyMode.USER_OR_IP;
}
