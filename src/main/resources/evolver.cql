CREATE TABLE evolver_log (
    key text,
    version text,
    time timestamp,
    PRIMARY KEY (key, version)
)
WITH CLUSTERING ORDER BY (version desc);