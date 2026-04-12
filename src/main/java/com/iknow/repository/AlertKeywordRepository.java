package com.iknow.repository;

import com.iknow.entity.AlertKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlertKeywordRepository extends JpaRepository<AlertKeyword, Long> {
    List<AlertKeyword> findByAlertIdOrderByIdAsc(Long alertId);
    List<AlertKeyword> findByAlertIdIn(List<Long> alertIds);
    List<AlertKeyword> findByAlertIdInAndKeyword(List<Long> alertIds, String keyword);
    void deleteByAlertId(Long alertId);
}
