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

-- 전국 신호등 표준 데이터 (data.go.kr 15028198)
CREATE TABLE IF NOT EXISTS traffic_lights (
    id                        BIGSERIAL    PRIMARY KEY,
    province_name             VARCHAR(50),           -- 시도명
    city_district_name        VARCHAR(50),           -- 시군구명
    road_type                 SMALLINT,              -- 도로종류 (코드)
    road_route_number         VARCHAR(20),           -- 도로노선번호
    road_route_name           VARCHAR(100),          -- 도로노선명
    road_route_direction      SMALLINT,              -- 도로노선방향 (코드)
    road_name_address         VARCHAR(200),          -- 소재지도로명주소
    lot_number_address        VARCHAR(200),          -- 소재지지번주소
    latitude                  DECIMAL(12, 8),        -- 위도
    longitude                 DECIMAL(12, 8),        -- 경도
    location                  GEOGRAPHY(POINT, 4326),-- PostGIS 공간 컬럼 (위도·경도 파생)
    installation_method       SMALLINT,              -- 신호기설치방식 (코드)
    road_shape                SMALLINT,              -- 도로형태 (코드)
    is_main_road              BOOLEAN,               -- 주도로여부 (Y/N)
    management_number         VARCHAR(50),           -- 신호등관리번호
    traffic_light_type        SMALLINT,              -- 신호등구분 (코드)
    light_color_type          SMALLINT,              -- 신호등색종류 (코드)
    lighting_method           SMALLINT,              -- 신호등화방식 (코드)
    lighting_sequence         VARCHAR(100),          -- 신호등화순서 (황색 등 텍스트)
    lighting_duration         SMALLINT,              -- 신호등화시간
    light_source_type         SMALLINT,              -- 광원종류 (코드)
    signal_control_method     SMALLINT,              -- 신호제어방식 (코드)
    signal_time_method        SMALLINT,              -- 신호시간결정방식 (코드)
    is_flashing_operated      BOOLEAN,               -- 점멸등운영여부 (Y/N)
    flashing_start_time       TIME,                  -- 점멸등운영시작시각
    flashing_end_time         TIME,                  -- 점멸등운영종료시각
    has_pedestrian_actuated   BOOLEAN,               -- 보행자작동신호기유무 (Y/N)
    has_countdown_display     BOOLEAN,               -- 잔여시간표시기유무 (Y/N)
    has_visual_impaired_sound BOOLEAN,               -- 시각장애인용음향신호기유무 (Y/N)
    road_sign_serial_number   VARCHAR(50),           -- 도로안내표지일련번호
    management_agency_name    VARCHAR(100),          -- 관리기관명
    management_agency_phone   VARCHAR(30),           -- 관리기관전화번호
    data_reference_date       DATE,                  -- 데이터기준일자
    facing_direction          DECIMAL(5, 2)          -- 신호등 바라보는 방향 (북=0, 동=90, 남=180, 서=270) Android azimuth 기준 degree
);

CREATE INDEX IF NOT EXISTS idx_traffic_lights_location  ON traffic_lights USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_traffic_lights_province  ON traffic_lights (province_name);
CREATE INDEX IF NOT EXISTS idx_traffic_lights_city      ON traffic_lights (city_district_name);
CREATE INDEX IF NOT EXISTS idx_traffic_lights_mgmt_no   ON traffic_lights (management_number);

-- 교차로 맵 정보 (crsrd_map_info API 응답)
CREATE TABLE IF NOT EXISTS intersection_maps (
    id               BIGSERIAL    PRIMARY KEY,
    stdg_cd          VARCHAR(20),                    -- 지자체코드
    lclgv_nm         VARCHAR(100),                   -- 지방자치단체명
    crsrd_id         VARCHAR(20),                    -- 교차로아이디
    crsrd_nm         VARCHAR(100),                   -- 교차로명
    map_ctpt_int_lat DECIMAL(14, 10),                -- Map중심점위도
    map_ctpt_int_lot DECIMAL(14, 10),                -- Map중심점경도
    location         GEOGRAPHY(POINT, 4326),         -- PostGIS 공간 컬럼 (위경도 파생)
    lane_wdth        INTEGER,                        -- 차로폭
    lmt_spd_type_nm  VARCHAR(100),                   -- 제한속도유형명
    lmt_spd          INTEGER,                        -- 제한속도
    crsrd_eng_nm     VARCHAR(100),                   -- 교차로영문명
    reg_id           VARCHAR(50),                    -- 등록자아이디
    reg_dt           TIMESTAMP,                      -- 등록일시
    tot_dt           TIMESTAMP                       -- 집계일시
);

CREATE INDEX IF NOT EXISTS idx_intersection_maps_location  ON intersection_maps USING GIST (location);
CREATE INDEX IF NOT EXISTS idx_intersection_maps_crsrd_id  ON intersection_maps (crsrd_id);
CREATE INDEX IF NOT EXISTS idx_intersection_maps_stdg_cd   ON intersection_maps (stdg_cd);

-- 신호제어기 보행자 신호잔여시간 정보 (tl_drct_info API 응답 - 보행신호만)
CREATE TABLE IF NOT EXISTS pedestrian_signals (
    id               BIGSERIAL    PRIMARY KEY,
    tot_dt           TIMESTAMP,                      -- 집계일시
    stdg_cd          VARCHAR(20),                    -- 지자체코드
    lclgv_nm         VARCHAR(100),                   -- 지방자치단체명
    crsrd_id         VARCHAR(20),                    -- 교차로아이디
    reg_id           VARCHAR(50),                    -- 등록자아이디
    reg_dt           DATE,                           -- 등록일자
    nt_pdsg_rmnd_cs  INTEGER,                        -- 북쪽_보행신호_잔여_센티초
    nt_pdsg_stts_nm  VARCHAR(100),                   -- 북쪽_보행신호_상태명
    et_pdsg_rmnd_cs  INTEGER,                        -- 동쪽_보행신호_잔여_센티초
    et_pdsg_stts_nm  VARCHAR(100),                   -- 동쪽_보행신호_상태명
    st_pdsg_rmnd_cs  INTEGER,                        -- 남쪽_보행신호_잔여_센티초
    st_pdsg_stts_nm  VARCHAR(100),                   -- 남쪽_보행신호_상태명
    wt_pdsg_rmnd_cs  INTEGER,                        -- 서쪽_보행신호_잔여_센티초
    wt_pdsg_stts_nm  VARCHAR(100)                    -- 서쪽_보행신호_상태명
);

CREATE INDEX IF NOT EXISTS idx_pedestrian_signals_crsrd_id ON pedestrian_signals (crsrd_id);
CREATE INDEX IF NOT EXISTS idx_pedestrian_signals_tot_dt   ON pedestrian_signals (tot_dt DESC);
