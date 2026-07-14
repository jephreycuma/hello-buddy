package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpSession;
import za.co.digital.hellobuddy.dto.ChatMessage;
import za.co.digital.hellobuddy.model.Agent;
import za.co.digital.hellobuddy.repository.AgentRepository;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
public class LiveChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final AgentRepository agentRepository;

    public LiveChatController(SimpMessagingTemplate messagingTemplate, AgentRepository agentRepository) {
        this.messagingTemplate = messagingTemplate;
        this.agentRepository = agentRepository;
    }

    // 1. Render Login Screen
    @GetMapping("/agent/login")
    public String showLoginPage() {
        return "agent-login"; 
    }

    // 2. Process Login Credentials
    @PostMapping("/agent/login")
    public String processLogin(@RequestParam String username, @RequestParam String password, HttpSession session, Model model) {
        Optional<Agent> agentOpt = agentRepository.findByUsername(username);
        
        if (agentOpt.isPresent() && agentOpt.get().getPassword().equals(password)) { // Plain text validation for simplicity
            session.setAttribute("loggedInAgent", agentOpt.get());
            return "redirect:/agent/workspace";
        }
        
        model.addAttribute("error", "Invalid username or password credentials.");
        return "agent-login";
    }

    // 3. Render Dashboard Protected Route
    @GetMapping("/agent/workspace")
    public String showWorkspace(HttpSession session, Model model) {
        Agent agent = (Agent) session.getAttribute("loggedInAgent");
        if (agent == null) {
            return "redirect:/agent/login"; // Kick back to login if unauthenticated
        }
        model.addAttribute("agent", agent);
        return "agent-workspace";
    }

    // 4. Handle incoming payload distributions
    @MessageMapping("/chat.send")
    public void processMessage(@Payload ChatMessage message) {
        if (message.getTimestamp() == 0) {
            message.setTimestamp(System.currentTimeMillis());
        }

        // Send to the private customer loop
        messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), message);

        // Routing logic: route to target channel
        if ("user".equals(message.getSender())) {
            // Mock dynamic assignment logic: If a specific agent handles a thread, route it to them
            // In a live system, look up thread assignments from your DB here
            String assignedAgentId = "1"; // e.g., assuming thread is linked to Agent ID 1
            messagingTemplate.convertAndSend("/topic/agent-" + assignedAgentId, message);
        }
    }
}