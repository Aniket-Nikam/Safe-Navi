"""PostgreSQL boundary for the existing product repository; risk cells stay immutable SQLite.

The small compatibility surface is intentionally limited to SQL used by product_database.
Rows retain sqlite3.Row's keyed AND positional access, including dict(cursor) for key/value pairs.
"""
from __future__ import annotations

import os
import re
import sqlite3
from urllib.parse import urlsplit


PRODUCT_LOCK = 831920147


class ProductDatabaseUnavailable(RuntimeError):
    pass


def database_url() -> str:
    return os.getenv("SAFE_NAVI_DATABASE_URL", "").strip()


def schema_name() -> str:
    value = os.getenv("SAFE_NAVI_DATABASE_SCHEMA", "safenavi").strip()
    if not re.fullmatch(r"safenavi(?:_[a-z0-9_]{1,42})?", value):
        raise ValueError("Use a dedicated private Safe-Navi database schema")
    return value


def translate_sql(sql: str) -> str:
    """Translate this repository's static qmark SQL, never interpolate parameter values."""
    literals = []
    def hide_literal(match):
        literals.append(match.group(0))
        return f"__safenavi_literal_{len(literals) - 1}__"
    sql = re.sub(r"'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"", hide_literal, sql)
    sql = re.sub(r"\bemail\s*=\s*\?\s+COLLATE\s+NOCASE", "LOWER(email)=LOWER(?)", sql, flags=re.I)
    sql = re.sub(r"\s+COLLATE\s+NOCASE\b", "", sql, flags=re.I)
    sql = re.sub(r"\bLIKE\b", "ILIKE", sql, flags=re.I)
    sql = re.sub(r"\bREAL\b", "DOUBLE PRECISION", sql, flags=re.I)
    sql = re.sub(r"\bCURRENT_TIMESTAMP\b", "CAST(CURRENT_TIMESTAMP AS TEXT)", sql, flags=re.I)
    ignore = bool(re.match(r"\s*INSERT\s+OR\s+IGNORE\b", sql, re.I))
    if ignore:
        sql = re.sub(r"INSERT\s+OR\s+IGNORE", "INSERT", sql, count=1, flags=re.I)
        sql = sql.rstrip().rstrip(";") + " ON CONFLICT DO NOTHING"
    sql = sql.replace("?", "%s")
    return re.sub(r"__safenavi_literal_(\d+)__", lambda match: literals[int(match.group(1))], sql)


class ProductRow:
    def __init__(self, names, values):
        self._names = tuple(names)
        self._values = tuple(int(value) if isinstance(value, bool) else value for value in values)
        self._mapping = dict(zip(self._names, self._values))

    def keys(self):
        return self._names

    def __getitem__(self, key):
        return self._values[key] if isinstance(key, (int, slice)) else self._mapping[key]

    def __iter__(self):
        return iter(self._values)

    def __len__(self):
        return len(self._values)


class ProductCursor:
    def __init__(self, cursor):
        self.cursor = cursor
        self.names = [column.name for column in cursor.description] if cursor.description else []

    @property
    def rowcount(self):
        return self.cursor.rowcount

    def fetchone(self):
        row = self.cursor.fetchone()
        return ProductRow(self.names, row) if row is not None else None

    def fetchall(self):
        return [ProductRow(self.names, row) for row in self.cursor.fetchall()]

    def __iter__(self):
        return (ProductRow(self.names, row) for row in self.cursor)


class PostgresProductConnection:
    dialect = "postgresql"

    def __init__(self, *, bootstrap=False):
        import psycopg
        url = database_url()
        parsed = urlsplit(url)
        if parsed.scheme not in {"postgres", "postgresql"} or not parsed.hostname:
            raise ValueError("Configure a PostgreSQL connection URI")
        # Public cloud connections are encrypted even if an unsafe URL says sslmode=disable.
        options = {} if parsed.hostname in {"localhost", "127.0.0.1", "::1"} else {"sslmode": "require"}
        try:
            self.connection = psycopg.connect(url, connect_timeout=8, prepare_threshold=None, **options)
        except psycopg.OperationalError as error:
            raise ProductDatabaseUnavailable("Persistent database is unavailable; check the cloud project's status") from error
        self.bootstrap = bootstrap

    def __enter__(self):
        self.connection.__enter__()
        try:
            self.connection.execute("SET LOCAL statement_timeout='30s'")
            self.connection.execute("SET LOCAL lock_timeout='10s'")
            # Conservative single-writer-equivalent transactions preserve repository read/modify/write
            # semantics across pooled connections. Suitable for the small, single-instance deployment.
            self.connection.execute("SELECT pg_advisory_xact_lock(%s)", (PRODUCT_LOCK,))
            schema = schema_name()
            if self.bootstrap:
                self.connection.execute(f'CREATE SCHEMA IF NOT EXISTS "{schema}"')
                self.connection.execute(f'REVOKE ALL ON SCHEMA "{schema}" FROM PUBLIC')
            self.connection.execute("SELECT set_config('search_path', %s, true)", (f'"{schema}",pg_catalog',))
            return self
        except BaseException as error:
            self.connection.__exit__(type(error), error, error.__traceback__)
            raise

    def __exit__(self, kind, value, traceback):
        return self.connection.__exit__(kind, value, traceback)

    def execute(self, sql, parameters=()):
        import psycopg
        if sql.strip().upper() == "BEGIN IMMEDIATE":
            # The transaction already holds PRODUCT_LOCK.
            sql, parameters = "SELECT 1", ()
        try:
            # No params argument for DDL: literal percent signs remain literal SQL.
            cursor = self.connection.execute(translate_sql(sql), parameters if parameters else None)
            return ProductCursor(cursor)
        except psycopg.IntegrityError as error:
            raise sqlite3.IntegrityError("Product data violates a uniqueness or relationship constraint") from error
        except psycopg.OperationalError as error:
            raise ProductDatabaseUnavailable("Persistent database request could not complete; please retry") from error

    def executescript(self, sql):
        # Shared product DDL contains no procedural SQL or semicolons inside literals.
        for statement in sql.split(";"):
            if statement.strip():
                self.execute(statement)

    def ensure_column(self, table, definition):
        if not re.fullmatch(r"[a-z_]+", table):
            raise ValueError("Invalid product table")
        self.execute(f'ALTER TABLE "{table}" ADD COLUMN IF NOT EXISTS {definition}')

    def protect_schema(self):
        schema = schema_name()
        self.execute("CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_idx ON users(LOWER(email))")
        # Supabase's public API roles must not read password hashes, sessions, reports or contacts.
        for role in ("anon", "authenticated"):
            exists = self.connection.execute("SELECT 1 FROM pg_roles WHERE rolname=%s", (role,)).fetchone()
            if exists:
                self.connection.execute(f'REVOKE ALL ON SCHEMA "{schema}" FROM "{role}"')
                self.connection.execute(f'REVOKE ALL ON ALL TABLES IN SCHEMA "{schema}" FROM "{role}"')
        self.connection.execute(f'REVOKE ALL ON ALL TABLES IN SCHEMA "{schema}" FROM PUBLIC')
