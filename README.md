# Triển khai Kubernetes on-premise với Cilium

Tài liệu này tổng hợp toàn bộ các bước dựng cụm Kubernetes on-premise sử dụng Cilium làm CNI (thay thế kube-proxy) để cân bằng tải lưu lượng TCP/UDP cho các backend service của nhóm. Các hướng dẫn được biên soạn lại từ file `hd cài k8s.docx` và đã được chuẩn hoá thành các đoạn lệnh có thể chạy trực tiếp trên Ubuntu Server.

---

## 1. Kiến trúc tổng quan

| Thành phần | Mô tả |
|------------|-------|
| Control Plane | 01 máy chủ (Ubuntu Server) cài kubeadm, kubelet, kubectl và chạy Cilium agent |
| Worker Nodes | 02 máy chủ (Ubuntu Server) cài kubeadm, kubelet, kubelet và tham gia cụm bằng `kubeadm join` |
| Container Runtime | containerd (cgroup driver = `systemd`) |
| CNI | Cilium 1.18.3 (cài bằng `cilium install`) |
| Ứng dụng | Java TCP/UDP backend (xem `source/server/README.md`) và Java Swing client (xem `source/client/README.md`) |

Các node giao tiếp qua mạng nội bộ (Layer 2). Cilium đảm nhiệm routing eBPF, load balancing lưu lượng tới các Pod backend trên 2 worker node.

---

## 2. Kiểm tra trước khi cài đặt (áp dụng cho **tất cả** các node)

1. **Kiểm tra phiên bản glibc**
   ```bash
   ldd --version
   ```
2. **Kiểm tra phiên bản kernel**
   ```bash
   uname -r
   ```
3. **Ghi nhận địa chỉ mạng các interface**
   ```bash
   ip -br addr show
   ```
4. **Tắt swap hoàn toàn** (bắt buộc trước khi chạy kubeadm)
   ```bash
   sudo swapoff -a
   # Kiểm tra lại
   free -h | grep -i swap

   # Comment dòng swap trong /etc/fstab để tránh tự bật lại
   sudo sed -i.bak '/\sswap\s/ s/^/#/' /etc/fstab

   # Khởi động lại máy và xác nhận swap đã tắt
   sudo reboot
   ```

---

## 3. Cài đặt container runtime (containerd)

Thực hiện trên **mọi node** sau khi máy khởi động lại.

1. **Bật IPv4 packet forwarding & các kernel module cần thiết**
   ```bash
   cat <<'EOF_SYS' | sudo tee /etc/modules-load.d/containerd.conf
   overlay
   br_netfilter
   EOF_SYS

   sudo modprobe overlay
   sudo modprobe br_netfilter

   cat <<'EOF_SYSCTL' | sudo tee /etc/sysctl.d/99-kubernetes-cri.conf
   net.bridge.bridge-nf-call-iptables  = 1
   net.bridge.bridge-nf-call-ip6tables = 1
   net.ipv4.ip_forward                 = 1
   EOF_SYSCTL

   sudo sysctl --system
   ```

2. **Cài containerd, runc và CNI plugin**
   ```bash
   sudo apt-get update
   sudo apt-get install -y apt-transport-https ca-certificates curl gnupg lsb-release

   # Cài containerd từ kho chính thức của Ubuntu
   sudo apt-get install -y containerd

   # (Tuỳ chọn) cập nhật runc & CNI plugin bản mới nhất
   sudo install -m 0755 -d /usr/local/lib/containerd
   sudo curl -L https://github.com/opencontainers/runc/releases/download/v1.1.12/runc.amd64 -o /usr/local/sbin/runc
   sudo chmod +x /usr/local/sbin/runc

   sudo mkdir -p /opt/cni/bin
   sudo curl -L https://github.com/containernetworking/plugins/releases/download/v1.4.0/cni-plugins-linux-amd64-v1.4.0.tgz | sudo tar -xz -C /opt/cni/bin
   ```

3. **Tạo cấu hình mặc định và chuyển sang `SystemdCgroup`**
   ```bash
   sudo mkdir -p /etc/containerd
   sudo containerd config default | sudo tee /etc/containerd/config.toml >/dev/null
   sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

   sudo systemctl enable containerd
   sudo systemctl restart containerd
   sudo systemctl status containerd --no-pager
   ```

---

## 4. Cài kubeadm, kubelet, kubectl

Thực hiện trên **mọi node**.

```bash
sudo apt-get update
sudo apt-get install -y apt-transport-https ca-certificates curl
curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.30/deb/Release.key | sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/kubernetes-apt-keyring.gpg

echo 'deb [signed-by=/etc/apt/trusted.gpg.d/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.30/deb/ /' | \
  sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update
sudo apt-get install -y kubelet kubeadm kubectl
sudo apt-mark hold kubelet kubeadm kubectl

sudo systemctl enable kubelet
sudo systemctl start kubelet
```

---

## 5. Cài và cấu hình Cilium CLI

Chạy lệnh sau **trên control-plane node** để lấy phiên bản CLI mới nhất:

```bash
CILIUM_CLI_VERSION=$(curl -s https://raw.githubusercontent.com/cilium/cilium-cli/main/stable.txt)
CLI_ARCH=amd64
if [ "$(uname -m)" = "aarch64" ]; then CLI_ARCH=arm64; fi

curl -L --fail --remote-name-all \
  https://github.com/cilium/cilium-cli/releases/download/${CILIUM_CLI_VERSION}/cilium-linux-${CLI_ARCH}.tar.gz{,.sha256sum}
sha256sum --check cilium-linux-${CLI_ARCH}.tar.gz.sha256sum
sudo tar xzvfC cilium-linux-${CLI_ARCH}.tar.gz /usr/local/bin
rm cilium-linux-${CLI_ARCH}.tar.gz{,.sha256sum}
```

Sau khi cụm được khởi tạo (xem bước 6), cài đặt Cilium CNI:

```bash
cilium install --version 1.18.3
cilium status --wait
```

---

## 6. Khởi tạo cụm Kubernetes bằng kubeadm

### 6.1. Tạo file cấu hình cho control-plane

Ví dụ file `kubeadm-config.yaml`:

```yaml
apiVersion: kubeadm.k8s.io/v1beta3
kind: ClusterConfiguration
kubernetesVersion: v1.30.0
controlPlaneEndpoint: "<CONTROL_PLANE_IP>:6443"
networking:
  podSubnet: "10.217.0.0/16"
  serviceSubnet: "10.96.0.0/12"
---
apiVersion: kubeproxy.config.k8s.io/v1alpha1
kind: KubeProxyConfiguration
mode: "none"  # vì sử dụng Cilium thay thế kube-proxy
```

Điều chỉnh `controlPlaneEndpoint` theo IP thực tế (xem bước kiểm tra IP ở mục 2).

### 6.2. Khởi tạo control-plane

```bash
sudo kubeadm init --config kubeadm-config.yaml
```

Sau khi thành công:

```bash
mkdir -p $HOME/.kube
sudo cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config
```

Triển khai Cilium nếu chưa chạy ở bước 5, kiểm tra các Pod hệ thống:

```bash
kubectl get nodes -o wide
kubectl get pods -A -o wide
```

### 6.3. Join worker nodes

Trên mỗi worker, dùng lệnh `kubeadm join` được in ra sau khi init. Ví dụ:

```bash
sudo kubeadm join <CONTROL_PLANE_IP>:6443 \
  --token <TOKEN> \
  --discovery-token-ca-cert-hash sha256:<HASH>
```

Kiểm tra lại trên control-plane:

```bash
kubectl get nodes -o wide
```

---

## 7. Triển khai workload mẫu

1. Build và publish image backend: xem `source/server/README.md`.
2. Tạo Deployment/Service để chạy backend (ví dụ sử dụng `LoadBalancer` hoặc `NodePort`).
3. Sử dụng `source/client/README.md` để gửi nhiều request TCP/UDP và quan sát bảng thống kê phân phối theo Pod IP.

Sau khi triển khai, có thể quan sát đường đi gói tin bằng Cilium:

```bash
cilium service list
cilium endpoint list
```

---

## 8. Ghi chú & khắc phục sự cố

- Kiểm tra swap phải tắt hoàn toàn trước khi join cụm.
- Nếu `kubeadm init` báo lỗi với `preflight checks`, dùng `kubeadm reset` để làm sạch trước khi thử lại.
- `containerd` cần chạy với `SystemdCgroup = true` để tương thích `kubelet --cgroup-driver=systemd`.
- Trước khi chạy client, đảm bảo Service của backend đã mở NodePort hoặc LoadBalancer để máy client truy cập được.

---

## 9. Tài liệu liên quan

- File hướng dẫn gốc: `hd cài k8s.docx`
- README thành phần backend: `source/server/README.md`
- README thành phần client: `source/client/README.md`
