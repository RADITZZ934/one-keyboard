@echo off
echo ========================================================
echo   ONE KEYBOARD - AUTO INSTALL DAN ZERO-TOUCH ACTIVATION
echo ========================================================
echo.
echo [1/3] Menginstal One Keyboard ke perangkat...
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
if %errorlevel% neq 0 (
    echo [ERROR] Gagal menginstal APK! Pastikan HP terhubung via USB.
    pause
    exit /b %errorlevel%
)

echo.
echo [2/3] Mengaktifkan One Keyboard di sistem secara otomatis (Bypass)...
adb shell ime enable com.raditzz.onekeyboard/com.example.barcodekeyboard.service.BarcodeKeyboardService

echo.
echo [3/3] Menjadikan One Keyboard sebagai keyboard utama...
adb shell ime set com.raditzz.onekeyboard/com.example.barcodekeyboard.service.BarcodeKeyboardService

echo.
echo ========================================================
echo   SUKSES! One Keyboard sudah aktif 100%% tanpa perlu
echo   membuka menu pengaturan sistem sama sekali.
echo ========================================================
echo.
pause
