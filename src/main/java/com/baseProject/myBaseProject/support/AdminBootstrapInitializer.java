package com.baseProject.myBaseProject.support;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.admin-bootstrap",
        name = "enabled",
        havingValue = "true")
public class AdminBootstrapInitializer implements ApplicationRunner {
    private final AdminBootstrapService service;

    @Override
    public void run(ApplicationArguments args) {
        service.bootstrap();
    }
}
