UPDATE users
SET cover_url = NULL
WHERE cover_url LIKE 'https://dummyimage.com/%'
   OR cover_url LIKE '/uploads/cover/%';

