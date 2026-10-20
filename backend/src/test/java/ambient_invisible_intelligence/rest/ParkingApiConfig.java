package ambient_invisible_intelligence.rest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ApiVersionInserter;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.HttpServiceGroup.ClientType;
import org.springframework.web.service.registry.ImportHttpServices;

// Registers the declarative HTTP clients as a "parking" group and points them at the
// running server, inserting the API version as a header (mirrors the course example).
@Configuration
@ImportHttpServices(
		group = "parking",
		clientType = ClientType.REST_CLIENT,
		types = { ObjectsApi.class, CommandsApi.class, UsersApi.class, AdminApi.class })
public class ParkingApiConfig {

	private String apiHeader;
	private String defaultVersion;
	private String baseUrl;

	@Value("${spring.mvc.apiversion.use.header:API-Version}")
	public void setApiHeader(String apiHeader) {
		this.apiHeader = apiHeader;
	}

	@Value("${spring.mvc.apiversion.default:1.3}")
	public void setDefaultVersion(String defaultVersion) {
		this.defaultVersion = defaultVersion;
	}

	@Value("${server.port:8084}")
	public void setPort(int port) {
		this.baseUrl = "http://localhost:" + port + "/ambient-invisible-intelligence";
	}

	@Bean
	public RestClientHttpServiceGroupConfigurer parkingGroupConfigurer() {
		return groups -> groups
				.filterByName("parking")
				.forEachClient((_, clientBuilder) -> clientBuilder
						.baseUrl(this.baseUrl)
						.apiVersionInserter(ApiVersionInserter.useHeader(this.apiHeader))
						.defaultApiVersion(this.defaultVersion)
						.build());
	}
}
