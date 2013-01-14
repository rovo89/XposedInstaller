#!/system/bin/sh
export PATH=/system/bin:$PATH

BUSYBOX=./busybox-xposed

CHMOD="$BUSYBOX chmod"
CHOWN="$BUSYBOX chown"
MOUNT="$BUSYBOX mount"
MV="$BUSYBOX mv"
RM="$BUSYBOX rm"

echo Mounting /system writable...
$MOUNT -o remount,rw /system || exit 1

if [ -f /system/bin/app_process.orig ]; then
	echo Restoring backup from /system/bin/app_process.orig...
	$MV /system/bin/app_process.orig /system/bin/app_process || exit 1
	$CHMOD 755 /system/bin/app_process || exit 1
	$CHOWN root:shell /system/bin/app_process || exit 1
else
    echo No backup found at /system/bin/app_process.orig
fi

if [ -d /data/xposed ]; then
	echo Deleting /data/xposed...
	$RM -r /data/xposed/ || exit 1
else
	echo /data/xposed did not exist, nothing to delete
fi

echo
echo Done! Changes will become active on reboot.
exit 0
