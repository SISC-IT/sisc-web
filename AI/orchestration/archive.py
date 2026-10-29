"""Content addressed local Bronze archive; a mounted volume is required in Docker."""

import hashlib
import os
import tempfile
from pathlib import Path


class RawArchive:
    def __init__(self, root):
        self.root = Path(root).resolve()

    def put(self, payload):
        checksum = hashlib.sha256(payload).hexdigest()
        path = self.root / checksum[:2] / checksum
        path.parent.mkdir(parents=True, exist_ok=True)
        # Fully flush a temporary inode before publishing an immutable hard link.
        fd, temporary = tempfile.mkstemp(dir=path.parent)
        try:
            with os.fdopen(fd, "wb") as stream:
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            try:
                os.link(temporary, path)
            except FileExistsError:
                if hashlib.sha256(path.read_bytes()).hexdigest() != checksum:
                    raise IOError("Archive checksum mismatch")
        finally:
            os.unlink(temporary)
        return checksum, str(path)
