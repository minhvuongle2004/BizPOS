# BizPOS — Hệ thống quản lý bán hàng cơ bản

Ứng dụng web POS hỗ trợ cửa hàng quản lý sản phẩm, khách hàng và đơn hàng.

---

## 🚀 Công nghệ sử dụng

- **Backend**: Java 17, Spring Boot 3.3.4, Spring Web
- **Cơ sở dữ liệu**: MySQL
- **ORM / Persistence**: Spring Data JPA, Hibernate
- **Giao diện**: HTML5, CSS3, JavaScript, Bootstrap 5
- **Template Engine**: Thymeleaf
- **Build Tool**: Maven

---

## 📋 Chức năng chính

1. **Quản lý danh mục**: Thêm, sửa, xóa, xem danh sách.
2. **Quản lý sản phẩm**: CRUD, tìm kiếm sản phẩm theo tên / mã.
3. **Quản lý khách hàng**: CRUD, tìm kiếm theo tên / số điện thoại.
4. **Tạo đơn hàng (POS)**: Chọn khách hàng, thêm nhiều sản phẩm vào đơn, tự động tính tổng tiền.
5. **Quản lý đơn hàng**: Xem danh sách đơn, chi tiết đơn, sửa / xóa đơn.
6. **Đăng nhập cơ bản**: Xác thực người dùng truy cập hệ thống.

---

## 🗄️ Cấu trúc cơ sở dữ liệu

- `categories`: Danh mục sản phẩm
- `products`: Sản phẩm (mã, tên, giá, mô tả, danh mục)
- `customers`: Khách hàng (họ tên, SĐT, email, địa chỉ)
- `orders`: Đơn hàng (mã đơn, khách hàng, ngày tạo, tổng tiền, ghi chú)
- `order_items`: Chi tiết đơn hàng (lưu snapshot sản phẩm, đơn giá, số lượng, thành tiền)

---

## ⚙️ Cài đặt & Khởi chạy

1. **Khởi tạo cơ sở dữ liệu**:
   Chạy script [schema.sql](src/main/resources/schema.sql) trên MySQL (hoặc để Spring Boot tự tạo qua `createDatabaseIfNotExist=true`).

2. **Cấu hình thông tin kết nối**:
   Mở file `src/main/resources/application.properties` và điều chỉnh:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/bizpos_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
   spring.datasource.username=root
   spring.datasource.password=your_password
   ```

3. **Khởi chạy ứng dụng**:
   ```bash
   mvn spring-boot:run
   ```

4. **Truy cập ứng dụng**:
   Mở trình duyệt tại: `http://localhost:8080/`
