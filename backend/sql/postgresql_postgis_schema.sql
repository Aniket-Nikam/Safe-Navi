CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE dataset_versions (
    id bigserial PRIMARY KEY,
    name text NOT NULL,
    version text NOT NULL,
    synthetic boolean NOT NULL DEFAULT true,
    manifest jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (name, version)
);

CREATE TABLE road_segment_risk (
    dataset_version_id bigint NOT NULL REFERENCES dataset_versions(id),
    segment_uid text NOT NULL,
    osm_way_id bigint NOT NULL,
    jurisdiction text NOT NULL,
    street_name text NOT NULL,
    area_name text NOT NULL,
    road_class text NOT NULL,
    time_period text NOT NULL CHECK (time_period IN ('morning_peak','midday','evening_peak','night')),
    traffic_congestion_index double precision NOT NULL CHECK (traffic_congestion_index BETWEEN 0 AND 100),
    crime_risk_index double precision NOT NULL CHECK (crime_risk_index BETWEEN 0 AND 100),
    lighting_quality_index double precision NOT NULL CHECK (lighting_quality_index BETWEEN 0 AND 100),
    population_density_index double precision NOT NULL CHECK (population_density_index BETWEEN 0 AND 100),
    road_condition_index double precision NOT NULL CHECK (road_condition_index BETWEEN 0 AND 100),
    pedestrian_activity_index double precision NOT NULL CHECK (pedestrian_activity_index BETWEEN 0 AND 100),
    emergency_access_index double precision NOT NULL CHECK (emergency_access_index BETWEEN 0 AND 100),
    flood_risk_index double precision NOT NULL CHECK (flood_risk_index BETWEEN 0 AND 100),
    isolation_index double precision NOT NULL CHECK (isolation_index BETWEEN 0 AND 100),
    public_transport_access_index double precision NOT NULL CHECK (public_transport_access_index BETWEEN 0 AND 100),
    synthetic_safety_risk_score double precision NOT NULL CHECK (synthetic_safety_risk_score BETWEEN 0 AND 100),
    synthetic_risk_label text NOT NULL,
    ml_split text NOT NULL CHECK (ml_split IN ('train','validation','test')),
    geometry geometry(LineString, 4326) NOT NULL,
    PRIMARY KEY (dataset_version_id, segment_uid, time_period)
);

CREATE INDEX road_segment_risk_geometry_gix ON road_segment_risk USING gist (geometry);
CREATE INDEX road_segment_risk_period_idx ON road_segment_risk (time_period);
CREATE INDEX road_segment_risk_way_idx ON road_segment_risk (osm_way_id);
