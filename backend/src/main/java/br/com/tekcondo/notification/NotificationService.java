package br.com.tekcondo.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import br.com.tekcondo.identity.access.Permission;
import br.com.tekcondo.shared.exception.ApiException;
import br.com.tekcondo.shared.security.CurrentUserProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canal de notificação interno. Outros canais (e-mail etc.) são acionados pelos módulos via MailDispatcher. */
@Service
public class NotificationService {

    public record NotificationResponse(UUID id, String title, String body, String link, boolean read, Instant createdAt) {
    }

    public record Inbox(long unread, List<NotificationResponse> items) {
    }

    private final NotificationRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, CurrentUserProvider currentUserProvider, Clock clock) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    /** Envia para cada usuário; com dedupKey, não repete a mesma notificação ao mesmo usuário. */
    @Transactional
    public int notify(UUID organizationId, Collection<UUID> userIds, String title, String body, String link, String dedupKey) {
        int sent = 0;
        for (UUID userId : userIds.stream().distinct().toList()) {
            if (dedupKey != null && repository.existsByUserIdAndDedupKey(userId, dedupKey)) {
                continue;
            }
            repository.save(new Notification(organizationId, userId, title, body, link, dedupKey));
            sent++;
        }
        return sent;
    }

    public List<UUID> usersWithPermission(UUID organizationId, UUID condominiumId, Permission permission) {
        return repository.usersWithPermission(organizationId, condominiumId, permission.name());
    }

    @Transactional(readOnly = true)
    public Inbox inbox() {
        UUID userId = currentUserProvider.get().userId();
        List<NotificationResponse> items = repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 30)).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getTitle(), n.getBody(), n.getLink(), n.getReadAt() != null,
                        n.getCreatedAt()))
                .toList();
        return new Inbox(repository.countByUserIdAndReadAtIsNull(userId), items);
    }

    @Transactional
    public void markRead(UUID id) {
        UUID userId = currentUserProvider.get().userId();
        Notification n = repository.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notificação"));
        n.markRead(Instant.now(clock));
    }

    @Transactional
    public void markAllRead() {
        repository.markAllRead(currentUserProvider.get().userId(), Instant.now(clock));
    }
}
