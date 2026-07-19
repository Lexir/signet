package com.signet.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signet.context.ThreadContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DraftWorkflowTest {

    @Mock
    private DraftTransactions tx;
    @Mock
    private DraftService draftService;
    @Mock
    private SenderClassifier senderClassifier;

    private AiProperties props;
    private DraftWorkflow workflow;

    private final UUID emailId = UUID.randomUUID();
    private DraftPayload payload;

    @BeforeEach
    void setUp() {
        props = new AiProperties();                       // onlyHumanSenders = true
        workflow = new DraftWorkflow(tx, draftService, senderClassifier, props);

        ThreadContext ctx = new ThreadContext("<root@x>", null, List.of(), "Привет!");
        payload = new DraftPayload(emailId, "ivan@example.com", "Привет", "Как дела?", "Профиль", ctx);
    }

    @Test
    void ничего_не_делает_если_письмо_уже_в_работе() {
        when(tx.claim(emailId)).thenReturn(Optional.empty());

        workflow.generate(emailId);

        verifyNoInteractions(senderClassifier, draftService);
    }

    @Test
    void не_генерирует_ответ_на_неличное_письмо() {
        when(tx.claim(emailId)).thenReturn(Optional.of(payload));
        when(senderClassifier.isPersonalHuman("ivan@example.com", "Привет", "Как дела?")).thenReturn(false);

        workflow.generate(emailId);

        verify(tx).markIgnored(emailId, "ivan@example.com");
        verifyNoInteractions(draftService);
    }

    @Test
    void генерирует_черновик_для_личного_письма() {
        when(tx.claim(emailId)).thenReturn(Optional.of(payload));
        when(senderClassifier.isPersonalHuman(anyString(), anyString(), anyString())).thenReturn(true);
        DraftService.GenerationResult result = new DraftService.GenerationResult(
                new DraftResponse("Hi!", "en", "Привет!"), 100, 40, "qwen2.5");
        when(draftService.draft(payload.context(), "Привет", "Профиль")).thenReturn(result);

        workflow.generate(emailId);

        verify(tx).saveDraft(emailId, result);
        verify(tx, never()).markFailed(any(), anyString());
    }

    @Test
    void при_сбое_модели_фиксирует_неудачу() {
        when(tx.claim(emailId)).thenReturn(Optional.of(payload));
        when(senderClassifier.isPersonalHuman(anyString(), anyString(), anyString())).thenReturn(true);
        when(draftService.draft(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("ollama недоступна"));

        workflow.generate(emailId);

        // Человек должен узнать о сбое — markFailed публикует ProcessingFailed.
        verify(tx).markFailed(eq(emailId), anyString());
        verify(tx, never()).saveDraft(any(), any());
    }

    @Test
    void при_выключенном_фильтре_классификатор_не_вызывается() {
        props.setOnlyHumanSenders(false);
        when(tx.claim(emailId)).thenReturn(Optional.of(payload));
        when(draftService.draft(any(), anyString(), anyString())).thenReturn(
                new DraftService.GenerationResult(new DraftResponse("Hi", "en", "Привет"), 1, 1, "m"));

        workflow.generate(emailId);

        verifyNoInteractions(senderClassifier);
    }
}
