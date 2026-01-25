package com.learning.platformservice.tenant.repo;

import com.learning.platformservice.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

}
