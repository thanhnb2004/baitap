# Client Monitor

Ứng dụng client là một công cụ Java Swing dùng để phát nhiều yêu cầu TCP/UDP song song đến backend và thống kê độ trễ trung bình theo từng Pod IP. Client giúp đánh giá khả năng cân bằng tải của Cilium sau khi cụm Kubernetes đã được dựng theo hướng dẫn trong `../../README.md`.

---

## 1. Tính năng chính

- Cho phép cấu hình địa chỉ control-plane/worker, port TCP và UDP tương ứng với Service NodePort (`31000/31001`).
- Tạo số lượng request lớn (mặc định 1000) cho từng giao thức.
- Tính toán thời gian phản hồi min/avg/max theo từng Pod backend, hiển thị dưới dạng bảng và log.
- Chạy đa luồng để mô phỏng tải thực tế, có log real-time ở giao diện chính.

---

## 2. Chuẩn bị môi trường

| Thành phần | Phiên bản gợi ý |
|------------|------------------|
| JDK | 21 |
| Maven (tùy chọn) | >= 3.8 (nếu muốn đóng gói jar) |
| Hệ điều hành | Ubuntu Desktop/Server hoặc Windows/Linux bất kỳ có JDK |

> **Lưu ý:** Client không phụ thuộc vào Kubernetes. Chỉ cần truy cập được đến NodePort của Service backend (ví dụ `NODE_IP:31000` và `NODE_IP:31001`).

---

## 3. Build & chạy trực tiếp bằng `javac`

```bash
cd source/client/ClientMonitor

# Tạo thư mục build
mkdir -p out

# Biên dịch mã nguồn (yêu cầu JDK 21)
javac -d out src/ClientLogic.java src/ClientApp.java

# Chạy ứng dụng GUI
java -cp out ClientApp
```

Khi ứng dụng mở lên, nhập `Host`, `TCP Port`, `UDP Port`, số lượng request và bấm **Gửi TCP** hoặc **Gửi UDP**. Bảng thống kê sẽ hiển thị khi hoàn tất.

---

## 4. Đóng gói runnable JAR (tuỳ chọn)

Nếu muốn tạo file JAR để phân phối, có thể dùng lệnh sau:

```bash
cd source/client/ClientMonitor
jar --create --file ClientMonitor.jar --main-class ClientApp -C out .
java -jar ClientMonitor.jar
```

Hoặc tạo một `pom.xml` tối giản rồi dùng Maven Shade plugin (tham khảo tài liệu Maven nếu cần).

---

## 5. Kịch bản kiểm thử với cụm Kubernetes

1. Đảm bảo backend NodePort 31000/31001 đã sẵn sàng (xem `../server/README.md`).
2. Lấy IP public/private của control-plane hoặc worker node (`ip -br addr show`).
3. Trên máy chạy client:
   - Nhập `Host = <NODE_IP>`.
   - `TCP Port = 31000`, `UDP Port = 31001` (hoặc port tương ứng bạn cấu hình trong manifest).
   - Nhập số lượng request (ví dụ 1000) rồi gửi.
4. Quan sát log, bảng tổng hợp để đánh giá phân phối request.

Nếu có nhiều client cùng test, bạn có thể chạy nhiều instance hoặc dùng tham số dòng lệnh để thay đổi port nhanh chóng (`java -cp out ClientApp <host> <tcpPort> <udpPort> <count>` sẽ được hỗ trợ nếu tự bổ sung, hiện tại cấu hình qua GUI).

---

## 6. Khắc phục sự cố

- **Không gửi được request**: kiểm tra firewall giữa máy client và node Kubernetes, đảm bảo port NodePort mở.
- **Không hiện bảng thống kê**: số lượng request có thể quá ít hoặc backend không trả về chuỗi chứa `Server IP:`. Kiểm tra log ở console.
- **Ứng dụng không mở GUI**: đảm bảo đã cài đặt môi trường đồ hoạ (OpenJDK full) và chạy ở máy có GUI. Nếu chạy headless, sử dụng X11 forwarding hoặc viết phiên bản CLI.

---

## 7. Tài liệu liên quan

- Backend TCP/UDP server: `../server/README.md`
- Hướng dẫn dựng cluster: `../../README.md`
