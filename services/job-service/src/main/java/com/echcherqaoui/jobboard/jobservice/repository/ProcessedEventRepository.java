package com.echcherqaoui.jobboard.jobservice.repository;


import com.echcherqaoui.jobboard.jobservice.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}