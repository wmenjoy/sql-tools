package com.footstone.audit.service.core.storage.repository;

import com.footstone.audit.service.core.storage.entity.CheckerConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CheckerConfigJpaRepository extends JpaRepository<CheckerConfigEntity, String> {
}
