package io.github.lnyocly.ai4j.ambient.channel;

import io.github.lnyocly.ai4j.ambient.heartbeat.QuietHoursPolicy;
import io.github.lnyocly.ai4j.ambient.heartbeat.SupervisoryAgenda;
import io.github.lnyocly.ai4j.ambient.memory.MemoryObservation;
import io.github.lnyocly.ai4j.ambient.memory.MemoryTargetLayer;
import io.github.lnyocly.ai4j.ambient.memory.TrustLevel;
import io.github.lnyocly.ai4j.ambient.support.AmbientSupport;
import io.github.lnyocly.ai4j.extension.Disposable;
import io.github.lnyocly.ai4j.harness.HarnessActor;
import io.github.lnyocly.ai4j.harness.HarnessCommandGateway;
import io.github.lnyocly.ai4j.harness.WakeupRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multi-endpoint inbox/outbox router and structured escalation option-card
 * callback handler for the single-soul ambient runtime.
 */
public class ChannelGateway implements Disposable {

    private final Map<String, ChannelEndpoint> endpoints = new LinkedHashMap<String, ChannelEndpoint>();
    private final Set<String> authorizedOwners = new LinkedHashSet<String>();
    private final List<ChannelInboxHandler> inboxHandlers = new ArrayList<ChannelInboxHandler>();
    private final List<EscalationCallbackListener> callbackListeners = new ArrayList<EscalationCallbackListener>();
    private final List<InboundChannelMessage> inboxHistory = new ArrayList<InboundChannelMessage>();
    private final List<OutboundChannelMessage> outboxHistory = new ArrayList<OutboundChannelMessage>();
    private final Map<String, EscalationOptionCard> activeCards = new LinkedHashMap<String, EscalationOptionCard>();
    private final Map<String, String> waitToCardIndex = new LinkedHashMap<String, String>();
    private final Map<String, EscalationCallbackResult> resolvedCallbacks =
            new LinkedHashMap<String, EscalationCallbackResult>();

    private final HarnessCommandGateway harnessGateway;
    private final SupervisoryAgenda agenda;
    private final QuietHoursPolicy quietHoursPolicy;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public ChannelGateway() {
        this(null, new SupervisoryAgenda(), QuietHoursPolicy.disabled());
    }

    public ChannelGateway(HarnessCommandGateway harnessGateway,
                          SupervisoryAgenda agenda,
                          QuietHoursPolicy quietHoursPolicy) {
        this.harnessGateway = harnessGateway;
        this.agenda = agenda;
        this.quietHoursPolicy = quietHoursPolicy == null ? QuietHoursPolicy.disabled() : quietHoursPolicy;
    }

    public synchronized ChannelGateway authorizeOwner(String ownerId) {
        ensureActive();
        String normalized = AmbientSupport.requireNonBlank(ownerId, "ownerId");
        authorizedOwners.add(normalized);
        return this;
    }

    public synchronized Disposable registerEndpoint(ChannelEndpoint endpoint) {
        ensureActive();
        if (endpoint == null) {
            throw new IllegalArgumentException("channel endpoint must not be null");
        }
        final String endpointId = AmbientSupport.requireNonBlank(endpoint.getEndpointId(), "endpointId");
        ChannelEndpoint copy = endpoint.toBuilder()
                .endpointId(endpointId)
                .trustedOwnerIds(endpoint.getTrustedOwnerIds() == null
                        ? new LinkedHashSet<String>()
                        : new LinkedHashSet<String>(endpoint.getTrustedOwnerIds()))
                .metadata(endpoint.getMetadata() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<String, Object>(endpoint.getMetadata()))
                .build();
        endpoints.put(endpointId, copy);
        return new Disposable() {
            @Override
            public void dispose() {
                unregisterEndpoint(endpointId);
            }
        };
    }

    public synchronized void unregisterEndpoint(String endpointId) {
        if (endpointId != null) {
            endpoints.remove(endpointId);
        }
    }

    public synchronized Disposable registerInboxHandler(final ChannelInboxHandler handler) {
        ensureActive();
        if (handler == null) {
            throw new IllegalArgumentException("inbox handler must not be null");
        }
        inboxHandlers.add(handler);
        return new Disposable() {
            @Override
            public void dispose() {
                removeInboxHandler(handler);
            }
        };
    }

    private synchronized void removeInboxHandler(ChannelInboxHandler handler) {
        inboxHandlers.remove(handler);
    }

    public synchronized Disposable registerCallbackListener(final EscalationCallbackListener listener) {
        ensureActive();
        if (listener == null) {
            throw new IllegalArgumentException("callback listener must not be null");
        }
        callbackListeners.add(listener);
        return new Disposable() {
            @Override
            public void dispose() {
                removeCallbackListener(listener);
            }
        };
    }

    private synchronized void removeCallbackListener(EscalationCallbackListener listener) {
        callbackListeners.remove(listener);
    }

    public synchronized InboundDispatchResult receiveInbound(InboundChannelMessage rawMessage) {
        ensureActive();
        if (rawMessage == null) {
            throw new IllegalArgumentException("inbound message must not be null");
        }
        String endpointId = AmbientSupport.requireNonBlank(rawMessage.getEndpointId(), "endpointId");
        ChannelEndpoint endpoint = endpoints.get(endpointId);
        if (endpoint == null) {
            throw new IllegalArgumentException("unknown channel endpoint: " + endpointId);
        }
        TrustLevel resolvedTrust = resolveInboundTrust(endpoint, rawMessage.getSenderId());
        String msgId = AmbientSupport.trimToNull(rawMessage.getMessageId());
        if (msgId == null) {
            msgId = "in_" + UUID.randomUUID().toString().replace("-", "");
        }
        long ts = rawMessage.getReceivedAtEpochMs() > 0L
                ? rawMessage.getReceivedAtEpochMs()
                : System.currentTimeMillis();
        InboundChannelMessage normalized = rawMessage.toBuilder()
                .messageId(msgId)
                .endpointId(endpointId)
                .resolvedTrustLevel(resolvedTrust)
                .receivedAtEpochMs(ts)
                .metadata(rawMessage.getMetadata() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<String, Object>(rawMessage.getMetadata()))
                .build();
        inboxHistory.add(normalized);

        List<ChannelInboxHandler> snapshot = new ArrayList<ChannelInboxHandler>(inboxHandlers);
        for (ChannelInboxHandler handler : snapshot) {
            handler.onMessage(normalized);
        }
        return InboundDispatchResult.builder()
                .message(normalized)
                .resolvedTrustLevel(resolvedTrust)
                .notifiedHandlers(snapshot.size())
                .build();
    }

    /**
     * Converts an inbound channel message into a {@link MemoryObservation} preserving
     * the endpoint/sender-verified {@link TrustLevel}.
     */
    public MemoryObservation toMemoryObservation(InboundChannelMessage message,
                                                 String subjectKey,
                                                 MemoryTargetLayer targetLayer) {
        if (message == null) {
            throw new IllegalArgumentException("inbound message must not be null");
        }
        TrustLevel trust = message.getResolvedTrustLevel();
        if (trust == null && message.getEndpointId() != null) {
            ChannelEndpoint ep;
            synchronized (this) {
                ep = endpoints.get(message.getEndpointId());
            }
            trust = ep == null ? TrustLevel.EXTERNAL_UNTRUSTED : resolveInboundTrust(ep, message.getSenderId());
        }
        return MemoryObservation.builder()
                .observationId(message.getMessageId())
                .scopeKey(message.getScopeKey())
                .subjectKey(subjectKey)
                .statement(message.getText())
                .trustLevel(trust == null ? TrustLevel.EXTERNAL_UNTRUSTED : trust)
                .targetLayer(targetLayer == null ? MemoryTargetLayer.DURABLE_FACT : targetLayer)
                .sourceKind("channel:" + message.getEndpointId())
                .sourceId(message.getSenderId())
                .observedAtEpochMs(message.getReceivedAtEpochMs())
                .metadata(message.getMetadata())
                .build();
    }

    public synchronized OutboundDeliveryReceipt dispatchOutbound(OutboundChannelMessage rawMessage) {
        ensureActive();
        if (rawMessage == null) {
            throw new IllegalArgumentException("outbound message must not be null");
        }
        String msgId = AmbientSupport.trimToNull(rawMessage.getMessageId());
        if (msgId == null) {
            msgId = "out_" + UUID.randomUUID().toString().replace("-", "");
        }
        long ts = rawMessage.getSentAtEpochMs() > 0L
                ? rawMessage.getSentAtEpochMs()
                : System.currentTimeMillis();
        OutboundChannelMessage message = rawMessage.toBuilder()
                .messageId(msgId)
                .sentAtEpochMs(ts)
                .metadata(rawMessage.getMetadata() == null
                        ? new LinkedHashMap<String, Object>()
                        : new LinkedHashMap<String, Object>(rawMessage.getMetadata()))
                .build();

        List<ChannelEndpoint> targets = resolveOutboundTargets(message.getTargetEndpointId(), message.getTargetChannelType());
        List<String> delivered = new ArrayList<String>();
        List<String> skipped = new ArrayList<String>();
        for (ChannelEndpoint ep : targets) {
            if (!ep.isEnabled() || ep.getTransport() == null) {
                skipped.add(ep.getEndpointId());
                continue;
            }
            ep.getTransport().sendMessage(message);
            delivered.add(ep.getEndpointId());
        }
        outboxHistory.add(message);
        return OutboundDeliveryReceipt.builder()
                .messageId(msgId)
                .deliveredEndpointIds(delivered)
                .skippedEndpointIds(skipped)
                .build();
    }

    public synchronized EscalationOptionCard publishEscalationCard(EscalationOptionCard rawCard) {
        ensureActive();
        if (rawCard == null) {
            throw new IllegalArgumentException("escalation card must not be null");
        }
        String question = AmbientSupport.requireNonBlank(rawCard.getQuestion(), "escalation question");
        String cardId = AmbientSupport.trimToNull(rawCard.getCardId());
        if (cardId == null) {
            cardId = "card_" + UUID.randomUUID().toString().replace("-", "");
        }
        long now = rawCard.getCreatedAtEpochMs() > 0L ? rawCard.getCreatedAtEpochMs() : System.currentTimeMillis();
        List<EscalationOptionCard.EscalationOption> copiedOptions = rawCard.getOptions() == null
                ? Collections.<EscalationOptionCard.EscalationOption>emptyList()
                : new ArrayList<EscalationOptionCard.EscalationOption>(rawCard.getOptions());

        String agendaItemId = rawCard.getAgendaItemId();
        if (agenda != null && AmbientSupport.trimToNull(agendaItemId) == null) {
            List<String> optionIds = new ArrayList<String>(copiedOptions.size());
            for (EscalationOptionCard.EscalationOption opt : copiedOptions) {
                if (opt != null && opt.getOptionId() != null) {
                    optionIds.add(opt.getOptionId());
                }
            }
            SupervisoryAgenda.AgendaItem item = agenda.route(
                    SupervisoryAgenda.AgendaItem.builder()
                            .scopeKey(rawCard.getScopeKey())
                            .waitId(rawCard.getWaitId())
                            .taskId(rawCard.getTaskId())
                            .executionId(rawCard.getExecutionId())
                            .source("channel-gateway")
                            .title(rawCard.getTitle() != null ? rawCard.getTitle() : question)
                            .summary(rawCard.getReason() != null ? rawCard.getReason() : question)
                            .priority(SupervisoryAgenda.Priority.HIGH)
                            .options(optionIds)
                            .recommendedOption(rawCard.getRecommendedOptionId())
                            .informationalOnly(false)
                            .build(),
                    quietHoursPolicy,
                    Instant.ofEpochMilli(now)
            );
            agendaItemId = item.getItemId();
        }

        EscalationOptionCard stored = rawCard.toBuilder()
                .cardId(cardId)
                .question(question)
                .agendaItemId(agendaItemId)
                .options(copiedOptions)
                .targetEndpointIds(rawCard.getTargetEndpointIds() == null
                        ? Collections.<String>emptyList()
                        : new ArrayList<String>(rawCard.getTargetEndpointIds()))
                .createdAtEpochMs(now)
                .build();
        activeCards.put(cardId, stored);
        if (stored.getWaitId() != null && !stored.getWaitId().trim().isEmpty()) {
            waitToCardIndex.put(stored.getWaitId().trim(), cardId);
        }

        List<ChannelEndpoint> targets = resolveCardTargets(stored.getTargetEndpointIds());
        for (ChannelEndpoint ep : targets) {
            if (ep.isEnabled() && ep.getTransport() != null) {
                ep.getTransport().sendEscalationCard(stored);
            }
        }
        return stored;
    }

    public synchronized EscalationCallbackResult handleEscalationCallback(EscalationCallback callback) {
        ensureActive();
        if (callback == null) {
            throw new IllegalArgumentException("escalation callback must not be null");
        }
        String cardId = AmbientSupport.trimToNull(callback.getCardId());
        if (cardId == null && AmbientSupport.trimToNull(callback.getWaitId()) != null) {
            cardId = waitToCardIndex.get(callback.getWaitId().trim());
        }
        EscalationOptionCard card = cardId == null ? null : activeCards.get(cardId);
        if (card == null) {
            return EscalationCallbackResult.builder()
                    .status(EscalationCallbackResult.Status.UNKNOWN_CARD)
                    .cardId(cardId)
                    .waitId(callback.getWaitId())
                    .reason("escalation card not found")
                    .build();
        }

        EscalationCallbackResult prior = resolvedCallbacks.get(card.getCardId());
        if (prior != null) {
            return EscalationCallbackResult.builder()
                    .status(EscalationCallbackResult.Status.IGNORED_DUPLICATE)
                    .cardId(card.getCardId())
                    .waitId(card.getWaitId())
                    .selectedOptionId(prior.getSelectedOptionId())
                    .reason("callback ignored: card already resolved")
                    .wakeup(prior.getWakeup())
                    .agendaItem(prior.getAgendaItem())
                    .build();
        }

        HarnessActor responderActor = callback.getResponderActor();
        if (responderActor != null && responderActor.isAgent()) {
            return EscalationCallbackResult.builder()
                    .status(EscalationCallbackResult.Status.REJECTED_UNAUTHORIZED)
                    .cardId(card.getCardId())
                    .waitId(card.getWaitId())
                    .reason("agent actor is not permitted to submit escalation rulings")
                    .build();
        }
        if (!isAuthorizedResponder(callback.getEndpointId(), callback.getResponderId(), responderActor)) {
            return EscalationCallbackResult.builder()
                    .status(EscalationCallbackResult.Status.REJECTED_UNAUTHORIZED)
                    .cardId(card.getCardId())
                    .waitId(card.getWaitId())
                    .reason("responder is not authorized for owner escalation rulings: "
                            + callback.getResponderId())
                    .build();
        }

        String selectedOption = AmbientSupport.trimToNull(callback.getSelectedOptionId());
        if (!isValidOption(card, selectedOption)) {
            return EscalationCallbackResult.builder()
                    .status(EscalationCallbackResult.Status.INVALID_OPTION)
                    .cardId(card.getCardId())
                    .waitId(card.getWaitId())
                    .selectedOptionId(selectedOption)
                    .reason("option is not valid for escalation card: " + selectedOption)
                    .build();
        }

        Instant respondedAt = callback.getRespondedAtEpochMs() > 0L
                ? Instant.ofEpochMilli(callback.getRespondedAtEpochMs())
                : Instant.now();
        String actorId = AmbientSupport.trimToNull(callback.getResponderId());
        if (actorId == null && responderActor != null) {
            actorId = responderActor.getId();
        }
        if (actorId == null) {
            actorId = "owner";
        }
        HarnessActor effectiveActor = responderActor != null ? responderActor : HarnessActor.human(actorId);

        SupervisoryAgenda.AgendaItem updatedItem = null;
        if (agenda != null && card.getAgendaItemId() != null) {
            updatedItem = agenda.recordAnswer(
                    card.getAgendaItemId(),
                    selectedOption,
                    callback.getComment(),
                    actorId,
                    respondedAt
            );
        }

        WakeupRecord wakeup = null;
        if (harnessGateway != null && card.getWaitId() != null) {
            Map<String, Object> deliveryPayload = new LinkedHashMap<String, Object>();
            deliveryPayload.put("cardId", card.getCardId());
            deliveryPayload.put("selectedOptionId", selectedOption);
            deliveryPayload.put("comment", callback.getComment());
            deliveryPayload.put("endpointId", callback.getEndpointId());
            deliveryPayload.put("responderId", actorId);
            wakeup = harnessGateway.deliverWait(card.getWaitId(), deliveryPayload, effectiveActor);
        }

        EscalationCallbackResult applied = EscalationCallbackResult.builder()
                .status(EscalationCallbackResult.Status.APPLIED)
                .cardId(card.getCardId())
                .waitId(card.getWaitId())
                .selectedOptionId(selectedOption)
                .reason("escalation ruling applied")
                .wakeup(wakeup)
                .agendaItem(updatedItem)
                .build();
        resolvedCallbacks.put(card.getCardId(), applied);

        List<EscalationCallbackListener> listeners = new ArrayList<EscalationCallbackListener>(callbackListeners);
        for (EscalationCallbackListener listener : listeners) {
            listener.onCallbackApplied(card, callback, applied);
        }
        return applied;
    }

    private TrustLevel resolveInboundTrust(ChannelEndpoint endpoint, String senderId) {
        String normalizedSender = AmbientSupport.trimToNull(senderId);
        if (normalizedSender != null) {
            if (authorizedOwners.contains(normalizedSender)
                    || (endpoint.getTrustedOwnerIds() != null
                    && endpoint.getTrustedOwnerIds().contains(normalizedSender))) {
                return TrustLevel.OWNER_DIRECT;
            }
        }
        return endpoint.getDefaultTrustLevel() == null
                ? TrustLevel.EXTERNAL_UNTRUSTED
                : endpoint.getDefaultTrustLevel();
    }

    private boolean isAuthorizedResponder(String endpointId, String responderId, HarnessActor actor) {
        String normalizedResponder = AmbientSupport.trimToNull(responderId);
        if (normalizedResponder != null && authorizedOwners.contains(normalizedResponder)) {
            return true;
        }
        ChannelEndpoint ep = endpointId == null ? null : endpoints.get(endpointId);
        if (ep != null) {
            if (normalizedResponder != null && ep.getTrustedOwnerIds() != null
                    && ep.getTrustedOwnerIds().contains(normalizedResponder)) {
                return true;
            }
            if (ep.getDefaultTrustLevel() == TrustLevel.OWNER_DIRECT) {
                return true;
            }
            return false;
        }
        return actor != null && actor.isHuman() && authorizedOwners.isEmpty();
    }

    private boolean isValidOption(EscalationOptionCard card, String selectedOption) {
        if (selectedOption == null) {
            return false;
        }
        List<EscalationOptionCard.EscalationOption> options = card.getOptions();
        if (options == null || options.isEmpty()) {
            return true;
        }
        for (EscalationOptionCard.EscalationOption option : options) {
            if (option != null && selectedOption.equals(option.getOptionId())) {
                return true;
            }
        }
        return false;
    }

    private List<ChannelEndpoint> resolveOutboundTargets(String endpointId, ChannelType channelType) {
        String targetId = AmbientSupport.trimToNull(endpointId);
        if (targetId != null) {
            ChannelEndpoint ep = endpoints.get(targetId);
            return ep == null ? Collections.<ChannelEndpoint>emptyList() : Collections.singletonList(ep);
        }
        List<ChannelEndpoint> matched = new ArrayList<ChannelEndpoint>();
        for (ChannelEndpoint ep : endpoints.values()) {
            if (channelType == null || ep.getChannelType() == channelType) {
                matched.add(ep);
            }
        }
        return matched;
    }

    private List<ChannelEndpoint> resolveCardTargets(List<String> targetEndpointIds) {
        if (targetEndpointIds == null || targetEndpointIds.isEmpty()) {
            return new ArrayList<ChannelEndpoint>(endpoints.values());
        }
        List<ChannelEndpoint> matched = new ArrayList<ChannelEndpoint>();
        for (String id : targetEndpointIds) {
            ChannelEndpoint ep = endpoints.get(id);
            if (ep != null) {
                matched.add(ep);
            }
        }
        return matched;
    }

    public synchronized Map<String, ChannelEndpoint> getEndpoints() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, ChannelEndpoint>(endpoints));
    }

    public synchronized List<InboundChannelMessage> getInboxHistory() {
        return Collections.unmodifiableList(new ArrayList<InboundChannelMessage>(inboxHistory));
    }

    public synchronized List<OutboundChannelMessage> getOutboxHistory() {
        return Collections.unmodifiableList(new ArrayList<OutboundChannelMessage>(outboxHistory));
    }

    public synchronized EscalationOptionCard getCard(String cardId) {
        return activeCards.get(cardId);
    }

    public boolean isDisposed() {
        return disposed.get();
    }

    @Override
    public synchronized void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        endpoints.clear();
        inboxHandlers.clear();
        callbackListeners.clear();
        activeCards.clear();
        waitToCardIndex.clear();
        resolvedCallbacks.clear();
    }

    private void ensureActive() {
        if (disposed.get()) {
            throw new IllegalStateException("ChannelGateway has been disposed");
        }
    }

    @FunctionalInterface
    public interface ChannelInboxHandler {
        void onMessage(InboundChannelMessage message);
    }

    @FunctionalInterface
    public interface EscalationCallbackListener {
        void onCallbackApplied(EscalationOptionCard card,
                               EscalationCallback callback,
                               EscalationCallbackResult result);
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InboundDispatchResult {
        private InboundChannelMessage message;
        private TrustLevel resolvedTrustLevel;
        private int notifiedHandlers;
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutboundDeliveryReceipt {
        private String messageId;
        @Builder.Default
        private List<String> deliveredEndpointIds = new ArrayList<String>();
        @Builder.Default
        private List<String> skippedEndpointIds = new ArrayList<String>();
    }

    @Data
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationCallbackResult {
        public enum Status {
            APPLIED,
            IGNORED_DUPLICATE,
            REJECTED_UNAUTHORIZED,
            INVALID_OPTION,
            UNKNOWN_CARD
        }

        private Status status;
        private String cardId;
        private String waitId;
        private String selectedOptionId;
        private String reason;
        private WakeupRecord wakeup;
        private SupervisoryAgenda.AgendaItem agendaItem;
    }
}
