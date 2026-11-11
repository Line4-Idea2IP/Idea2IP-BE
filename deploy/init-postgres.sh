#!/usr/bin/env bash

psql --set=ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_password="$IDEA2IP_DB_PASSWORD" <<'SQL'
CREATE ROLE idea2ip_app LOGIN PASSWORD :'app_password';
ALTER DATABASE idea2ip OWNER TO idea2ip_app;
SQL
