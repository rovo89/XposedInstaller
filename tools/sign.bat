@ECHO OFF

cd /d %~dp0
cd ..

echo Signing Xposed-Disabler-Recovery.zip
java -jar tools\SignApk.jar -w tools\testkey.x509.pem tools\testkey.pk8 assets\Xposed-Disabler-Recovery.zip assets\Xposed-Disabler-Recovery.zip_signed
move assets\Xposed-Disabler-Recovery.zip_signed assets\Xposed-Disabler-Recovery.zip

echo Signing Xposed-Installer-Recovery.zip
java -jar tools\SignApk.jar -w tools\testkey.x509.pem tools\testkey.pk8 assets\Xposed-Installer-Recovery.zip assets\Xposed-Installer-Recovery.zip_signed
move assets\Xposed-Installer-Recovery.zip_signed assets\Xposed-Installer-Recovery.zip

pause
