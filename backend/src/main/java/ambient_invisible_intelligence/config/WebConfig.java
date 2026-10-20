package ambient_invisible_intelligence.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Allows the Spot&Slot clients to call the API from the browser / WebView:
// - the desktop admin app (Vite dev :8090, previews :4173/:5173)
// - the mobile app served in web dev (:8080)
// - the native Android app (Capacitor WebView origins)
// - phones/dev machines on the local network during demos
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOriginPatterns(
						"http://localhost:[*]",
						"http://127.0.0.1:[*]",
						// Capacitor Android WebView
						"https://localhost",
						"capacitor://localhost",
						// LAN dev/demo (phone or another machine on the network)
						"http://192.168.*.*:[*]",
						"http://10.*.*.*:[*]")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(false);
	}
}
