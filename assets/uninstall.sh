#!/system/bin/sh
export PATH=/system/bin:$PATH

BUSYBOX=./busybox-xposed

CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
MOUNT="$BUSYBOX mount"
MV="$BUSYBOX mv"
RM="$BUSYBOX rm"

echo Mounting /system writable...
$MOUNT -o remount,rw /system

if [ -f /system/bin/app_process.orig ]; then
	echo Restoring backup from /system/bin/app_process.orig...
	$MV /system/bin/app_process.orig /system/bin/app_process || exit 1
	$CHMOD 755 /system/bin/app_process || exit 1
	$CHOWN root:shell /system/bin/app_process || exit 1
else
    echo No backup found at /system/bin/app_process.orig
fi

if [ -f /data/xposed/XposedBridge.jar ]; then
	echo Deleting XposedBridge.jar...
	$RM /data/xposed/XposedBridge.jar || exit 1
else
	echo XposedBridge.jar did not exist, nothing to delete
fi

if [ -f /data/xposed/XposedBridge.jar.newversion ]; then
	echo Deleting XposedBridge.jar.newversion...
	$RM /data/xposed/XposedBridge.jar.newversion || exit 1
fi

echo
echo Done! Changes will become active on reboot.
exit 0
