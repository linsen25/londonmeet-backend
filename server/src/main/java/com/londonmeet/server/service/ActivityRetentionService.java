package com.londonmeet.server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActivityRetentionService {

    private final JdbcTemplate jdbcTemplate;

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void deleteExpiredActivities() {
        Integer retentionDays = jdbcTemplate.queryForObject(
                "SELECT CAST(setting_value AS UNSIGNED) FROM system_settings WHERE setting_key = ?",
                Integer.class, "activity_detail_retention_days");
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays == null ? 30 : retentionDays);
        List<Map<String, Object>> activities = jdbcTemplate.queryForList("""
                SELECT a.id
                FROM activities a
                WHERE a.end_at < ?
                ORDER BY a.id
                LIMIT 500
                """, cutoff);
        for (Map<String, Object> activity : activities) {
            long id = ((Number) activity.get("id")).longValue();
            jdbcTemplate.update("DELETE FROM activity_reports WHERE activity_id = ?", id);
            jdbcTemplate.update("DELETE FROM activity_favorites WHERE activity_id = ?", id);
            jdbcTemplate.update("DELETE FROM activity_reviews WHERE activity_id = ?", id);
            jdbcTemplate.update("DELETE FROM activity_registrations WHERE activity_id = ?", id);
            jdbcTemplate.update("DELETE FROM activities WHERE id = ?", id);
        }
    }
}
