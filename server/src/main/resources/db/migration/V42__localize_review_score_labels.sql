UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Organization"', '"label":"组织安排"')
WHERE scores_json LIKE '%"label":"Organization"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Experience"', '"label":"活动体验"')
WHERE scores_json LIKE '%"label":"Experience"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Atmosphere"', '"label":"现场氛围"')
WHERE scores_json LIKE '%"label":"Atmosphere"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Match"', '"label":"内容匹配"')
WHERE scores_json LIKE '%"label":"Match"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Punctuality"', '"label":"准时守约"')
WHERE scores_json LIKE '%"label":"Punctuality"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Communication"', '"label":"沟通配合"')
WHERE scores_json LIKE '%"label":"Communication"%';

UPDATE activity_reviews
SET scores_json = REPLACE(scores_json, '"label":"Friendliness"', '"label":"友善礼貌"')
WHERE scores_json LIKE '%"label":"Friendliness"%';
