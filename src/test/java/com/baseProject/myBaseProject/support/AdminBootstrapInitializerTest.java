package com.baseProject.myBaseProject.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AdminBootstrapInitializerTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AdminBootstrapService.class, () -> mock(AdminBootstrapService.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void isNotRegisteredWhenBootstrapIsDisabled() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(AdminBootstrapInitializer.class));
    }

    @Test
    void isRegisteredWhenBootstrapIsEnabled() {
        contextRunner
                .withPropertyValues("app.admin-bootstrap.enabled=true")
                .run(context ->
                        assertThat(context).hasSingleBean(AdminBootstrapInitializer.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(AdminBootstrapInitializer.class)
    static class TestConfiguration {
    }
}
