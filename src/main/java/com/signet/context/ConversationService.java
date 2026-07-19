package com.signet.context;

import com.signet.shared.domain.Conversation;
import com.signet.shared.domain.ConversationMessage;
import com.signet.shared.domain.MessageRole;
import com.signet.shared.repo.ConversationMessageRepository;
import com.signet.shared.repo.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Управляет памятью диалога: связывает письма в тред, хранит реплики в исходных
 * ролях и строит контекст (sliding window + summary) для генерации ответа.
 */
@Service
public class ConversationService {

    private final ConversationRepository conversations;
    private final ConversationMessageRepository messages;

    public ConversationService(ConversationRepository conversations,
                               ConversationMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public Conversation getOrCreate(String threadRoot, String clientAddr) {
        return conversations.findByThreadId(threadRoot)
                .orElseGet(() -> conversations.save(new Conversation(threadRoot, clientAddr)));
    }

    @Transactional
    public void recordClientMessage(UUID conversationId, UUID emailId, String content) {
        messages.save(new ConversationMessage(conversationId, emailId, MessageRole.USER, content));
        touch(conversationId);
    }

    @Transactional
    public void recordAssistantMessage(UUID conversationId, String content) {
        // В память кладём именно ОТПРАВЛЕННЫЙ финальный текст (после правок менеджера).
        messages.save(new ConversationMessage(conversationId, null, MessageRole.ASSISTANT, content));
        touch(conversationId);
    }

    private void touch(UUID conversationId) {
        conversations.findById(conversationId).ifPresent(c -> {
            c.setLastActivity(Instant.now());
            conversations.save(c);
        });
    }

    /**
     * Контекст для LLM: резюме старой части (если есть) + последние {@code window}
     * реплик диалога дословно.
     */
    @Transactional(readOnly = true)
    public ThreadContext buildContext(UUID conversationId, int window) {
        Conversation conv = conversations.findById(conversationId).orElseThrow();
        List<ConversationMessage> all = messages.findByConversationIdOrderByCreatedAtAsc(conversationId);

        List<ConversationMessage> tail = all.size() > window
                ? all.subList(all.size() - window, all.size())
                : all;

        List<ThreadContext.Turn> turns = tail.stream()
                .map(m -> new ThreadContext.Turn(m.getRole(), m.getContent()))
                .toList();

        String lastClient = tail.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .map(ConversationMessage::getContent)
                .reduce((a, b) -> b)
                .orElse("");

        return new ThreadContext(conv.getThreadId(), conv.getSummary(), turns, lastClient);
    }
}
