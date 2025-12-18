package com.footstone.audit.service.core.storage.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "checker_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckerConfigEntity {
    @Id
    private String checkerId;
    
    private boolean enabled;
}
