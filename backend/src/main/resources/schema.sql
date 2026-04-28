CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS hazards (
    id           BIGSERIAL PRIMARY KEY,
    location     GEOGRAPHY(POINT, 4326) NOT NULL,
    type         VARCHAR(50)            NOT NULL,
    confidence   FLOAT,
    image_url    TEXT,
    description  TEXT,
    reported_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_hazards_location ON hazards USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_hazards_type     ON hazards (type);
CREATE INDEX IF NOT EXISTS idx_hazards_reported ON hazards (reported_at DESC);
