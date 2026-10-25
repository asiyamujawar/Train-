package com.trainconcierge.repository;

import com.trainconcierge.entity.Passenger;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    Optional<Passenger> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    @Query("""
            select p from Passenger p
            where (:fullName is null or lower(p.fullName) like lower(concat('%', :fullName, '%')))
              and (:email is null or lower(p.email) like lower(concat('%', :email, '%')))
              and (:active is null or p.active = :active)
            """)
    Page<Passenger> search(@Param("fullName") String fullName,
                           @Param("email") String email,
                           @Param("active") Boolean active,
                           Pageable pageable);
}
