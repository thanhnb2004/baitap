# MODULE SERVER

> 📘 *Sinh viên mô tả phần **server** tại đây. Điền đầy đủ theo framework và bài toán của nhóm.*

---

## 🎯 MỤC TIÊU

Server chịu trách nhiệm:
- Tiếp nhận yêu cầu từ client
- Xử lý dữ liệu/tính toán
- Trả kết quả cho client

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ |
|------------|-----------|
| Ngôn ngữ | Python / Node.js / Java / ... |
| Framework | Flask / Express / Spring Boot / ... |
| Database | SQLite / MySQL / ... (nếu có) |

---

## 🚀 HƯỚNG DẪN CHẠY

### Cài đặt
```bash
# Ví dụ với Python
pip install -r requirements.txt

# Hoặc với Node.js
npm install
```

### Khởi động server
```bash
docker build -t backend-server .
docker run --rm -p 9000:9000/tcp -p 9001:9001/udp backend-serve
docker tag backend-server n3thanh/backend-server:latest
docker push n3thanh/backend-server:latest
kubectl apply -f backendserver-k8s.yaml
kubectl get pods -l app=backend-server -o wide
```

Server chạy tại: `http://localhost:8080`

---

## 🔗 API

| Endpoint | Method | Input | Output |
|----------|--------|-------|--------|
| `/health` | GET | — | `{"status":"ok"}` |
| `/api/...` | POST | `{...}` | `{...}` |

> **Lưu ý:** Bổ sung các endpoint của nhóm vào bảng trên.

---

## 📦 CẤU TRÚC
```
server/
├── README.md
├── app.py (hoặc server.js)
├── requirements.txt (hoặc package.json)
├── routes/
│   └── ...
└── utils/
    └── ...
```

---

## 🧪 TEST
```bash
# Test API bằng curl
curl http://localhost:8080/health
```

---

## 📝 GHI CHÚ

- Port mặc định: **8080**
- Có thể thay đổi trong file `.env` hoặc config