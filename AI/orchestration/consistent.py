"""A single repeatable-read view for multi-query exports and quality checks."""

from contextlib import contextmanager

from AI.orchestration.series_store import SeriesStore


@contextmanager
def consistent_read(store):
    conn = store.connection_factory()
    try:
        conn.set_session(isolation_level="REPEATABLE READ", readonly=True)
        with conn, conn.cursor() as cursor:
            cursor.execute("SET LOCAL TIME ZONE 'UTC'")

            class BoundStore(SeriesStore):
                @contextmanager
                def transaction(self):
                    yield cursor

            yield BoundStore(connection_factory=store.connection_factory)
    finally:
        conn.close()
