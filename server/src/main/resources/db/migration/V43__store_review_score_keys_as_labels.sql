UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"组织安排"', '"label":"organization"')
WHERE scores_json LIKE '%"label":"组织安排"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"活动体验"', '"label":"experience"')
WHERE scores_json LIKE '%"label":"活动体验"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"现场氛围"', '"label":"atmosphere"')
WHERE scores_json LIKE '%"label":"现场氛围"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"内容匹配"', '"label":"match"')
WHERE scores_json LIKE '%"label":"内容匹配"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"准时守约"', '"label":"punctual"')
WHERE scores_json LIKE '%"label":"准时守约"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"沟通配合"', '"label":"communication"')
WHERE scores_json LIKE '%"label":"沟通配合"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"友善礼貌"', '"label":"friendly"')
WHERE scores_json LIKE '%"label":"友善礼貌"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Organization"', '"label":"organization"')
WHERE scores_json LIKE '%"label":"Organization"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Experience"', '"label":"experience"')
WHERE scores_json LIKE '%"label":"Experience"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Atmosphere"', '"label":"atmosphere"')
WHERE scores_json LIKE '%"label":"Atmosphere"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Match"', '"label":"match"')
WHERE scores_json LIKE '%"label":"Match"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Punctuality"', '"label":"punctual"')
WHERE scores_json LIKE '%"label":"Punctuality"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Communication"', '"label":"communication"')
WHERE scores_json LIKE '%"label":"Communication"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Friendliness"', '"label":"friendly"')
WHERE scores_json LIKE '%"label":"Friendliness"%';
