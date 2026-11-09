package com.line4.idea2ip.domain.user.repository;

import com.line4.idea2ip.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByLoginId(String loginId);
}
