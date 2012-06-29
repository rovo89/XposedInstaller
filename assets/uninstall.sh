#!/system/bin/sh
export PATH=/system/bin:$PATH

echo Mounting /system writable...
mount -o remount,rw /system || exit 1

if [ -f /system/bin/app_process.orig ]; then
	echo Restoring backup from /system/bin/app_process.orig...
	mv /system/bin/app_process.orig /system/bin/app_process || exit 1
	chmod 755 /system/bin/app_process
	chown 0 /system/bin/app_process
	chgrp 2000 /system/bin/app_process
else
    echo No backup found at /system/bin/app_process.orig
fi

if [ -f /data/xposed/XposedBridge.jar ]; then
	echo Deleting XposedBridge.jar...
	rm /data/xposed/XposedBridge.jar || exit 1
else
	echo XposedBridge.jar did not exist, nothing to delete
fi
rm /data/xposed/XposedBridge.jar.newversion 2>/dev/null

echo
echo Done! Changes will become active on reboot.
exit 0
