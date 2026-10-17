"""Interactive first-admin provisioning on a new deployment, never a public API."""
import getpass
import argparse
from .product_database import connect_product, create_user


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", help="Explicit private cloud configuration file; affects only this process")
    options = parser.parse_args()
    if options.env_file:
        from .environment import load_environment_file
        load_environment_file(options.env_file, override=True)
    with connect_product() as connection:
        if connection.execute("SELECT 1 FROM users WHERE role='admin' LIMIT 1").fetchone():
            raise SystemExit("An administrator already exists. Use the existing account; no changes made.")
    name = input("Administrator display name: ").strip()
    email = input("Administrator email: ").strip()
    password = getpass.getpass("New password (12+ characters): ")
    if not name or "@" not in email or len(password) < 12:
        raise SystemExit("Valid name/email and a password of at least 12 characters are required.")
    if password != getpass.getpass("Confirm password: "):
        raise SystemExit("Passwords do not match; no account created.")
    create_user(name, email, password, "admin")
    print("Administrator created. Sign in as Admin; promote a separately registered citizen to official when authorised.")


if __name__ == "__main__":
    main()
