package com.signet.shared.repo;

import com.signet.shared.domain.Attachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByEmailId(UUID emailId);

    /** Только идентификаторы — чтобы грузить тяжёлые файлы по одному, а не все сразу. */
    @Query("select a.id from Attachment a where a.emailId = :emailId order by a.createdAt")
    List<UUID> findIdsByEmailId(@Param("emailId") UUID emailId);
}
