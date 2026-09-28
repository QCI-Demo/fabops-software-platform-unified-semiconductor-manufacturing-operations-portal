"""Pytest configuration and shared fixtures."""

import sys
from pathlib import Path

# Add source directory to path for imports
src_path = Path(__file__).parent.parent / "src" / "main" / "python"
sys.path.insert(0, str(src_path))
