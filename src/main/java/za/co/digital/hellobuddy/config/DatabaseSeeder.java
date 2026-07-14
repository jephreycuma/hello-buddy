package za.co.digital.hellobuddy.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import za.co.digital.hellobuddy.model.Agent;
import za.co.digital.hellobuddy.repository.AgentRepository;

@Configuration
public class DatabaseSeeder {

    @Bean
    CommandLineRunner initDatabase(AgentRepository agentRepository) {
        return args -> {
            // Check if the agent already exists to prevent duplicate inserts on server restart
            if (agentRepository.findByUsername("agent_admin").isEmpty()) {
                Agent defaultAgent = new Agent();
                defaultAgent.setUsername("agent_admin");
                defaultAgent.setPassword("BuddyPass123"); // Note: Use BCrypt hashing for production!
                defaultAgent.setFullName("Jephrey Augustin");
                
                agentRepository.save(defaultAgent);
                System.out.println(">>> Database Seeded: Default support agent successfully created!");
            }
        };
    }
}