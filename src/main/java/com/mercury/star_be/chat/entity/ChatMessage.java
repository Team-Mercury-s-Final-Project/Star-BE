package com.mercury.star_be.chat.entity;

import com.mercury.star_be.user.entity.User;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**채팅메시지*/
@Entity
@Getter
@NoArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(length = 500)
    private String content;
    private int unreadCount;
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User chatSender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id")
    private User chatReceiver;

    @ManyToOne(fetch = FetchType.LAZY)
    private ChatRoom chatRoom;

    @OneToMany(mappedBy = "chatMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessageFile> chatMessageFiles;

    @OneToMany(mappedBy = "chatMessage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatRead> chatReads;

    @Builder
    public ChatMessage(
            String content,
            int unreadCount,
            LocalDateTime createdAt,
            User chatSender,
            User chatReceiver,
            ChatRoom chatRoom,
            List<ChatMessageFile> chatMessageFiles
    ) {
        this.content = content;
        this.unreadCount = unreadCount;
        this.createdAt = createdAt;
        this.chatSender = chatSender;
        this.chatReceiver = chatReceiver;
        this.chatRoom = chatRoom;
        this.chatMessageFiles = chatMessageFiles != null ? chatMessageFiles : List.of();
    }

    public void updateFiles(List<ChatMessageFile> chatMessageFiles) {
        this.chatMessageFiles = chatMessageFiles;
    }

    public void updateReceiver(User chatReceiver) {
        this.chatReceiver = chatReceiver;
    }

    public void updateUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public void fileUploadContentString(String fileUrl){
        this.content = fileUrl;
    }
}
