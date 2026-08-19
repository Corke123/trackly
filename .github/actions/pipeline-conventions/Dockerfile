FROM python:3.13-alpine3.22

COPY requirements.txt /requirements.txt
RUN pip install --no-cache-dir -r /requirements.txt

COPY check.py /check.py

ENTRYPOINT ["python", "/check.py"]
