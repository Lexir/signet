package com.signet.shared.repo;

import com.signet.shared.domain.Draft;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DraftRepository extends JpaRepository<Draft, UUID> {

    Optional<Draft> findFirstByEmailIdOrderByCreatedAtDesc(UUID emailId);

    @Query("select coalesce(sum(d.tokensIn),0) from Draft d where d.createdAt >= :after")
    long sumTokensInSince(@Param("after") Instant after);

    @Query("select coalesce(sum(d.tokensOut),0) from Draft d where d.createdAt >= :after")
    long sumTokensOutSince(@Param("after") Instant after);

    /** Токены в разрезе ящиков — одним групповым запросом вместо двух на каждый ящик. */
    @Query("select e.mailboxId as mailboxId, "
            + "coalesce(sum(d.tokensIn),0) as tokensIn, "
            + "coalesce(sum(d.tokensOut),0) as tokensOut "
            + "from Draft d, Email e "
            + "where d.emailId = e.id and d.createdAt >= :after "
            + "group by e.mailboxId")
    List<MailboxTokensView> sumTokensByMailboxSince(@Param("after") Instant after);
}
