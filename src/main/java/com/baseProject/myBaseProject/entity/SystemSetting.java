package com.baseProject.myBaseProject.entity;

import com.baseProject.myBaseProject.enums.SystemSettingType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
public class SystemSetting {
    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, updatable = false, length = 20)
    private SystemSettingType type;

    @Column(nullable = false, updatable = false, length = 500)
    private String description;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserAccount updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
