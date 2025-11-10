# BÀI TẬP LỚN: LẬP TRÌNH MẠNG  

## Understanding Cilium: The Next-Gen CNI for Kubernetes

> 📘 README này mô tả chi tiết hệ thống triển khai thật, sử dụng Kubernetes on-premise với Cilium làm cơ chế load balancing cho các container backend xử lý TCP/UDP.

---

## 🧑‍💻 THÔNG TIN NHÓM

| STT | Họ và Tên | MSSV | Email | Đóng góp |
|-----|-----------|------|-------|----------|
| 1 | Nguyễn Bá Thành | B22DCCN793 | thanhkeu2k4@gmail.com | ... |
| 2 | Trần Thị B | 20IT002 | b@example.com | ... |
| 3 | Lê Văn C | 20IT003 | c@example.com | ... |

**Tên nhóm:** Nhóm 07 – Lập trình mạng  
**Chủ đề đã đăng ký:** Understanding Cilium: The Next-Gen CNI for Kubernetes

---

## 🧠 MÔ TẢ HỆ THỐNG


Hệ thống Kubernetes On-Premise với CNI Cilium Load Balancing

Hệ thống được triển khai trên Kubernetes on-premise gồm 3 node:

- 1 Control Plane Node chịu trách nhiệm quản lý, điều phối tài nguyên trong cluster.

- 2 Worker Node, mỗi node chạy 3 Pod (tổng cộng 6 Pod) đảm nhận xử lý các yêu cầu từ client.

Mỗi Pod chứa container backend (được kéo trực tiếp từ Docker Hub), chạy dịch vụ TCP/UDP Server được viết bằng Java. Ứng dụng ClientApp trên máy người dùng gửi nhiều request TCP/UDP đồng thời đến cluster và nhân lai mot bang thong ke bao gom do tre trung binh moi lan giao tiep giua backend-client, so request duoc xu ly o moi pod co dia chi ip rieng, giúp kiểm thử hiệu quả của cơ chế load balancing do Cilium đảm nhiệm. Cilium đóng vai trò là CNI Plugin thay thế kube-proxy, sử dụng công nghệ eBPF trong nhân Linux để can bang tai (load blancing ) luu luong TCP/UDP giua cac Pod trong mot cluster.

Khi client gửi yêu cầu, Cilium tự động phân phối các gói TCP/UDP đến các Pod backend trên hai worker node, giúp hệ thống đạt được khả năng phân tải thông minh, giảm độ trễ và tối ưu băng thông.

> NOTE: Toan bo he thong k8s duoc trien khai tren cac may ao ubnutu server  
 


**Cấu trúc logic tổng quát:**
```
        ┌────────────────────────┐
         │        Client          │
         │  n Request TCP / UDP   │
         └──────────┬─────────────┘
                    │
                    ▼
        ┌──────────────────────────────┐
        │       Control Plane          │
        │ ┌──────────────────────────┐ │
        │ │     kube-apiserver       │ │
        │ │     scheduler            │ │
        │ │     etcd                 │ │
        │ │     Cilium Agent         │ │
        │ └──────────────────────────┘ │
        └──────────┬───────────────────┘
                   │
     ┌─────────────┼──────────────────────────┐
     │                                          │
     ▼                                          ▼
┌──────────────────────────┐        ┌──────────────────────────┐
│     Worker Node #1       │        │     Worker Node #2       │
│ ┌──────────────────────┐ │        │ ┌──────────────────────┐ │
│ │ Pod1  Pod2  Pod3     │ │        │ │ Pod1  Pod2  Pod3     │ │
│ │ (Container backend)  │ │        │ │ (Container backend)  │ │
│ └──────────────────────┘ │        │ └──────────────────────┘ │
│                          │        │                          │
│ containerd + kubelet     │        │ containerd + kubelet     │
│ Cilium Agent (eBPF)      │        │ Cilium Agent (eBPF)      │
└──────────────────────────┘        └──────────────────────────┘
-------------------------------
Sơ đồ hoạt động chi tiết
-------------------------------
Client
  │ TCP dst=NodeIP_cp:31000
  ▼
[Control-Plane Node]
  NIC(rx) → eBPF(tc/XDP)
    → svc map lookup → pick PodIP on worker-01
    → DNAT (and SNAT if policy=Cluster)
    → Encapsulate VXLAN to NodeIP_worker1
  NIC(tx) ───────────────────────────────►
  [Worker-01 Node]
  NIC(rx) → eBPF → Decap VXLAN → revNAT/CT → veth → Pod
  Pod handles → veth → eBPF → reverse NAT → (VXLAN/direct)
  NIC(tx) ───────────────────────────────► Client

```

**Sơ đồ hệ thống:**

![System Diagram](./statics/diagram.png)

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

> Liệt kê công nghệ, framework, thư viện chính mà nhóm sử dụng.

| Thành phần | Công nghệ | Ghi chú |
|------------|-----------|---------|
| Server | K8s + Cilium | REST API |
| Client | Java + Java Swing | Gui Request TCP/UDP |
| Backend | Java | Xu ly request TCP/UDP va tra ve phan hoi |
| Triển khai | Docker + K8s | Dong goi va trien khai tren cac pod |

---

## 🚀 HƯỚNG DẪN CHẠY DỰ ÁN

### 1. Clone repository
```bash
git clone <repository-url>
cd assignment-network-project
```

### 2. Chạy server
```bash
cd source/server
# Các lệnh để khởi động server
```

### 3. Chạy client
```bash
cd source/client
# Các lệnh để khởi động client
```

### 4. Kiểm thử nhanh
```bash
# Các lệnh test
```

---

## 🔗 GIAO TIẾP (GIAO THỨC SỬ DỤNG)

| Endpoint | Protocol | Method | Input | Output |
|----------|----------|--------|-------|--------|
| `/health` | HTTP/1.1 | GET | — | `{"status": "ok"}` |
| `/compute` | HTTP/1.1 | POST | `{"task":"sum","payload":[1,2,3]}` | `{"result":6}` |

---

## 📊 KẾT QUẢ THỰC NGHIỆM

> Đưa ảnh chụp kết quả hoặc mô tả log chạy thử.

![Demo Result](./statics/result.png)

---

## 🧩 CẤU TRÚC DỰ ÁN
```
assignment-network-project/
├── README.md
├── INSTRUCTION.md
├── statics/
│   ├── diagram.png
│   └── dataset_sample.csv
└── source/
    ├── .gitignore
    ├── client/
    │   ├── README.md
    │   └── (client source files...)
    ├── server/
    │   ├── README.md
    │   └── (server source files...)
    └── (các module khác nếu có)
```

---

## 🧩 HƯỚNG PHÁT TRIỂN THÊM

> Nêu ý tưởng mở rộng hoặc cải tiến hệ thống.

- [ ] Cải thiện giao diện người dùng
- [ ] Thêm tính năng xác thực và phân quyền
- [ ] Tối ưu hóa hiệu suất
- [ ] Triển khai trên cloud

---

## 📝 GHI CHÚ

- Repo tuân thủ đúng cấu trúc đã hướng dẫn trong `INSTRUCTION.md`.
- Đảm bảo test kỹ trước khi submit.

---

## 📚 TÀI LIỆU THAM KHẢO

> (Nếu có) Liệt kê các tài liệu, API docs, hoặc nguồn tham khảo đã sử dụng.