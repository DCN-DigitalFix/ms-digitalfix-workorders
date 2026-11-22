package cl.duoc.ms_digitalfix_workorders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate(new JdkClientHttpRequestFactory());

        // Workorders no tiene identidad propia: actua en nombre de quien
        // llamo. El Supervisor que asigna la orden es quien ve a catalog, no
        // un usuario de servicio. Sin esto catalog responde 401 y la
        // asignacion falla sin motivo visible.
        restTemplate.getInterceptors().add((request, body, execution) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtAuth.getToken().getTokenValue());
            }
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}
