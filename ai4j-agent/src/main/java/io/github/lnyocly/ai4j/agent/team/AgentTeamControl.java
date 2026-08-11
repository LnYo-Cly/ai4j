package io.github.lnyocly.ai4j.agent.team;

import java.util.List;

public interface AgentTeamControl {

    void registerMember(AgentTeamMember member);

    boolean unregisterMember(String memberId);

    List<AgentTeamMember> listMembers();

    List<AgentTeamMessage> listMessages();

    List<AgentTeamMessage> listMessagesFor(String memberId, int limit);

    /**
     * Returns messages addressed to {@code memberId} that arrived since its last read, and
     * advances that member's read cursor. This is the reactive-receive path behind the
     * {@code team_read_messages} tool.
     */
    List<AgentTeamMessage> readUnreadMessages(String memberId);

    void publishMessage(AgentTeamMessage message);

    void sendMessage(String fromMemberId, String toMemberId, String type, String taskId, String content);

    void broadcastMessage(String fromMemberId, String type, String taskId, String content);

    List<AgentTeamTaskState> listTaskStates();

    boolean claimTask(String taskId, String memberId);

    boolean releaseTask(String taskId, String memberId, String reason);

    boolean reassignTask(String taskId, String fromMemberId, String toMemberId);

    boolean heartbeatTask(String taskId, String memberId);
}
