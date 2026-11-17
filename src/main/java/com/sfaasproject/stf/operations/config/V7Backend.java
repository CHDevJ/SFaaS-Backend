package com.sfaasproject.stf.operations.config;

import org.springframework.boot.autoconfigure.condition.*;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ConditionalOnProperty(name = "stf.backend-b.enabled", havingValue = "true")
@ConditionalOnExpression("!${stf.backend-b.legacy-v5-enabled:false}")
public @interface V7Backend {}
