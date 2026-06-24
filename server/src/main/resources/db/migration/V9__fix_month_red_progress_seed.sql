UPDATE activities
SET start_at = DATE_SUB(end_at, INTERVAL 80 DAY)
WHERE title = 'Month Red 13'
  AND author_name = 'MeetFun';
