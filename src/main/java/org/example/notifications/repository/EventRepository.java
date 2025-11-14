package org.example.notifications.repository;

import org.example.notifications.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByUrlOrderByTimestampDesc(String url);
}

