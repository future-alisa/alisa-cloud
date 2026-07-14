# ================= ⚙️ 变量配置 =================
# 默认服务名称
SERVICE ?= alisa-gateway
COMPANY ?= alisa
REGISTRY ?= 

# 自动判断操作系统
ifeq ($(OS),Windows_NT)
    MVN_CMD = mvn
else
    MVN_CMD = mvn
endif

# ----------------------------------------------------
# 🔍 动态映射核心逻辑：
# 核心原因：服务名叫 alisa-gateway，但你的子模块目录、Dockerfile 和 Jar 包名字各有不同。
# 这里通过条件判断，将服务名完美映射到具体的「Maven模块名」和「Dockerfile前缀」
# ----------------------------------------------------
ifeq ($(SERVICE),alisa-gateway)
    MODULE = alisa-gateway
    DOCKER_NAME = alisa-gateway
endif

# 常量定义
# 对应你的路径：docker/gateway.Dockerfile
DOCKERFILE_PATH = docker/$(DOCKER_NAME).Dockerfile

# 生产环境完整的镜像名称
ifeq ($(REGISTRY),)
    IMAGE_NAME = $(COMPANY)/$(DOCKER_NAME)
else
    IMAGE_NAME = $(REGISTRY)/$(COMPANY)/$(DOCKER_NAME)
endif
IMAGE_TAG = latest

# 定义所有子服务列表（用作批量处理）
SERVICES = alisa-gateway 

.PHONY: package-single build-only build clean-image push-image build-all push-all $(SERVICES)

# ================= 🚀 基础构建逻辑 =================

# 1. 精准打包子模块
package-single:
	@echo "====> [Maven] 正在精准打包子模块: $(MODULE) ..."
	$(MVN_CMD) clean package -pl $(MODULE) -am -DskipTests

# 2. 构建生产级镜像
build-only:
	@echo "====> [Podman] 正在构建生产镜像: $(IMAGE_NAME):$(IMAGE_TAG) ..."
	podman build -f $(DOCKERFILE_PATH) -t $(IMAGE_NAME):$(IMAGE_TAG) .

# 3. 完整构建流水线（打包 + 编镜像）
build: package-single build-only

# 4. 清理本地旧镜像
clean-image:
	@echo "====> [Podman] 正在删除旧镜像 $(IMAGE_NAME):$(IMAGE_TAG) ..."
	-podman rmi $(IMAGE_NAME):$(IMAGE_TAG)


# ================= 🌪️ 快捷入口（支持 Windows 并发） =================

# 快捷指令：单独构建某一个服务（例如直接输入：make alisa-gateway）
$(SERVICES):
	@$(MAKE) build SERVICE=$@

# 一键并发构建所有服务（完美解决 Windows 上的进程调度 Bug）
build-all:
	@$(MAKE) -j 1 build-gateway


# 各个服务的内部快捷嵌套
build-gateway:
	@$(MAKE) build SERVICE=alisa-gateway

# ================= 🚢 生产部署一键推送 =================

# 一键推送所有镜像到云端仓库
push-all:
	podman push $(REGISTRY)/$(COMPANY)/gateway:$(IMAGE_TAG)

# 终极省心指令：一键完成「全量打包、全量构建、全量推送」
release-all: build-all push-all