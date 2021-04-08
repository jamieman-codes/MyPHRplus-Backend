package com.jws1g18.myphrplus;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    // Deployment
    private static final String corsGAE = "https://myphrplus-frontend.nw.r.appspot.com";

    // Local
    private static final String corsLocal = "http://localhost:8080";

    @Autowired
    FirebaseTokenFilter fireBaseTokenFilter;

    @Override
    protected void configure(HttpSecurity security) throws Exception {
        security.cors().and().csrf().disable().httpBasic().disable()
        .authorizeRequests().antMatchers("/registerPatient", "/getAllDPs").permitAll() //These endpoints don't need authentication 
        .anyRequest().fullyAuthenticated().and().addFilterBefore(fireBaseTokenFilter, BasicAuthenticationFilter.class) //Add authentication filter to all other endpoints 
        .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(corsGAE, corsLocal));
        configuration.setAllowedMethods(Arrays.asList("POST", "GET"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
