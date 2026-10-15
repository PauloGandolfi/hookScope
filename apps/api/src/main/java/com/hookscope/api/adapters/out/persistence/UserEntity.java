package com.hookscope.api.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user", schema = "hookscope")
public class UserEntity {
    @Id public UUID id;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, length = 320, unique = true) public String email;
    @Column(name = "password_hash", nullable = false, length = 100) public String passwordHash;
    @Column(nullable = false) public boolean enabled;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
    @Column(name = "updated_at", nullable = false) public Instant updatedAt;

    protected UserEntity() {}

    public UserEntity(String name, String email, String passwordHash) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
}
