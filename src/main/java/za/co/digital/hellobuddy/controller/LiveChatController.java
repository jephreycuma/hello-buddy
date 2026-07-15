package za.co.digital.hellobuddy.controller;

import jakarta.servlet.http.HttpSession;
import za.co.digital.hellobuddy.model.Agent;
import za.co.digital.hellobuddy.model.ChatMessage;
import za.co.digital.hellobuddy.repository.AgentRepository;
import za.co.digital.hellobuddy.repository.ChatMessageRepository;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
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

    public LiveChatController(SimpMessagingTemplate messagingTemplate, 
                              ChatMessageRepository messageRepository, 
                              AgentRepository agentRepository) {
        this.messagingTemplate = messagingTemplate;
        this.messageRepository = messageRepository;
        this.agentRepository = agentRepository;
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
        
        if (agentOpt.isPresent() && agentOpt.get().getPassword().equals(password)) {
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
    public void processMessage(@Payload ChatMessage message) {
        if (message.getTimestamp() == 0) {
            message.setTimestamp(System.currentTimeMillis());
        }

        // 1. Save every single message to the DB instantly
        messageRepository.save(message);

        // 2. Broadcast to customer thread
        messagingTemplate.convertAndSend("/topic/thread/" + message.getThreadId(), message);

        // 3. Forward to the agent's live dashboard stream
        // For simplicity, routing to agent ID "1". You can dynamic-link this later.
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
    
 // Add this new endpoint inside your LiveChatController.java class
    @GetMapping("/api/chat/active-threads")
    @ResponseBody
    public List<ChatMessage> getActiveThreads() {
        return messageRepository.findLatestMessagesPerThread();
    }
}