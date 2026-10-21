from app import product_database as db
from app import news_intelligence as news


def signals(count):
    return [dict(headline=f"Test streetlight report {i}", summary="Offline fixture only",
                 publisher="example.test", source_url=f"https://example.test/{i}",
                 published_at="", category="CIVIC", confidence=.57) for i in range(count)]


def test_batch_deduplicates_across_chunks_without_reopening_connections(monkeypatch):
    original = db.connect_product
    connections = []
    def connect():
        connections.append(True)
        return original()
    monkeypatch.setattr(db, "connect_product", connect)
    items = signals(100)
    assert db.store_news_signals(items + [items[0], items[75]]) == 100
    assert len(connections) == 1
    assert db.store_news_signals(items) == 0
    assert len(connections) == 2
    with original() as connection:
        assert connection.execute("SELECT COUNT(*) FROM news_signals WHERE review_status='UNVERIFIED'").fetchone()[0] == 100
    assert db.store_news_signals([]) == 0
    assert len(connections) == 2


def test_ingestion_uses_batch_and_preserves_refresh_and_dedup(monkeypatch):
    monkeypatch.setenv("SAFE_NAVI_NEWS_FEEDS", "https://example.test/feed")
    class Response:
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def read(self, limit): return b"<rss><channel/></rss>"
    monkeypatch.setattr(news, "urlopen", lambda *args, **kwargs: Response())
    monkeypatch.setattr(news, "parse_feed", lambda *args: signals(100))
    first = news.ingest_configured_feeds(force=False)
    assert first["inserted"] == 100 and not first["errors"]
    assert first["last_ingested_at"] and first["next_ingestion_at"]
    assert news.ingest_configured_feeds(force=False)["skipped"] == "not_due"
    repeat = news.ingest_configured_feeds(force=True)
    assert repeat["inserted"] == 0 and repeat["deduplicated"] == 100
