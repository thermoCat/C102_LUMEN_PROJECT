package com.smartcane.backend.domain.pedestriansignal.repository;

import com.smartcane.backend.domain.pedestriansignal.entity.PedestrianSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedestrianSignalRepository extends JpaRepository<PedestrianSignal, Long> {

    Optional<PedestrianSignal> findTopByCrsrdIdOrderByTotDtDesc(String crsrdId);

    List<PedestrianSignal> findByStdgCd(String stdgCd);
}
