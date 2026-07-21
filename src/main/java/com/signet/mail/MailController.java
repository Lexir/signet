package com.signet.mail;

import com.signet.ingest.ParsedAttachment;
import com.signet.mail.MailQueryService.AttachmentView;
import com.signet.mail.MailQueryService.FolderView;
import com.signet.mail.MailQueryService.MailboxView;
import com.signet.mail.MailQueryService.MessageDetail;
import com.signet.mail.MailQueryService.MessagePage;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Чтение почтового зеркала для SPA-клиента: ящики, папки, письма, тело, вложения, ответы. */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final MailQueryService query;
    private final ReplyWorkflowStarter aiReply;
    private final ComposeService compose;

    public MailController(MailQueryService query, ReplyWorkflowStarter aiReply, ComposeService compose) {
        this.query = query;
        this.aiReply = aiReply;
        this.compose = compose;
    }

    public record ReplyReq(String text) {
    }

    @GetMapping("/mailboxes")
    public List<MailboxView> mailboxes() {
        return query.mailboxViews();
    }

    @GetMapping("/{mailboxId}/folders")
    public List<FolderView> folders(@PathVariable String mailboxId) {
        return query.folderViews(mailboxId);
    }

    /** Письма папки. Имя папки — query-параметром: в нём бывают '/', пробелы и т.п. */
    @GetMapping("/{mailboxId}/messages")
    public MessagePage messages(@PathVariable String mailboxId,
                                @RequestParam String folder,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size) {
        return query.messages(mailboxId, folder, page, Math.min(size, 200));
    }

    @GetMapping("/messages/{id}")
    public ResponseEntity<MessageDetail> message(@PathVariable UUID id) {
        return query.message(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/messages/{id}/attachments")
    public List<AttachmentView> attachments(@PathVariable UUID id) {
        return query.attachments(id);
    }

    /** Черновик AI-ответа на письмо (для показа прямо в письме). 204 — генерацию не запускали. */
    @GetMapping("/messages/{id}/draft")
    public ResponseEntity<MailQueryService.DraftView> draft(@PathVariable UUID id) {
        return query.draftFor(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/messages/{id}/attachments/{index}")
    public ResponseEntity<ByteArrayResource> download(@PathVariable UUID id, @PathVariable int index) {
        return query.attachment(id, index)
                .map(MailController::asDownload)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Запустить AI-генерацию ответа (уходит в очередь ревью выбранного каналом ящика). */
    @PostMapping("/{mailboxId}/messages/{id}/generate")
    public ResponseEntity<Void> generate(@PathVariable String mailboxId, @PathVariable UUID id) {
        return aiReply.startAiReply(id)
                ? ResponseEntity.accepted().build()
                : ResponseEntity.notFound().build();
    }

    /** Отправить ручной (авторский) ответ на письмо. */
    @PostMapping("/{mailboxId}/messages/{id}/reply")
    public ResponseEntity<Void> reply(@PathVariable String mailboxId, @PathVariable UUID id,
                                      @RequestBody ReplyReq req) {
        if (req.text() == null || req.text().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).build();
        }
        return compose.sendReply(id, req.text())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private static ResponseEntity<ByteArrayResource> asDownload(ParsedAttachment att) {
        byte[] data = att.data() != null ? att.data() : new byte[0];
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (att.contentType() != null) {
                type = MediaType.parseMediaType(att.contentType());
            }
        } catch (Exception ignored) {
            // остаётся octet-stream
        }
        ContentDisposition cd = ContentDisposition.attachment()
                .filename(att.filename() != null ? att.filename() : "attachment")
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(type)
                .body(new ByteArrayResource(data));
    }
}
