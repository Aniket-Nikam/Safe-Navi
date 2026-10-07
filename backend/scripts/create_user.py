"""Create a privileged Safe-Navi account using an interactive password prompt."""

from __future__ import annotations

import argparse
from getpass import getpass
from pathlib import Path
import sys


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.product_database import create_user  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Create a Safe-Navi government or admin account")
    parser.add_argument("--name", required=True)
    parser.add_argument("--email", required=True)
    parser.add_argument("--role", required=True, choices=["government", "admin"])
    args = parser.parse_args()
    password = getpass("Password (minimum 10 characters): ")
    confirmation = getpass("Confirm password: ")
    if password != confirmation:
        raise SystemExit("Passwords do not match")
    user = create_user(args.name, args.email, password, args.role)
    print(f"Created {user['role']} account {user['email']} ({user['id']})")


if __name__ == "__main__":
    main()
