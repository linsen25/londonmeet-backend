UPDATE users
SET avatar_url = 'https://dummyimage.com/300x300/ffffff/111111.png&text=Avatar'
WHERE avatar_url IS NULL
   OR avatar_url = ''
   OR avatar_url = '/uploads/avatar/default-avatar.png';

UPDATE users
SET cover_url = ''
WHERE cover_url LIKE '/uploads/cover/%';

UPDATE activities
SET avatar_url = 'https://dummyimage.com/300x300/ffffff/111111.png&text=Avatar'
WHERE avatar_url IS NULL
   OR avatar_url = ''
   OR avatar_url = '/uploads/avatar/default-avatar.png';
