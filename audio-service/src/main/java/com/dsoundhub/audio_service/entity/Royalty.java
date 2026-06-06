package com.dsoundhub.audio_service.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "royalties")
public class Royalty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @Column(name = "artist_id", nullable = false)
    private UUID artistId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoyaltyStatus status;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) status = RoyaltyStatus.PENDING;
    }

    // Getters & Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Transaction getTransaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }
    public UUID getArtistId() { return artistId; }
    public void setArtistId(UUID artistId) { this.artistId = artistId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public RoyaltyStatus getStatus() { return status; }
    public void setStatus(RoyaltyStatus status) { this.status = status; }
    public LocalDateTime getSettledAt() { return settledAt; }
    public void setSettledAt(LocalDateTime settledAt) { this.settledAt = settledAt; }
}