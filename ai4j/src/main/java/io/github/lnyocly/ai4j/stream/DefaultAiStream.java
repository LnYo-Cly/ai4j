package io.github.lnyocly.ai4j.stream;

import io.github.lnyocly.ai4j.client.ModelCall;
import io.github.lnyocly.ai4j.client.ModelClient;
import io.github.lnyocly.ai4j.exception.Ai4jException;
import io.github.lnyocly.ai4j.model.Block;
import io.github.lnyocly.ai4j.model.EventDeltaKind;
import io.github.lnyocly.ai4j.model.Message;
import io.github.lnyocly.ai4j.model.ModelCallStatus;
import io.github.lnyocly.ai4j.model.ModelEvent;
import io.github.lnyocly.ai4j.model.ModelEventListener;
import io.github.lnyocly.ai4j.model.ModelEventType;
import io.github.lnyocly.ai4j.model.ModelItem;
import io.github.lnyocly.ai4j.model.ModelRequest;
import io.github.lnyocly.ai4j.model.ModelResult;
import io.github.lnyocly.ai4j.model.ModelUsage;
import io.github.lnyocly.ai4j.model.Protocol;
import io.github.lnyocly.ai4j.model.ReasoningBlock;
import io.github.lnyocly.ai4j.model.ReasoningItem;
import io.github.lnyocly.ai4j.model.TextBlock;
import io.github.lnyocly.ai4j.model.ToolCallBlock;
import io.github.lnyocly.ai4j.model.ToolCallItem;
import io.github.lnyocly.ai4j.model.ToolCallValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Default thread-safe, non-blocking {@link AiStream} implementation with ordered
 * replay for late subscribers, safe immutable {@code partial} snapshots, and
 * pull-based {@link #iterable()} support.
 */
public final class DefaultAiStream<T> implements AiStream<T> {

    private final Object stateLock = new Object();
    private final List<AiStreamEvent> history = new ArrayList<AiStreamEvent>();
    private final List<ListenerSubscription<T>> subscriptions = new CopyOnWriteArrayList<ListenerSubscription<T>>();
    private final CompletableFuture<AiReply<T>> future = new CompletableFuture<AiReply<T>>();

    private volatile Runnable cancelAction;
    private volatile boolean cancelled;
    private boolean terminalRecorded;
    private boolean settled;
    private AiReply<T> terminalReply;
    private Throwable terminalError;

    public DefaultAiStream() {
    }

    public static <T> DefaultAiStream<T> create() {
        return new DefaultAiStream<T>();
    }

    /** Bridge a single-turn {@link ModelClient#createStream} call into a Pi-style {@link AiStream}. */
    public static <T> AiStream<T> fromModelClient(ModelClient client,
                                                  ModelRequest request,
                                                  Function<ModelResult, T> resultMapper) {
        return fromModelClient(client, request, resultMapper, DispatcherProvider.executor());
    }

    /** Bridge a single-turn {@link ModelClient#createStream} call using a specific executor. */
    public static <T> AiStream<T> fromModelClient(final ModelClient client,
                                                  final ModelRequest request,
                                                  final Function<ModelResult, T> resultMapper,
                                                  Executor executor) {
        Objects.requireNonNull(client, "client is required");
        Objects.requireNonNull(request, "request is required");
        Objects.requireNonNull(resultMapper, "resultMapper is required");
        final DefaultAiStream<T> stream = new DefaultAiStream<T>();
        final BlockTracker tracker = new BlockTracker(stream);
        final Executor targetExecutor = executor == null ? DispatcherProvider.executor() : executor;

        stream.emitStart();
        final ModelCall call;
        try {
            call = client.createStream(request, new ModelEventListener() {
                @Override
                public void onEvent(ModelEvent event) {
                    if (stream.isCancelled() || event == null) {
                        return;
                    }
                    tracker.accept(event);
                }
            });
        } catch (Throwable startFailure) {
            stream.fail(startFailure);
            return stream;
        }

        stream.bindCancel(new Runnable() {
            @Override
            public void run() {
                call.cancel();
            }
        });

        targetExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ModelResult result = call.await();
                    if (stream.isCancelled() || call.status() == ModelCallStatus.CANCELLED) {
                        stream.cancel();
                        return;
                    }
                    tracker.closeActiveBlock();
                    T data = resultMapper.apply(result);
                    AiReply<T> reply = AiReply.fromModelResult(result, data);
                    stream.complete(reply);
                } catch (Throwable failure) {
                    if (stream.isCancelled()) {
                        return;
                    }
                    stream.fail(failure);
                }
            }
        });
        return stream;
    }

    public void bindCancel(Runnable cancelAction) {
        this.cancelAction = cancelAction;
        if (this.cancelled && cancelAction != null) {
            try {
                cancelAction.run();
            } catch (Exception ignored) {
                // Best effort cancellation propagation.
            }
        }
    }

    /** Emit the initial {@link AiStreamEventType#START} event. */
    public void emitStart() {
        emit(AiStreamEvent.builder(AiStreamEventType.START)
                .partial(Message.assistant().build())
                .build());
    }

    /** Append and dispatch an intermediate event if the stream is not yet settled. */
    public void emit(AiStreamEvent event) {
        Objects.requireNonNull(event, "event is required");
        synchronized (stateLock) {
            if (terminalRecorded || cancelled) {
                return;
            }
            history.add(event);
            stateLock.notifyAll();
        }
        drainAll();
    }

    /** Complete the stream with a final reply and emit {@link AiStreamEventType#DONE}. */
    public void complete(AiReply<T> reply) {
        Objects.requireNonNull(reply, "reply is required");
        AiStreamEvent doneEvent = AiStreamEvent.builder(AiStreamEventType.DONE)
                .stopReason(reply.getStopReason())
                .partial(reply.getMessage() != null ? reply.getMessage() : Message.assistant().build())
                .finalReply(reply)
                .usage(reply.getUsage())
                .build();
        synchronized (stateLock) {
            if (terminalRecorded || cancelled) {
                return;
            }
            terminalRecorded = true;
            terminalReply = reply;
            history.add(doneEvent);
            stateLock.notifyAll();
        }
        drainAll();
        synchronized (stateLock) {
            settled = true;
            stateLock.notifyAll();
        }
        future.complete(reply);
    }

    /** Fail the stream and emit {@link AiStreamEventType#ERROR}. */
    public void fail(Throwable error) {
        Throwable effective = error == null ? new Ai4jException("Stream failed") : error;
        AiStreamEvent errorEvent = AiStreamEvent.builder(AiStreamEventType.ERROR)
                .error(effective)
                .build();
        synchronized (stateLock) {
            if (terminalRecorded || cancelled) {
                return;
            }
            terminalRecorded = true;
            terminalError = effective;
            history.add(errorEvent);
            stateLock.notifyAll();
        }
        drainAll();
        synchronized (stateLock) {
            settled = true;
            stateLock.notifyAll();
        }
        future.completeExceptionally(effective);
    }

    @Override
    public void cancel() {
        boolean shouldCancel = false;
        synchronized (stateLock) {
            if (!terminalRecorded && !cancelled) {
                cancelled = true;
                terminalRecorded = true;
                settled = true;
                terminalError = new CancellationException("AiStream cancelled");
                shouldCancel = true;
                stateLock.notifyAll();
            }
        }
        if (shouldCancel) {
            Runnable action = cancelAction;
            if (action != null) {
                try {
                    action.run();
                } catch (Exception ignored) {
                    // Best effort upstream cancellation.
                }
            }
            future.cancel(false);
            drainAll();
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public AiStream<T> onEvent(final Consumer<AiStreamEvent> listener) {
        if (listener == null) {
            return this;
        }
        return subscribe(new ListenerSubscription<T>(listener, null, null, null, null));
    }

    @Override
    public AiStream<T> onTextDelta(final Consumer<String> onDelta) {
        if (onDelta == null) {
            return this;
        }
        return subscribe(new ListenerSubscription<T>(null, onDelta, null, null, null));
    }

    @Override
    public AiStream<T> onThinkingDelta(final Consumer<String> onThinking) {
        if (onThinking == null) {
            return this;
        }
        return subscribe(new ListenerSubscription<T>(null, null, onThinking, null, null));
    }

    @Override
    public AiStream<T> onComplete(final Consumer<AiReply<T>> onDone) {
        if (onDone == null) {
            return this;
        }
        return subscribe(new ListenerSubscription<T>(null, null, null, onDone, null));
    }

    @Override
    public AiStream<T> onError(final Consumer<Throwable> onError) {
        if (onError == null) {
            return this;
        }
        return subscribe(new ListenerSubscription<T>(null, null, null, null, onError));
    }

    @Override
    public AiReply<T> await() {
        synchronized (stateLock) {
            while (!settled) {
                try {
                    stateLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancel();
                    throw new Ai4jException("Interrupted while awaiting AiStream", e);
                }
            }
            if (cancelled) {
                throw new Ai4jException("AiStream was cancelled", terminalError);
            }
            if (terminalError != null) {
                if (terminalError instanceof RuntimeException) {
                    throw (RuntimeException) terminalError;
                }
                throw new Ai4jException(terminalError.getMessage(), terminalError);
            }
            return terminalReply;
        }
    }

    @Override
    public CompletableFuture<AiReply<T>> toFuture() {
        return future;
    }

    @Override
    public Iterable<AiStreamEvent> iterable() {
        return new Iterable<AiStreamEvent>() {
            @Override
            public Iterator<AiStreamEvent> iterator() {
                return new StreamEventIterator();
            }
        };
    }

    private AiStream<T> subscribe(ListenerSubscription<T> subscription) {
        subscriptions.add(subscription);
        drainSubscription(subscription);
        return this;
    }

    private void drainAll() {
        for (ListenerSubscription<T> subscription : subscriptions) {
            drainSubscription(subscription);
        }
    }

    private void drainSubscription(ListenerSubscription<T> subscription) {
        synchronized (subscription.deliveryLock) {
            while (true) {
                AiStreamEvent next = null;
                boolean deliverComplete = false;
                AiReply<T> replySnapshot = null;
                boolean deliverError = false;
                Throwable errorSnapshot = null;

                synchronized (stateLock) {
                    if (subscription.deliveredCount < history.size()) {
                        next = history.get(subscription.deliveredCount);
                        subscription.deliveredCount++;
                    } else if (terminalRecorded && !subscription.terminalDelivered) {
                        subscription.terminalDelivered = true;
                        if (terminalReply != null) {
                            deliverComplete = true;
                            replySnapshot = terminalReply;
                        } else if (terminalError != null && !cancelled) {
                            deliverError = true;
                            errorSnapshot = terminalError;
                        }
                    }
                }

                if (next != null) {
                    subscription.deliverEvent(next);
                } else if (deliverComplete) {
                    subscription.deliverComplete(replySnapshot);
                } else if (deliverError) {
                    subscription.deliverError(errorSnapshot);
                } else {
                    break;
                }
            }
        }
    }

    private final class StreamEventIterator implements Iterator<AiStreamEvent> {

        private int cursor;

        @Override
        public boolean hasNext() {
            synchronized (stateLock) {
                while (cursor >= history.size() && !terminalRecorded) {
                    try {
                        stateLock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
                return cursor < history.size();
            }
        }

        @Override
        public AiStreamEvent next() {
            synchronized (stateLock) {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more events in AiStream");
                }
                return history.get(cursor++);
            }
        }
    }

    private static final class ListenerSubscription<T> {

        private final Object deliveryLock = new Object();
        private final Consumer<AiStreamEvent> eventListener;
        private final Consumer<String> textListener;
        private final Consumer<String> thinkingListener;
        private final Consumer<AiReply<T>> completeListener;
        private final Consumer<Throwable> errorListener;
        private int deliveredCount;
        private boolean terminalDelivered;

        private ListenerSubscription(Consumer<AiStreamEvent> eventListener,
                                     Consumer<String> textListener,
                                     Consumer<String> thinkingListener,
                                     Consumer<AiReply<T>> completeListener,
                                     Consumer<Throwable> errorListener) {
            this.eventListener = eventListener;
            this.textListener = textListener;
            this.thinkingListener = thinkingListener;
            this.completeListener = completeListener;
            this.errorListener = errorListener;
        }

        private void deliverEvent(AiStreamEvent event) {
            if (eventListener != null) {
                eventListener.accept(event);
            }
            if (textListener != null && event.getType() == AiStreamEventType.TEXT_DELTA && event.getDelta() != null) {
                textListener.accept(event.getDelta());
            }
            if (thinkingListener != null && event.getType() == AiStreamEventType.THINKING_DELTA && event.getDelta() != null) {
                thinkingListener.accept(event.getDelta());
            }
        }

        private void deliverComplete(AiReply<T> reply) {
            if (completeListener != null) {
                completeListener.accept(reply);
            }
        }

        private void deliverError(Throwable error) {
            if (errorListener != null) {
                errorListener.accept(error);
            }
        }
    }

    /**
     * Stateful Pi-style 3-phase block accumulator converting low-level {@link ModelEvent}s
     * into symmetric START/DELTA/END events with {@code contentIndex} and immutable {@code partial} snapshots.
     */
    public static final class BlockTracker {

        private enum ActiveKind {
            NONE, THINKING, TEXT, TOOLCALL
        }

        private final DefaultAiStream<?> stream;
        private final List<Block> completedBlocks = new ArrayList<Block>();
        private final StringBuilder activeBuffer = new StringBuilder();
        private ActiveKind activeKind = ActiveKind.NONE;
        private int activeIndex = -1;
        private int nextContentIndex = 0;
        private String activeCallId;
        private String activeToolName;
        private String activeSlotKey;
        private String activeSignature;
        private Protocol activeProtocol;

        public BlockTracker(DefaultAiStream<?> stream) {
            this.stream = Objects.requireNonNull(stream, "stream is required");
        }

        public synchronized void accept(ModelEvent event) {
            if (event == null) {
                return;
            }
            if (event.getType() == ModelEventType.ERROR) {
                if (event.getFailure() != null) {
                    stream.fail(io.github.lnyocly.ai4j.client.ModelCallException.fromMarker(event.getFailure()));
                } else {
                    stream.fail(new Ai4jException("Model stream failed"));
                }
                return;
            }
            if (event.getType() == ModelEventType.ITEM_START) {
                String type = event.getItemType();
                if ("function_call".equals(type) || "tool_use".equals(type)) {
                    ensureToolCallStarted(slotKeyOf(event), event.getItemId(), event.getToolName(), event.getProtocol());
                }
                return;
            }
            if (event.getType() == ModelEventType.ITEM_DELTA) {
                EventDeltaKind kind = event.getDeltaKind();
                if (kind == EventDeltaKind.REASONING) {
                    onThinkingDelta(event.getDeltaText(), event.getSignature(), event.getProtocol());
                } else if (kind == EventDeltaKind.TEXT) {
                    onTextDelta(event.getDeltaText(), event.getSignature(), event.getProtocol());
                } else if (kind == EventDeltaKind.TOOL_ARGS) {
                    onToolCallDelta(slotKeyOf(event), event.getItemId(), event.getToolName(),
                            event.getDeltaText(), event.getSignature(), event.getProtocol());
                } else if (kind == EventDeltaKind.OTHER && "signature_delta".equals(event.getItemType())) {
                    appendSignature(event.getDeltaText(), event.getProtocol());
                }
                return;
            }
            if (event.getType() == ModelEventType.ITEM_DONE) {
                if (!event.getItems().isEmpty()) {
                    for (ModelItem each : event.getItems()) {
                        acceptCompletedItem(each);
                    }
                } else if (event.getItem() != null) {
                    acceptCompletedItem(event.getItem());
                } else if (event.getItemId() != null || activeKind != ActiveKind.NONE) {
                    closeActiveBlock();
                }
            }
        }

        public synchronized void onThinkingDelta(String delta, String signature, Protocol protocol) {
            if (activeKind != ActiveKind.THINKING) {
                closeActiveBlock();
                activeKind = ActiveKind.THINKING;
                activeIndex = nextContentIndex++;
                activeBuffer.setLength(0);
                activeSignature = null;
                activeProtocol = protocol;
                stream.emit(AiStreamEvent.builder(AiStreamEventType.THINKING_START)
                        .contentIndex(activeIndex)
                        .partial(snapshotPartial())
                        .build());
            }
            if (delta != null) {
                activeBuffer.append(delta);
            }
            if (signature != null) {
                setSignature(signature, protocol);
            }
            if (delta != null && !delta.isEmpty()) {
                stream.emit(AiStreamEvent.builder(AiStreamEventType.THINKING_DELTA)
                        .contentIndex(activeIndex)
                        .delta(delta)
                        .partial(snapshotPartial())
                        .build());
            }
        }

        public synchronized void onTextDelta(String delta, String signature, Protocol protocol) {
            if (activeKind != ActiveKind.TEXT) {
                closeActiveBlock();
                activeKind = ActiveKind.TEXT;
                activeIndex = nextContentIndex++;
                activeBuffer.setLength(0);
                activeSignature = null;
                activeProtocol = protocol;
                stream.emit(AiStreamEvent.builder(AiStreamEventType.TEXT_START)
                        .contentIndex(activeIndex)
                        .partial(snapshotPartial())
                        .build());
            }
            if (delta != null) {
                activeBuffer.append(delta);
            }
            if (signature != null) {
                setSignature(signature, protocol);
            }
            if (delta != null && !delta.isEmpty()) {
                stream.emit(AiStreamEvent.builder(AiStreamEventType.TEXT_DELTA)
                        .contentIndex(activeIndex)
                        .delta(delta)
                        .partial(snapshotPartial())
                        .build());
            }
        }

        public synchronized void onToolCallDelta(String slotKey, String callId, String toolName,
                                                 String jsonDelta, String signature, Protocol protocol) {
            ensureToolCallStarted(slotKey, callId, toolName, protocol);
            if (jsonDelta != null) {
                activeBuffer.append(jsonDelta);
            }
            if (signature != null) {
                setSignature(signature, protocol);
            }
            if (jsonDelta != null && !jsonDelta.isEmpty()) {
                stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOLCALL_DELTA)
                        .contentIndex(activeIndex)
                        .callId(activeCallId)
                        .toolName(activeToolName)
                        .delta(jsonDelta)
                        .partial(snapshotPartial())
                        .build());
            }
        }

        private void ensureToolCallStarted(String slotKey, String callId, String toolName, Protocol protocol) {
            boolean sameActive = activeKind == ActiveKind.TOOLCALL
                    && (slotKey == null ? activeSlotKey == null : slotKey.equals(activeSlotKey))
                    && (callId == null || activeCallId == null || callId.equals(activeCallId));
            if (sameActive) {
                if (activeCallId == null && callId != null) {
                    activeCallId = callId;
                }
                if ((activeToolName == null || activeToolName.isEmpty()) && toolName != null) {
                    activeToolName = toolName;
                }
                return;
            }
            closeActiveBlock();
            activeKind = ActiveKind.TOOLCALL;
            activeIndex = nextContentIndex++;
            activeBuffer.setLength(0);
            activeSlotKey = slotKey;
            activeCallId = callId == null ? ("call_" + activeIndex) : callId;
            activeToolName = toolName == null ? "" : toolName;
            activeSignature = null;
            activeProtocol = protocol;
            stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOLCALL_START)
                    .contentIndex(activeIndex)
                    .callId(activeCallId)
                    .toolName(activeToolName)
                    .partial(snapshotPartial())
                    .build());
        }

        private void acceptCompletedItem(ModelItem item) {
            if (item instanceof ReasoningItem) {
                ReasoningItem r = (ReasoningItem) item;
                if (activeKind == ActiveKind.THINKING && activeBuffer.length() > 0) {
                    if (r.getSignature() != null) {
                        setSignature(r.getSignature(), r.getSourceProtocol());
                    }
                    closeActiveBlock();
                } else {
                    String text = r.getText() != null ? r.getText() : r.getSummary();
                    onThinkingDelta(text, r.getSignature(), r.getSourceProtocol());
                    closeActiveBlock();
                }
            } else if (item instanceof Message) {
                for (Block block : ((Message) item).getBlocks()) {
                    if (block instanceof TextBlock) {
                        TextBlock tb = (TextBlock) block;
                        Object sig = tb.getProtocolData().get("thoughtSignature");
                        String sigStr = sig == null ? null : String.valueOf(sig);
                        Protocol tbProto = protocolFromMap(tb.getProtocolData());
                        if (activeKind == ActiveKind.TEXT && activeBuffer.length() > 0) {
                            if (sigStr != null) {
                                setSignature(sigStr, tbProto);
                            }
                            closeActiveBlock();
                        } else {
                            onTextDelta(tb.getText(), sigStr, tbProto);
                            closeActiveBlock();
                        }
                    } else if (block instanceof ReasoningBlock) {
                        ReasoningBlock rb = (ReasoningBlock) block;
                        if (activeKind == ActiveKind.THINKING && activeBuffer.length() > 0) {
                            if (rb.getSignature() != null) {
                                setSignature(rb.getSignature(), rb.getSourceProtocol());
                            }
                            closeActiveBlock();
                        } else {
                            onThinkingDelta(rb.getText(), rb.getSignature(), rb.getSourceProtocol());
                            closeActiveBlock();
                        }
                    } else if (block instanceof ToolCallBlock) {
                        emitCompletedToolCall(((ToolCallBlock) block).getValue());
                    }
                }
            } else if (item instanceof ToolCallItem) {
                emitCompletedToolCall(((ToolCallItem) item).getValue());
            }
        }

        private void emitCompletedToolCall(ToolCallValue value) {
            if (activeKind == ActiveKind.TOOLCALL
                    && (value.getCallId().equals(activeCallId) || activeBuffer.length() > 0)) {
                if (activeBuffer.length() == 0 && value.getArgumentsJson() != null) {
                    activeBuffer.append(value.getArgumentsJson());
                }
                if (value.getThoughtSignature() != null) {
                    activeSignature = value.getThoughtSignature();
                    activeProtocol = value.getSourceProtocol();
                }
                closeActiveBlock();
                return;
            }
            closeActiveBlock();
            int index = nextContentIndex++;
            ToolCallBlock block = ToolCallBlock.of(value);
            completedBlocks.add(block);
            Message partial = Message.assistant().blocks(completedBlocks).build();
            stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOLCALL_START)
                    .contentIndex(index)
                    .callId(value.getCallId())
                    .toolName(value.getName())
                    .partial(partial)
                    .build());
            if (!value.getArgumentsJson().isEmpty()) {
                stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOLCALL_DELTA)
                        .contentIndex(index)
                        .callId(value.getCallId())
                        .toolName(value.getName())
                        .delta(value.getArgumentsJson())
                        .partial(partial)
                        .build());
            }
            stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOLCALL_END)
                    .contentIndex(index)
                    .callId(value.getCallId())
                    .toolName(value.getName())
                    .toolCall(block)
                    .partial(partial)
                    .build());
        }

        public synchronized void closeActiveBlock() {
            if (activeKind == ActiveKind.NONE) {
                return;
            }
            ActiveKind closing = activeKind;
            int index = activeIndex;
            String accumulated = activeBuffer.toString();
            activeKind = ActiveKind.NONE;
            activeBuffer.setLength(0);

            if (closing == ActiveKind.THINKING) {
                ReasoningBlock block = ReasoningBlock.of(accumulated, activeSignature, activeProtocol);
                completedBlocks.add(block);
                stream.emit(AiStreamEvent.builder(AiStreamEventType.THINKING_END)
                        .contentIndex(index)
                        .content(accumulated)
                        .partial(snapshotPartial())
                        .build());
            } else if (closing == ActiveKind.TEXT) {
                Map<String, Object> protocolData = null;
                if (activeSignature != null && activeProtocol == Protocol.GEMINI) {
                    protocolData = new LinkedHashMap<String, Object>();
                    protocolData.put("_protocol", "GEMINI");
                    protocolData.put("thoughtSignature", activeSignature);
                }
                TextBlock block = TextBlock.of(accumulated, null, null, protocolData);
                completedBlocks.add(block);
                stream.emit(AiStreamEvent.builder(AiStreamEventType.TEXT_END)
                        .contentIndex(index)
                        .content(accumulated)
                        .partial(snapshotPartial())
                        .build());
            } else if (closing == ActiveKind.TOOLCALL) {
                ToolCallValue value = ToolCallValue.of(
                        activeCallId == null ? ("call_" + index) : activeCallId,
                        activeToolName == null ? "" : activeToolName,
                        accumulated,
                        activeSignature,
                        activeProtocol);
                ToolCallBlock block = ToolCallBlock.of(value);
                completedBlocks.add(block);
                stream.emit(AiStreamEvent.builder(AiStreamEventType.TOOLCALL_END)
                        .contentIndex(index)
                        .callId(value.getCallId())
                        .toolName(value.getName())
                        .toolCall(block)
                        .partial(snapshotPartial())
                        .build());
            }
            activeCallId = null;
            activeToolName = null;
            activeSlotKey = null;
            activeSignature = null;
            activeProtocol = null;
        }

        public synchronized Message snapshotPartial() {
            List<Block> blocks = new ArrayList<Block>(completedBlocks);
            String current = activeBuffer.toString();
            if (activeKind == ActiveKind.THINKING) {
                blocks.add(ReasoningBlock.of(current, activeSignature, activeProtocol));
            } else if (activeKind == ActiveKind.TEXT) {
                Map<String, Object> protocolData = null;
                if (activeSignature != null && activeProtocol == Protocol.GEMINI) {
                    protocolData = new LinkedHashMap<String, Object>();
                    protocolData.put("_protocol", "GEMINI");
                    protocolData.put("thoughtSignature", activeSignature);
                }
                blocks.add(TextBlock.of(current, null, null, protocolData));
            } else if (activeKind == ActiveKind.TOOLCALL) {
                blocks.add(ToolCallBlock.of(ToolCallValue.of(
                        activeCallId == null ? ("call_" + activeIndex) : activeCallId,
                        activeToolName == null ? "" : activeToolName,
                        current,
                        activeSignature,
                        activeProtocol)));
            }
            return Message.assistant().blocks(blocks).build();
        }

        private void setSignature(String sig, Protocol protocol) {
            if (sig == null || sig.isEmpty()) {
                return;
            }
            if (protocol != null) {
                activeProtocol = protocol;
            }
            activeSignature = sig;
        }

        private void appendSignature(String sig, Protocol protocol) {
            if (sig == null || sig.isEmpty()) {
                return;
            }
            if (activeProtocol == null && protocol != null) {
                activeProtocol = protocol;
            }
            activeSignature = activeSignature == null ? sig : (activeSignature + sig);
        }

        private static Protocol protocolFromMap(Map<String, Object> protocolData) {
            if (protocolData == null || protocolData.isEmpty()) {
                return null;
            }
            Object raw = protocolData.get("_protocol");
            if (raw instanceof Protocol) {
                return (Protocol) raw;
            }
            if (raw instanceof String) {
                try {
                    return Protocol.valueOf((String) raw);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static String slotKeyOf(ModelEvent event) {
            if (event.getOutputIndex() != null) {
                return "out:" + event.getOutputIndex();
            }
            if (event.getContentIndex() != null) {
                return "cnt:" + event.getContentIndex();
            }
            return event.getItemId();
        }
    }
}
