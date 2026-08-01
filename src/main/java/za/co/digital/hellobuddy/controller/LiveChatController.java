package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpSession;
import za.co.digital.hellobuddy.model.Agent;
import za.co.digital.hellobuddy.model.ChatMessage;
import za.co.digital.hellobuddy.repository.AgentRepository;
import za.co.digital.hellobuddy.repository.ChatMessageRepository;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Optional;

@Controller
public class LiveChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository messageRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder; // Injected PasswordEncoder

    public LiveChatController(SimpMessagingTemplate messagingTemplate, 
                               ChatMessageRepository messageRepository, 
                               AgentRepository agentRepository,
                               PasswordEncoder passwordEncoder) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==========================================
    // 1. AGENT ROUTING & AUTHENTICATION
    // ==========================================

    @GetMapping("/agent/login")
    public String showLoginPage() {
        return "agent-login"; // Renders templates/agent-login.html
    }

    @PostMapping("/agent/login")
    public String processLogin(@RequestParam String username, 
                               @RequestParam String password, 
                               HttpSession session, 
                               Model model) {
        Optional<Agent> agentOpt = agentRepository.findByUsername(username);
        
        // Use passwordEncoder.matches() instead of .equals()
        if (agentOpt.isPresent() && passwordEncoder.matches(password, agentOpt.get().getPassword())) {
            session.setAttribute("loggedInAgent", agentOpt.get());
            return "redirect:/agent/workspace";
        }
        
        model.addAttribute("error", "Invalid username or password credentials.");
        return "agent-login";
    }

    @GetMapping("/agent/workspace")
    public String showWorkspace(HttpSession session, Model model) {
        Agent agent = (Agent) session.getAttribute("loggedInAgent");
        if (agent == null) {
            return "redirect:/agent/login"; // Redirect to login if not authenticated
        }
        model.addAttribute("agent", agent);
        return "agent-workspace"; // Renders templates/agent-workspace.html
    }

    // ==========================================
    // 2. WEBSOCKET & CHAT PERSISTENCE
    // ==========================================

    @MessageMapping("/chat.send")
    @Transactional
    public void processMessage(@Payload ChatMessage message) {
        System.out.println(">>> RECEIVED CHAT MESSAGE FROM CLIENT: " + message.getMessage());
        if (message.getTimestamp() == 0) {
            message.setTimestamp(System.currentTimeMillis());
        }

        messageRepository.saveAndFlush(message);
        messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), message);
        System.out.println(">>> SUCCESSFULLY SAVED MESSAGE ID TO DB: " + message.getId());
        String assignedAgentId = "1"; 
        messagingTemplate.convertAndSend("/topic/agent-" + assignedAgentId, message);
    }

    // ==========================================
    // 3. HISTORICAL CHAT REST ENDPOINT
    // ==========================================

    @GetMapping("/api/chat/history/{threadId}")
    @ResponseBody
    public List<ChatMessage> getChatHistory(@PathVariable String threadId) {
        return messageRepository.findByThreadIdOrderByTimestampAsc(threadId);
    }
    
    @GetMapping("/api/chat/active-threads")
    @ResponseBody
    public List<ChatMessage> getActiveThreads() {
        return messageRepository.findLatestMessagesPerThread();
    }
    
    @MessageMapping("/chat.edit")
    public void processMessageEdit(@Payload ChatMessage message) {
        // 1. Fetch the original message from DB and update its text
        Optional<ChatMessage> existingOpt = messageRepository.findById(message.getId());
        if (existingOpt.isPresent()) {
            ChatMessage existing = existingOpt.get();
            existing.setMessage(message.getMessage());
            messageRepository.save(existing); // Update in database

            // 2. Broadcast the edited message to the customer thread
            messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), message);

            // 3. Forward the edit payload to the active agent dashboard
            String assignedAgentId = "1"; 
            messagingTemplate.convertAndSend("/topic/agent-" + assignedAgentId, message);
        }
    }
}