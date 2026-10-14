package com.hookscope.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@AutoConfigureMockMvc
@SpringBootTest(
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    })
class HealthEndpointTest {
    @Autowired
    MockMvc http;

    @Test
    void healthEndpointRespondsUp() throws Exception {
        var response = http.perform(get("/actuator/health")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"status\":\"UP\"");
    }
}
