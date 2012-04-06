#!/system/bin/sh
cd `dirname $0`

echo Mounting /system writable...
mount -o remount,rw /system || exit 1

if [ -f /system/bin/app_process.orig ]; then
	echo Restoring backup from /system/bin/app_process.orig...
	mv -f /system/bin/app_process.orig /system/bin/app_process || exit 1
	chmod 755 /system/bin/app_process
	chown root:shell /system/bin/app_process
else
    echo No backup found at /system/bin/app_process.orig
fi

echo Deleting /data/xposed...
rm -rf /data/xposed/ || exit 1

echo
echo Done! Changes will become active on reboot.
exit 0
