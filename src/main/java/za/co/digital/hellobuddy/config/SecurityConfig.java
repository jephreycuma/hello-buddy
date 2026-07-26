package za.co.digital.hellobuddy.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/wallet/**"))
            .headers(headers -> headers
                .cacheControl(cache -> cache.disable())
                .addHeaderWriter((request, response) -> {
                    response.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
                    response.setHeader("Pragma", "no-cache");
                    response.setHeader("Expires", "0");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers(
                		"/", 
                	    "/index",
                	    "/error",
                	    "/success",
                	    "/wallet/**", 
                	    "/vouchers/**",  
                	    "/api/paystack/**",
                	    "/api/**",          
                	    "/images/**", 
                	    "/images.png", // <--- ADD THIS
                	    "/favicon.ico",
                	    "/css/**", 
                	    "/js/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .logout(logout -> logout
                // Explicitly intercept GET (and POST) requests to /wallet/logout
                .logoutUrl("/wallet/logout")
                .logoutRequestMatcher(request -> 
                    "/wallet/logout".equals(request.getServletPath())
                )
                .logoutSuccessUrl("/?logoutSuccess=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}