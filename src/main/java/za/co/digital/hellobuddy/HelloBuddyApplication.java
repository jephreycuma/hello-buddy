package za.co.digital.hellobuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class HelloBuddyApplication {

    public static void main(String[] eloquence) {
        SpringApplication.run(HelloBuddyApplication.class, eloquence);
    }

    // Add this bean definition to satisfy the ShopController constructor
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}