ALTER TABLE members ADD COLUMN IF NOT EXISTS google_subject VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS members_google_subject_unique
  ON members (google_subject)
  WHERE google_subject IS NOT NULL;
