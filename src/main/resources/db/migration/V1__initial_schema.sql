CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS members (
  id BIGSERIAL PRIMARY KEY,
  member_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  phone VARCHAR(30),
  password_hash TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT member_code_format CHECK (member_code ~ '^U[0-9]{3,}$')
);

CREATE TABLE IF NOT EXISTS sessions (
  token_hash CHAR(64) PRIMARY KEY,
  member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS sessions_member_idx ON sessions(member_id);
CREATE INDEX IF NOT EXISTS sessions_expiry_idx ON sessions(expires_at);

CREATE TABLE IF NOT EXISTS rooms (
  id BIGSERIAL PRIMARY KEY,
  room_code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  floor VARCHAR(40) NOT NULL,
  capacity INTEGER NOT NULL CHECK (capacity > 0),
  features TEXT NOT NULL DEFAULT '',
  sort_order INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS reservations (
  id BIGSERIAL PRIMARY KEY,
  room_id BIGINT NOT NULL REFERENCES rooms(id),
  member_id BIGINT NOT NULL REFERENCES members(id),
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ NOT NULL,
  purpose VARCHAR(160) NOT NULL,
  people_count INTEGER NOT NULL CHECK (people_count > 0),
  status VARCHAR(20) NOT NULL DEFAULT 'approved' CHECK (status IN ('approved', 'cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  approved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT reservation_time_order CHECK (ends_at > starts_at),
  CONSTRAINT reservation_max_duration CHECK (ends_at <= starts_at + INTERVAL '4 hours')
);

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'reservations_no_overlap'
  ) THEN
    ALTER TABLE reservations
      ADD CONSTRAINT reservations_no_overlap
      EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
      ) WHERE (status = 'approved');
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS reservations_member_idx ON reservations(member_id);
CREATE INDEX IF NOT EXISTS reservations_room_time_idx ON reservations(room_id, starts_at);

CREATE TABLE IF NOT EXISTS reservation_photos (
  id BIGSERIAL PRIMARY KEY,
  reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
  member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  phase VARCHAR(10) NOT NULL CHECK (phase IN ('before', 'after')),
  mime_type VARCHAR(40) NOT NULL,
  file_size INTEGER NOT NULL CHECK (file_size > 0 AND file_size <= 5242880),
  image_data BYTEA NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (reservation_id, phase)
);

CREATE INDEX IF NOT EXISTS reservation_photos_reservation_idx ON reservation_photos(reservation_id);

INSERT INTO rooms (room_code, name, floor, capacity, features, sort_order)
VALUES
  ('201', '201호 회의실', '교육관 2층', 12, '화이트보드 · 모니터', 1),
  ('202', '202호 회의실', '교육관 2층', 12, '화상회의 · 모니터', 2)
ON CONFLICT (room_code) DO UPDATE SET
  name = EXCLUDED.name,
  floor = EXCLUDED.floor,
  capacity = EXCLUDED.capacity,
  features = EXCLUDED.features,
  sort_order = EXCLUDED.sort_order,
  active = TRUE;

UPDATE rooms SET active = FALSE WHERE room_code NOT IN ('201', '202');

CREATE OR REPLACE VIEW member_reservation_stats AS
SELECT
  m.member_code,
  m.name,
  COUNT(r.id) FILTER (WHERE r.status = 'approved') AS total_count,
  COUNT(ap.id) FILTER (WHERE r.status = 'approved') AS completed_count,
  COUNT(r.id) FILTER (WHERE r.status = 'approved' AND r.starts_at > NOW()) AS upcoming_count,
  COALESCE(
    SUM(EXTRACT(EPOCH FROM (r.ends_at - r.starts_at)) / 60)
      FILTER (WHERE r.status = 'approved' AND r.ends_at <= NOW()),
    0
  )::BIGINT AS total_minutes
FROM members m
LEFT JOIN reservations r ON r.member_id = m.id
LEFT JOIN reservation_photos ap ON ap.reservation_id = r.id AND ap.phase = 'after'
GROUP BY m.id, m.member_code, m.name;
