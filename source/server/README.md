# Backend TCP/UDP Service

Backend là ứng dụng Java cung cấp cả TCP và UDP server (mặc định `9000/tcp` và `9001/udp`). Mỗi Pod chạy service này trả về IP nội bộ của chính Pod, giúp quan sát khả năng cân bằng tải của Cilium trong cụm Kubernetes.

---

## 1. Yêu cầu hệ thống

| Thành phần | Phiên bản gợi ý |
|------------|------------------|
| JDK | 21 (Temurin/Oracle OpenJDK) |
| Maven | >= 3.8 |
| Docker | 24.x trở lên (để build image) |
| Kubernetes | v1.30 (cài bằng kubeadm, xem hướng dẫn trong README ở thư mục gốc) |

Trước khi deploy lên cluster, đảm bảo đã hoàn thành các bước chuẩn bị node, cài containerd, kubeadm, Cilium như mô tả trong `../../README.md`.

---

## 2. Build & chạy local

```bash
cd source/server/BackendServer

# Biên dịch & tạo file JAR
mvn clean package

# Chạy trực tiếp (lắng nghe mặc định 9000/9001)
java -jar target/BackendServer-1.0-SNAPSHOT.jar

# Tùy chọn truyền port khác
java -jar target/BackendServer-1.0-SNAPSHOT.jar 9100 9101
```

Ứng dụng sẽ in log mỗi khi nhận request và phản hồi lại payload, kèm IP và port thực tế của server.

---

## 3. Đóng gói Docker image

Dockerfile đã sẵn trong thư mục `BackendServer/`.

```bash
cd source/server/BackendServer
mvn clean package

# Build image cục bộ
IMAGE_TAG=n3thanh/backend-server:latest
sudo docker build -t ${IMAGE_TAG} .

# Kiểm thử nhanh image
sudo docker run --rm -p 9000:9000/tcp -p 9001:9001/udp ${IMAGE_TAG}

# Đăng image lên Docker Hub (điều chỉnh registry nếu cần)
sudo docker login
sudo docker push ${IMAGE_TAG}
```

---

## 4. Triển khai lên Kubernetes

Sau khi control-plane đã sẵn sàng và Cilium hoạt động tốt:

1. Tạo file `backend-deployment.yaml` với nội dung mẫu bên dưới (sửa `image` nếu dùng registry khác).
2. Áp dụng manifest và chờ Pod lên trạng thái `Running`.
3. Kiểm tra Service NodePort/LoadBalancer để biết port truy cập từ bên ngoài.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-server
  labels:
    app: backend-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend-server
  template:
    metadata:
      labels:
        app: backend-server
    spec:
      containers:
        - name: backend
          image: n3thanh/backend-server:latest
          imagePullPolicy: Always
          ports:
            - containerPort: 9000
              name: tcp
              protocol: TCP
            - containerPort: 9001
              name: udp
              protocol: UDP
---
apiVersion: v1
kind: Service
metadata:
  name: backend-server
spec:
  selector:
    app: backend-server
  type: NodePort
  ports:
    - name: tcp
      protocol: TCP
      port: 9000
      targetPort: 9000
      nodePort: 31000
    - name: udp
      protocol: UDP
      port: 9001
      targetPort: 9001
      nodePort: 31001
```

Áp dụng manifest:

```bash
kubectl apply -f backend-deployment.yaml
kubectl get pods -l app=backend-server -o wide
kubectl get svc backend-server
```

Cilium sẽ thay thế kube-proxy nên Service `NodePort` 31000/31001 được điều phối thông minh đến các Pod. Có thể dùng lệnh `cilium service list` để kiểm tra mapping backend.

---

## 5. Khắc phục sự cố

- Pod `CrashLoopBackOff`: kiểm tra log `kubectl logs <pod>`, bảo đảm container có quyền mở port 9000/9001.
- Không gửi/nhận được UDP: kiểm tra firewall của node và chắc chắn Service có khai báo port UDP.
- Load balancing không đều: dùng `cilium status` và `cilium monitor` để xem gói tin có bị policy chặn không.

---

## 6. Tài liệu tham khảo

- Quy trình dựng cluster: `../../README.md`
- Client test tool: `../client/README.md`
