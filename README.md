# 王者荣耀农场助手

前后端分离 Web 应用：Vue 3 + Spring Boot。

## 目录结构

```
farm-helper/
├── frontend/     # Vue 3 前端
├── backend/      # Spring Boot 后端
├── data/csv/     # 静态游戏数据
├── docs/         # 文档
└── scripts/      # 数据转换脚本
```

## 启动方式

### 1. 后端（端口 8080）

```bash
cd backend
mvn spring-boot:run
```

### 2. 前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问：http://localhost:5173

## 更新静态数据

```bash
cd scripts
pip install -r requirements.txt
python xls_to_csv.py
```

然后重启后端以重新加载 CSV。
