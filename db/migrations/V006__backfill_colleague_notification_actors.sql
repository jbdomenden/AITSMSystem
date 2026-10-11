-- Link legacy colleague notifications to the employee responsible for the event.
-- Only rows with a colleague request created within five minutes are changed;
-- unmatched legacy notifications remain readable and retain their safe fallback.
WITH candidates AS (
    SELECT
        n.id AS notification_id,
        CASE
            WHEN n.title = 'New colleague' THEN cr.sender_id
            WHEN n.title = 'Colleague request accepted' THEN cr.recipient_id
        END AS actor_id,
        u.full_name AS actor_name,
        CASE
            WHEN n.title = 'New colleague' THEN 'colleague_added'
            WHEN n.title = 'Colleague request accepted' THEN 'colleague_accepted'
        END AS corrected_type,
        ROW_NUMBER() OVER (
            PARTITION BY n.id
            ORDER BY ABS(EXTRACT(EPOCH FROM (n.created_at - cr.created_at)))
        ) AS match_rank
    FROM notifications n
    JOIN colleague_requests cr ON
        (n.title = 'New colleague' AND cr.recipient_id = n.user_id) OR
        (n.title = 'Colleague request accepted' AND cr.sender_id = n.user_id)
    JOIN users u ON u.id = CASE
        WHEN n.title = 'New colleague' THEN cr.sender_id
        WHEN n.title = 'Colleague request accepted' THEN cr.recipient_id
    END
    WHERE n.type = 'colleague_request'
      AND n.related_user_id IS NULL
      AND n.title IN ('New colleague', 'Colleague request accepted')
      AND ABS(EXTRACT(EPOCH FROM (n.created_at - cr.created_at))) <= 300
), matched AS (
    SELECT notification_id, actor_id, actor_name, corrected_type
    FROM candidates
    WHERE match_rank = 1
)
UPDATE notifications n
SET related_user_id = matched.actor_id,
    type = matched.corrected_type,
    message = CASE
        WHEN matched.corrected_type = 'colleague_added' THEN 'You were added by: ' || matched.actor_name
        ELSE matched.actor_name || ' accepted your colleague request.'
    END
FROM matched
WHERE n.id = matched.notification_id;
