CREATE TABLE IF NOT EXISTS tags (
  id BIGINT NOT NULL COMMENT 'Stable tag id',
  name VARCHAR(40) NOT NULL COMMENT 'Tag display name',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'Whether this tag can be selected',
  sort_order INT NOT NULL DEFAULT 0 COMMENT 'Display order',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tags_name (name),
  KEY idx_tags_enabled_sort (enabled, sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Activity tag definitions';

INSERT INTO tags (id, name, enabled, sort_order) VALUES
(1, '摄影', 1, 10),
(2, '运动', 1, 20),
(3, '学习', 1, 30),
(4, '美食', 1, 40),
(5, '旅行', 1, 50),
(6, '社交', 1, 60),
(7, '音乐', 1, 70),
(8, '健身', 1, 80),
(9, '露营', 1, 90),
(10, '桌游', 1, 100),
(11, '电影', 1, 110),
(12, '跑步', 1, 120)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  enabled = VALUES(enabled),
  sort_order = VALUES(sort_order);
