@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo    LogX SDK 构建脚本 v2.0
echo ========================================
echo.

:: 记录开始时间
set START_TIME=%time%

:: 保存当前目录
set ROOT_DIR=%cd%

:: 步骤1: 清理并编译整个项目
echo [1/3] 清理并编译项目...
call mvn clean compile -DskipTests
if %errorlevel% neq 0 (
    echo [错误] 编译失败！
    goto :error
)
echo [1/3] 编译完成 ✓
echo.

:: 步骤2: 安装 logx-common 模块
echo [2/3] 安装 logx-common 模块...
cd logx-common
call mvn install -DskipTests
if %errorlevel% neq 0 (
    echo [错误] logx-common 安装失败！
    cd %ROOT_DIR%
    goto :error
)
cd %ROOT_DIR%
echo [2/3] logx-common 安装完成 ✓
echo        - logx-common-core
echo        - logx-common-api
echo        - logx-common-grpc
echo.

:: 步骤3: 安装 logx-sdk 模块
echo [3/3] 安装 logx-sdk 模块...
cd logx-sdk
call mvn install -DskipTests
if %errorlevel% neq 0 (
    echo [错误] logx-sdk 安装失败！
    cd %ROOT_DIR%
    goto :error
)
cd %ROOT_DIR%
echo [3/3] logx-sdk 安装完成 ✓
echo        - logx-sdk-core
echo        - logx-sdk-spring-boot-starter (Servlet)
echo        - logx-sdk-gateway-starter (WebFlux)
echo.

:: 构建成功
echo ========================================
echo    构建成功！
echo ========================================
echo 开始时间: %START_TIME%
echo 结束时间: %time%
echo.
echo SDK 已安装到本地 Maven 仓库:
echo.
echo   [logx-common]
echo     - logx-common-core
echo     - logx-common-api
echo     - logx-common-grpc
echo.
echo   [logx-sdk]
echo     - logx-sdk-core              (核心模块)
echo     - logx-sdk-spring-boot-starter (业务服务 - Servlet)
echo     - logx-sdk-gateway-starter     (微服务网关 - WebFlux)
echo.
echo ----------------------------------------
echo 使用方式:
echo.
echo   业务服务 (如 realauth, order):
echo     ^<artifactId^>logx-sdk-spring-boot-starter^</artifactId^>
echo.
echo   微服务网关 (Spring Cloud Gateway):
echo     ^<artifactId^>logx-sdk-gateway-starter^</artifactId^>
echo ----------------------------------------
echo.
goto :end

:error
echo.
echo ========================================
echo    构建失败！
echo ========================================
exit /b 1

:end
endlocal