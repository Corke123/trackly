package org.unibl.etf.pisio.notificationservice.integration;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.client.RestTestClient;
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
    ServiceBusEmulatorContainer serviceBusEmulatorContainer(Network serviceBusNetwork, MSSQLServerContainer mssqlServerContainer) {
        return new ServiceBusEmulatorContainer("mcr.microsoft.com/azure-messaging/servicebus-emulator:1.1.2")
                .acceptLicense()
                .withConfig(MountableFile.forClasspathResource("servicebus-config.json"))
                .withNetwork(serviceBusNetwork)
                .withMsSqlServerContainer(mssqlServerContainer);
    }

    @Bean
    DynamicPropertyRegistrar serviceBusProperties(ServiceBusEmulatorContainer serviceBusEmulatorContainer) {
        return registry -> registry.add("trackly.servicebus.connection-string", serviceBusEmulatorContainer::getConnectionString);
    }

    @Bean
    RestTestClient restTestClient(WebApplicationContext context) {
        return RestTestClient.bindToApplicationContext(context).build();
    }
}
