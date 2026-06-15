package com.internal.tasktracker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    // BUG FIX: Added parentheses around the OR clause.
    // Original query: archived=FALSE AND title LIKE term OR description LIKE term AND status filter
    // Due to SQL operator precedence (AND binds before OR), this was incorrectly parsed as:
    //   (archived=FALSE AND title LIKE term) OR (description LIKE term AND status filter)
    // This caused archived tasks to leak into results and status filter to be ignored for title matches.
    @Query(value = "SELECT * FROM tasks "
                 + "WHERE archived = FALSE "
                 + "AND (LOWER(title) LIKE :term OR LOWER(description) LIKE :term) "
                 + "AND (:status IS NULL OR status = :status) "
                 + "ORDER BY created_at DESC",
           nativeQuery = true)
    List<Task> searchTasks(@Param("term") String term, @Param("status") String status);
}
