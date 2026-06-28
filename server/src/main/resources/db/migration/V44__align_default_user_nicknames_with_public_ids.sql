UPDATE users
SET nickname = CONCAT('用户', public_id)
WHERE role = 'USER'
  AND public_id REGEXP '^[0-9]{5}$'
  AND (
      nickname IS NULL
      OR nickname = ''
      OR nickname = 'MeetFun User'
      OR nickname REGEXP '^用户[0-9]{5}$'
  );
