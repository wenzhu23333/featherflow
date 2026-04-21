package com.ywz.workflow.featherflow.ops;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class OpsConsoleProfileConfigurationTest {

    @Test
    void shouldUseMysqlAsDefaultRuntimeProfile() {
        Properties properties = loadYaml("application.yml");

        assertThat(properties.getProperty("spring.profiles.default")).isEqualTo("mysql");
    }

    @Test
    void shouldUseH2OnlyForLocalDemoProfile() {
        Properties properties = loadYaml("application-h2.yml");

        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("org.h2.Driver");
        assertThat(properties.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("embedded");
        assertThat(properties.getProperty("spring.sql.init.schema-locations")).isEqualTo("classpath:schema.sql");
        assertThat(properties.getProperty("spring.sql.init.data-locations")).isEqualTo("classpath:demo-data.sql");
    }

    @Test
    void shouldUseMysqlForProductionProfileWithoutAutoInitializingSchema() {
        Properties properties = loadYaml("application-mysql.yml");

        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(properties.getProperty("spring.datasource.url")).contains("jdbc:mysql://");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    private Properties loadYaml(String location) {
        ClassPathResource resource = new ClassPathResource(location);
        assertThat(resource.exists()).as(location + " should exist").isTrue();

        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        Properties properties = factory.getObject();
        assertThat(properties).as(location + " should be readable").isNotNull();
        return properties;
    }
}
