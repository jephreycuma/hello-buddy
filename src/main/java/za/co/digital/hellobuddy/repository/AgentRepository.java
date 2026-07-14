package za.co.digital.hellobuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.digital.hellobuddy.model.Agent;

import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
	Optional<Agent> findByUsername(String username);
}