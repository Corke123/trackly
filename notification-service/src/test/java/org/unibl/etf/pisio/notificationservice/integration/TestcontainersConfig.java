package org.unibl.etf.pisio.notificationservice.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.azure.ServiceBusEmulatorContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }

    @Bean
    Network serviceBusNetwork() {
        return Network.newNetwork();
    }

    @Bean
    @SuppressWarnings("resource")
    MSSQLServerContainer mssqlServerContainer(Network serviceBusNetwork) {
        return new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-CU14-ubuntu-22.04")
                .acceptLicense()
                .withPassword("yourStrong(!)Password")
                .withCreateContainerCmdModifier(cmd -> {
                    HostConfig hostConfig = cmd.getHostConfig();
                    if (hostConfig != null) {
                        hostConfig.withCapAdd(Capability.SYS_PTRACE);
                    }
                })
                .withNetwork(serviceBusNetwork);
    }

    @Bean
    @SuppressWarnings("resource")
    ServiceBusEmulatorContainer serviceBusEmulatorContainer(Network serviceBusNetwork,
                                                            MSSQLServerContainer mssqlServerContainer) {
        return new ServiceBusEmulatorContainer("mcr.microsoft.com/azure-messaging/servicebus-emulator:1.1.2")
                .acceptLicense()
                .withConfig(MountableFile.forClasspathResource("servicebus-config.json"))
                .withNetwork(serviceBusNetwork)
                .withMsSqlServerContainer(mssqlServerContainer);
    }

    @Bean
    DynamicPropertyRegistrar serviceBusProperties(ServiceBusEmulatorContainer serviceBusEmulatorContainer) {
        return registry ->
                registry.add("trackly.servicebus.connection-string", serviceBusEmulatorContainer::getConnectionString);
    }

    @Bean
    JwtDecoder jwtDecoder() {
        return _ -> {
            throw new UnsupportedOperationException(
                    "JwtDecoder should never be invoked: the security filter chain isn't applied to RestTestClient "
                            + "requests in these tests");
        };
    }

    @Bean
    RestTestClient restTestClient(WebApplicationContext context) {
        return RestTestClient.bindToApplicationContext(context)
                .configureServer(builder -> builder.defaultRequest(
                        MockMvcRequestBuilders.get("/")
                                .with(jwt()
                                        .jwt(token -> token.subject("admin").claim("roles", List.of("ROLE_ADMIN")))
                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))))
                .build();
    }
}
