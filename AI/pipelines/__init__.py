# AI/pipelines/__init__.py
def run_daily_pipeline(*args, **kwargs):
    # Avoid importing heavy runtime dependencies at package import time.
    from .daily_routine import run_daily_pipeline as _run_daily_pipeline

    return _run_daily_pipeline(*args, **kwargs)


__all__ = ["run_daily_pipeline"]
