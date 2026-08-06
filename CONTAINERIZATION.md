# Containerization Backend Spring Boot

File container tersedia di folder ini:

- `Dockerfile` untuk build image backend Spring Boot.
- `.dockerignore` untuk mengecilkan build context.
- `docker-compose.yml` untuk menjalankan backend dan PostgreSQL bersama.

## Menjalankan dengan Docker Compose

```bash
cd backend-springboot
docker compose up --build
```

Backend akan tersedia di:

```text
http://localhost:8080
```

PostgreSQL akan tersedia di:

```text
localhost:5432
database: sewa_mobil_db
username: db_sewa
password: Password123!
```

## Build Image Saja

```bash
cd backend-springboot
docker build -t sewa-mobil-backend:latest .
```

## Jalankan Image dengan Database Eksternal

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/sewa_mobil_db \
  -e SPRING_DATASOURCE_USERNAME=db_sewa \
  -e SPRING_DATASOURCE_PASSWORD=Password123! \
  sewa-mobil-backend:latest
```
