package za.co.digital.hellobuddy.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import za.co.digital.hellobuddy.model.Agent;
import za.co.digital.hellobuddy.repository.AgentRepository;

@Configuration
@Service
public class DatabaseSeeder {

    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;

    public DatabaseSeeder(AgentRepository agentRepository, PasswordEncoder passwordEncoder) {
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    CommandLineRunner initDatabase() {
        return args -> {
            if (agentRepository.findByUsername("agent_admin").isEmpty()) {
                Agent defaultAgent = new Agent();
                defaultAgent.setUsername("agent_admin");
                defaultAgent.setPassword(passwordEncoder.encode("BuddyPass123"));
                defaultAgent.setFullName("Jephrey Augustin");
                
                agentRepository.save(defaultAgent);
                System.out.println(">>> Database Seeded: Default support agent successfully created!");
            }
        };
    }

    /**
     * Helper method called by Controller to save new registered agents.
     */
    public Agent registerNewAgent(String fullName, String username, String rawPassword) {
        if (agentRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists.");
        }

        Agent agent = new Agent();
        agent.setFullName(fullName);
        agent.setUsername(username);
        agent.setPassword(passwordEncoder.encode(rawPassword));

        return agentRepository.save(agent);
    }
}