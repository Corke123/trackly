# syntax=docker/dockerfile:1

FROM python:3.13-alpine3.22

COPY requirements.txt /requirements.txt

# --require-hashes verifies the bytes, --only-binary refuses an sdist whose setup script would run
# during the install of a tool whose whole job is to check this repository's supply-chain hygiene.
RUN pip install --no-cache-dir --require-hashes --only-binary :all: -r /requirements.txt

COPY check.py /check.py

# The runner owns the mounted workspace as UID 1001. The checker only reads it, and matching that UID
# keeps the two files it appends to — GITHUB_STEP_SUMMARY and GITHUB_OUTPUT — writable without root.
USER 1001

ENTRYPOINT ["python", "/check.py"]
